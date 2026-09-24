// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::collections::HashMap;
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};

use crate::buffer::RdmaBuffer;
use crate::config::FabricConfig;
use crate::endpoint::{LibfabricEndpoint, Registration};
use crate::error::RdmaError;
use crate::progress::{ProgressDriver, ProgressGuard};
use crate::region_ref::RegionRef;
use crate::session::Handshake;

/// One address vector entry.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
struct PeerHandle(u64);

/// The client's libfabric domain representing one network device open for use.
#[derive(Clone, Debug)]
pub struct RdmaFabric {
    inner: Arc<FabricInner>,
}

#[derive(Debug)]
struct FabricInner {
    /// `None` for efa-direct.
    progress: Option<ProgressDriver>,
    endpoint: Mutex<LibfabricEndpoint>,
    address: Vec<u8>,
    uses_virtual_addressing: bool,
    /// Every peer address currently in the address vector and how many sessions
    /// depend on it.
    peers: Mutex<HashMap<Vec<u8>, PeerEntry>>,
}

#[derive(Debug)]
struct PeerEntry {
    handle: PeerHandle,
    /// Sessions holding this address. At zero the entry leaves the address vector.
    sessions: usize,
}

/// One RESP connection's RDMA session.
/// The server holds an RDMA session for each new client connection, so
/// this is the client-side version of RDMA sessions.
#[derive(Debug)]
pub struct RdmaSession {
    fabric: RdmaFabric,
    addresses: Vec<Vec<u8>>,
}

impl Drop for RdmaSession {
    fn drop(&mut self) {
        self.fabric.release_peers(&self.addresses);
    }
}

impl RdmaFabric {
    /// Open an endpoint for `config`.
    pub fn open(config: &FabricConfig) -> Result<Self, RdmaError> {
        crate::libfabric_dl::ensure_loaded()?;
        let endpoint = LibfabricEndpoint::open(config)?;
        let address = endpoint.local_address()?;
        let uses_virtual_addressing = endpoint.uses_virtual_addressing();
        let progress = config
            .provider()
            .needs_manual_progress()
            .then(|| ProgressDriver::new(endpoint.completion_queue()));
        Ok(Self {
            inner: Arc::new(FabricInner {
                progress,
                endpoint: Mutex::new(endpoint),
                address,
                uses_virtual_addressing,
                peers: Mutex::new(HashMap::new()),
            }),
        })
    }

    /// Register host memory for the server to RMA against.
    ///
    /// Registration is expensive and pins pages against `RLIMIT_MEMLOCK`, so reuse a
    /// region and advertise windows of it with [`RdmaBuffer::slice`] rather than
    /// registering per transfer.
    pub fn register(
        &self,
        memory: impl AsMut<[u8]> + Send + 'static,
    ) -> Result<RdmaBuffer, RdmaError> {
        let mut memory: Box<dyn AsMut<[u8]> + Send> = Box::new(memory);
        let buffer = (*memory).as_mut();
        let (pointer, length) = (buffer.as_ptr() as u64, buffer.len());
        if length == 0 {
            return Err(RdmaError::Configuration("cannot register 0 bytes".into()));
        }
        // SAFETY: `memory` is boxed and moved into the returned RdmaBuffer, which
        // declares its registration first so the region closes before the memory drops.
        let memory_region = unsafe { self.endpoint().register_remote(buffer)? };
        let registration = Registration::new(memory_region, self.clone());
        let region_ref = self.region_ref(pointer, &registration);
        Ok(RdmaBuffer::host(
            registration,
            region_ref,
            length,
            memory,
            self.clone(),
        ))
    }

    /// Register shared, read-only memory as a source for the `LO.SET` direction.
    ///
    /// Registered `FI_REMOTE_READ` only, so a server can read any window advertised
    /// from it but never write. Each registration counts separately against
    /// `RLIMIT_MEMLOCK` even though the physical pages are the same.
    pub fn register_shared<S>(&self, memory: Arc<S>) -> Result<RdmaBuffer, RdmaError>
    where
        S: AsRef<[u8]> + Send + Sync + 'static,
    {
        let bytes: &[u8] = (*memory).as_ref();
        let (pointer, length) = (bytes.as_ptr() as u64, bytes.len());
        if length == 0 {
            return Err(RdmaError::Configuration("cannot register 0 bytes".into()));
        }
        let memory_region = unsafe { self.endpoint().register_source(bytes)? };
        let registration = Registration::new(memory_region, self.clone());
        let region_ref = self.region_ref(pointer, &registration);
        Ok(RdmaBuffer::shared(
            registration,
            region_ref,
            length,
            memory,
            self.clone(),
        ))
    }

