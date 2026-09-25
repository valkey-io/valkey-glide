// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::collections::HashMap;
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};

use crate::buffer::{HostMemory, RdmaBuffer};
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
    /// buffer, lending a different window of it to each transfer, rather than
    /// registering per transfer.
    pub fn register(
        &self,
        memory: impl AsMut<[u8]> + Send + 'static,
    ) -> Result<RdmaBuffer, RdmaError> {
        let host = HostMemory::new(memory);
        if host.len() == 0 {
            return Err(RdmaError::Configuration("cannot register 0 bytes".into()));
        }
        // SAFETY: `host` keeps the bytes at one address until it is dropped, and the
        // returned RdmaBuffer drops it only after the region is closed.
        let memory_region = unsafe { self.endpoint().register_remote(host.bytes())? };
        let registration = Registration::new(memory_region, self.clone());
        let region_ref = self.region_ref(host.address(), &registration);
        Ok(RdmaBuffer::new(
            registration,
            region_ref,
            host,
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

    /// Whether `self` and `other` are the same open fabric, rather than two opened
    /// separately.
    pub(crate) fn is(&self, other: &RdmaFabric) -> bool {
        Arc::ptr_eq(&self.inner, &other.inner)
    }

    /// This endpoint's local fabric address.
    pub fn local_address(&self) -> &[u8] {
        &self.inner.address
    }

    /// Close a fid belonging to this domain, returning libfabric's result code.
    pub(crate) fn fi_close(&self, fid: *mut ofi_libfabric_sys::bindgen::fid) -> i32 {
        #[cfg(test)]
        if tests::take_failed_close() {
            return -libc::EBUSY;
        }
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
pub(crate) mod tests {
    use super::RdmaFabric;
    use crate::config::{FabricConfig, Provider};
    use crate::error::RdmaError;
    use crate::progress::ProgressDriver;
    use std::cell::Cell;

    impl RdmaFabric {
        /// How many distinct peer addresses the address vector currently holds.
        fn peer_count(&self) -> usize {
            self.peers().len()
        }

        /// How many transfers are keeping the progress thread polling.
        pub(crate) fn transfers_in_flight(&self) -> usize {
            self.inner
                .progress
                .as_ref()
                .map_or(0, ProgressDriver::active)
        }
    }

    thread_local! {
        /// How many of this thread's next `fi_close` calls fail without closing.
        static FAILED_CLOSES: Cell<u32> = const { Cell::new(0) };
    }

    /// Make the next `count` calls to [`RdmaFabric::fi_close`] on this thread fail.
    pub(crate) fn fail_next_closes(count: u32) {
        FAILED_CLOSES.with(|closes| closes.set(count));
    }

    /// Whether this `fi_close` should fail, using up one of the failures if so.
    pub(super) fn take_failed_close() -> bool {
        FAILED_CLOSES.with(|closes| {
            let left = closes.get();
            closes.set(left.saturating_sub(1));
            left > 0
        })
    }

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
    fn a_peer_address_of_the_wrong_length_is_refused() {
        let fabric = fabric();
        let length = fabric.local_address().len();
        for wrong in [0, 1, length - 1, length + 1] {
            let result = fabric.open_session(&crate::session::Handshake {
                peers: vec![fabric.local_address().to_vec(), vec![0u8; wrong]],
            });
            assert!(
                matches!(result, Err(RdmaError::Protocol(_))),
                "a {wrong}-byte address should be refused, got {result:?}"
            );
            assert_eq!(
                fabric.peer_count(),
                0,
                "the good address before it was given back"
            );
        }
    }

    #[test]
    fn a_buffer_belongs_to_the_fabric_that_registered_it() {
        let fabric = fabric();
        let buffer = fabric
            .register(vec![0u8; 4096])
            .expect("registration failed");
        assert_eq!(buffer.capacity(), 4096);
        assert!(buffer.is_registered_on(&fabric));
        assert!(
            buffer.is_registered_on(&fabric.clone()),
            "a clone is the same fabric"
        );
        assert!(
            !buffer.is_registered_on(&self::fabric()),
            "a fabric opened separately is a different one"
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
}