    /// Open one RDMA session for one RESP connection.
    pub fn open_session(&self, handshake: &Handshake) -> Result<RdmaSession, RdmaError> {
        let mut held: Vec<Vec<u8>> = Vec::with_capacity(handshake.peers.len());
        for address in &handshake.peers {
            if let Err(error) = self.hold_peer(address) {
                // Give back what this call took, so a half-opened session leaves
                // the address vector as it found it.
                self.release_peers(&held);
                return Err(error);
            }
            held.push(address.clone());
        }
        Ok(RdmaSession {
            fabric: self.clone(),
            addresses: held,
        })
    }

    /// How many distinct peer addresses the address vector currently holds.
    #[cfg(test)]
    fn peer_count(&self) -> usize {
        self.peers().len()
    }

    /// Take a reference to `address`, inserting it if this is the first.
    fn hold_peer(&self, address: &[u8]) -> Result<(), RdmaError> {
        let mut peers = self.peers();
        if let Some(entry) = peers.get_mut(address) {
            entry.sessions += 1;
            return Ok(());
        }
        let handle = PeerHandle(self.endpoint().fi_av_insert(address)?);
        peers.insert(
            address.to_vec(),
            PeerEntry {
                handle,
                sessions: 1,
            },
        );
        Ok(())
    }

    /// Give back one reference to each address, releasing any that reach zero.
    fn release_peers(&self, addresses: &[Vec<u8>]) {
        let mut peers = self.peers();
        for address in addresses {
            let Some(entry) = peers.get_mut(address) else {
                continue;
            };
            entry.sessions -= 1;
            if entry.sessions == 0 {
                let entry = peers.remove(address).expect("just looked it up");
                let _ = self.endpoint().fi_av_remove(entry.handle.0);
            }
        }
    }

    fn peers(&self) -> MutexGuard<'_, HashMap<Vec<u8>, PeerEntry>> {
        self.inner
            .peers
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
    }

    /// Drive progress until the returned guard drops.
    /// Hold it across a transfer's RESP round trip so the server's RMA is serviced.
    /// `None` when the provider needs no polling, which is the efa-direct case.
    #[must_use]
    pub fn drive_progress(&self) -> Option<ProgressGuard> {
        self.inner.progress.as_ref().map(ProgressDriver::drive)
    }

    /// This endpoint's local fabric address.
    pub fn local_address(&self) -> &[u8] {
        &self.inner.address
    }

    /// Close a fid belonging to this domain, returning libfabric's result code.
    pub(crate) fn fi_close(&self, fid: *mut ofi_libfabric_sys::bindgen::fid) -> i32 {
        // this is the domain synchronization lock
        let _domain = self.endpoint();
        // SAFETY: the caller owns `fid`, it is live, and is closed exactly once. The
        // guard above excludes every other call into this domain for the duration.
        unsafe { ofi_libfabric_sys::bindgen::fi_close(fid) }
    }

    fn endpoint(&self) -> MutexGuard<'_, LibfabricEndpoint> {
        self.inner
            .endpoint
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
    }

    fn region_ref(&self, remote_address: u64, registration: &Registration) -> RegionRef {
        RegionRef {
            address: self.inner.address.clone(),
            remote_key: registration.remote_key(),
            remote_address: if self.inner.uses_virtual_addressing {
                remote_address
            } else {
                // for offset-addressed providers like tcp
                0
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::RdmaFabric;
    use crate::config::{FabricConfig, Provider};
    use std::sync::Arc;

    fn fabric() -> RdmaFabric {
        RdmaFabric::open(&FabricConfig::new(Provider::Tcp)).expect("tcp should open")
    }

    fn handshake_of(fabric: &RdmaFabric) -> crate::session::Handshake {
        crate::session::Handshake {
            peers: vec![fabric.local_address().to_vec()],
        }
    }

    #[test]
    fn a_session_holds_its_addresses_and_gives_them_back_on_drop() {
        let fabric = fabric();
        assert_eq!(fabric.peer_count(), 0);

        let session = fabric
            .open_session(&handshake_of(&fabric))
            .expect("the fabric accepts its own address");
        assert_eq!(fabric.peer_count(), 1);

        drop(session);
        assert_eq!(fabric.peer_count(), 0, "the entry left with the session");
    }

    #[test]
    fn sessions_sharing_an_address_share_one_entry() {
        let fabric = fabric();
        let handshake = handshake_of(&fabric);

        let first = fabric.open_session(&handshake).expect("opens");
        let second = fabric.open_session(&handshake).expect("opens");
        let third = fabric.open_session(&handshake).expect("opens");
        assert_eq!(fabric.peer_count(), 1, "one address, three sessions");

        drop(second);
        assert_eq!(
            fabric.peer_count(),
            1,
            "still held by the other two, so the entry stays"
        );
        drop(first);
        drop(third);
        assert_eq!(fabric.peer_count(), 0, "the last holder released it");
    }

    #[test]
    fn a_session_naming_several_addresses_holds_each_one() {
        let other = fabric();
        let fabric = fabric();

        let session = fabric
            .open_session(&crate::session::Handshake {
                peers: vec![
                    fabric.local_address().to_vec(),
                    other.local_address().to_vec(),
                ],
            })
            .expect("both are real endpoints");
        assert_eq!(fabric.peer_count(), 2);

        drop(session);
        assert_eq!(fabric.peer_count(), 0);
    }

    /// A handshake naming the same address twice must not leave a reference
    /// behind when its session drops.
    #[test]
    fn a_repeated_address_in_one_handshake_balances_out() {
        let fabric = fabric();
        let address = fabric.local_address().to_vec();

        let session = fabric
            .open_session(&crate::session::Handshake {
                peers: vec![address.clone(), address],
            })
            .expect("opens");
        assert_eq!(fabric.peer_count(), 1);

        drop(session);
        assert_eq!(fabric.peer_count(), 0, "both references were given back");
    }

    #[test]
    fn registers_a_buffer_and_advertises_it() {
        let fabric = fabric();
        let buffer = fabric
            .register(vec![0u8; 4096])
            .expect("registration failed");
        assert_eq!(buffer.capacity(), 4096);
        assert_eq!(buffer.region_ref().address, fabric.local_address());
        assert!(
            !buffer.region_ref().address.is_empty(),
            "an enabled endpoint has an address to advertise"
        );
    }

    /// tcp addresses by offset, so the region starts at 0 rather than at its virtual
    /// address. Getting this backwards is the silent-corruption case.
    #[test]
    fn offset_addressing_advertises_zero_for_the_region_start() {
        let fabric = fabric();
        let buffer = fabric.register(vec![0u8; 4096]).unwrap();
        if fabric.inner.uses_virtual_addressing {
            assert_ne!(buffer.region_ref().remote_address, 0);
        } else {
            assert_eq!(buffer.region_ref().remote_address, 0);
        }
    }

    #[test]
    fn registering_nothing_is_a_configuration_error() {
        assert!(fabric().register(Vec::new()).is_err());
        assert!(fabric().register_shared(Arc::new(Vec::new())).is_err());
    }

    /// A shared source can be registered on several fabrics at once.
    #[test]
    fn one_allocation_registers_on_several_fabrics() {
        let shared: Arc<Vec<u8>> = Arc::new(vec![7u8; 2048]);
        let first = fabric().register_shared(shared.clone()).unwrap();
        let second = fabric().register_shared(shared.clone()).unwrap();
        assert_eq!(first.capacity(), 2048);
        assert_eq!(second.capacity(), 2048);
        // Distinct endpoints, so distinct advertised addresses.
        assert_ne!(first.region_ref().address, second.region_ref().address);
    }

    /// tcp emulates RMA in software, so a transfer only progresses while the target
    /// polls. efa-direct needs no driver and would return None.
    #[test]
    fn tcp_drives_progress() {
        assert!(fabric().drive_progress().is_some());
    }
}
