// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::collections::HashMap;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};

use crate::buffer::{HostMemory, RdmaBuffer};
use crate::closer::Closer;
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
///
/// # What blocks
///
/// These calls can wait on libfabric, for microseconds on tcp and up to
/// milliseconds on efa for large regions, so avoid making them on an async
/// runtime's worker thread where that matters:
///
/// - [`Self::register`]: registers the memory, after waiting for any region this
///   fabric is still closing.
/// - [`Self::open_session`] and dropping an [`RdmaSession`]: edit the address
///   vector, which waits for any region closing at that moment.
/// - [`RdmaBuffer::revoke`], [`crate::RdmaRevoker::revoke`] and
///   [`crate::LentBuffer::recall`]: close the region before returning.
/// - [`Self::wait_for_closes`]: waits for the regions of buffers dropped before it.
/// - Dropping the last handle to the fabric, from any thread but its closing
///   thread: stops the progress thread and closes the endpoint.
///
/// Dropping an [`RdmaBuffer`] or a [`crate::LentBuffer`] does not block: the
/// fabric's closing thread closes the region and then frees or parks the memory.
#[derive(Clone, Debug)]
pub struct RdmaFabric {
    inner: Arc<FabricInner>,
}

#[derive(Debug)]
struct FabricInner {
    /// Closes regions for the buffers and loans dropped on this fabric. Every
    /// close it has queued holds a handle to the fabric, so by the time this
    /// drops it has nothing left to run.
    closer: Closer,
    /// `None` for efa-direct.
    progress: Option<ProgressDriver>,
    endpoint: Mutex<LibfabricEndpoint>,
    parked: Mutex<Vec<HostMemory>>,
    /// Total length of `parked`, readable without the lock.
    parked_bytes: AtomicUsize,
    /// Bytes kept for the life of the process because their region failed to close.
    leaked_bytes: AtomicUsize,
    /// [`crate::config::Provider::close_stops_writes`] for this fabric's provider.
    close_stops_writes: bool,
    address: Vec<u8>,
    uses_virtual_addressing: bool,
    /// Every peer address currently in the address vector and how many sessions
    /// depend on it.
    peers: Mutex<HashMap<Vec<u8>, PeerEntry>>,
    #[cfg(test)]
    hooks: tests::CloseHooks,
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
                closer: Closer::start()?,
                progress,
                endpoint: Mutex::new(endpoint),
                parked: Mutex::new(Vec::new()),
                parked_bytes: AtomicUsize::new(0),
                leaked_bytes: AtomicUsize::new(0),
                close_stops_writes: config.provider().close_stops_writes(),
                address,
                uses_virtual_addressing,
                peers: Mutex::new(HashMap::new()),
                #[cfg(test)]
                hooks: tests::CloseHooks::default(),
            }),
        })
    }

    /// Register host memory for the server to RMA against.
    ///
    /// Registration is expensive and pins pages against `RLIMIT_MEMLOCK`, so reuse a
    /// buffer, lending a different window of it to each transfer, rather than
    /// registering per transfer.
    ///
    /// Waits first for every region this fabric is still closing, so the pages of
    /// a buffer dropped just before are no longer pinned.
    pub fn register(
        &self,
        memory: impl AsMut<[u8]> + Send + 'static,
    ) -> Result<RdmaBuffer, RdmaError> {
        let host = HostMemory::new(memory);
        if host.len() == 0 {
            return Err(RdmaError::Configuration("cannot register 0 bytes".into()));
        }
        self.wait_for_closes();
        // SAFETY: `host` keeps the bytes at one address until it is dropped, and the
        // returned RdmaBuffer drops it only after the region is closed.
        let registered = unsafe { self.endpoint().register_remote(host.bytes_ptr())? };
        let registration = Registration::new(registered, self.clone());
        let region_ref = self.region_ref(&registration);
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

    /// Close a region on this fabric's closing thread rather than the caller's:
    /// `close` runs there with this handle, which keeps the fabric open until
    /// it has.
    pub(crate) fn close_later(self, close: impl FnOnce(&RdmaFabric) + Send + 'static) {
        let queue = self.inner.closer.queue();
        queue.submit(Box::new(move || close(&self)));
    }

    /// Wait until every [`RdmaBuffer`] and [`crate::LentBuffer`] dropped on this
    /// fabric before this call has finished closing its region and has freed,
    /// parked or leaked its memory.
    ///
    /// Dropping one of them only queues that work for the fabric's closing
    /// thread. Call this when the caller needs it done, for example before
    /// handing back memory it does not own: once this returns, the memory of
    /// such a buffer has been dropped unless it was parked or leaked, which
    /// [`Self::parked_bytes`] and [`Self::leaked_bytes`] count.
    ///
    /// Buffers dropped while this waits are not waited for. Called from the
    /// closing thread itself, it returns at once.
    pub fn wait_for_closes(&self) {
        self.inner.closer.queue().wait_until_idle();
    }

    /// Dispose of the memory of a loan that ended without the server's reply.
    /// `closed` is whether its region closed successfully.
    ///
    /// The memory is freed now when the region closed on a provider whose close also
    /// stops writes already in flight, parked until this fabric closes when it closed
    /// on any other provider, and leaked when it failed to close.
    pub(crate) fn release_unanswered(&self, host: HostMemory, closed: bool) {
        if !closed {
            self.leak(host);
        } else if self.inner.close_stops_writes {
            drop(host);
        } else {
            self.park(host);
        }
    }

    /// Keep `host` allocated for the life of the process because its region failed
    /// to close, and count it in [`Self::leaked_bytes`].
    ///
    /// The memory is forgotten before anything else runs, so even a panic while
    /// counting or logging cannot free memory the server may still write to.
    pub(crate) fn leak(&self, host: HostMemory) {
        let bytes = host.len();
        std::mem::forget(host);
        self.note_leaked(bytes);
    }

    /// Count `bytes` the caller is keeping for the life of the process because their
    /// region failed to close.
    ///
    /// A failed close means the device may still let the server write into the
    /// memory, so it can never safely be freed. It also keeps this fabric open for
    /// good, so nothing parked on it is freed either.
    fn note_leaked(&self, bytes: usize) {
        let total = self.inner.leaked_bytes.fetch_add(bytes, Ordering::Relaxed) + bytes;
        glide_logger::log_error(
            "rdma fabric",
            format!(
                "an RDMA memory region failed to close, so its {bytes} bytes stay allocated \
                 until the process exits ({total} bytes in all), and this fabric can no \
                 longer close. Create a new client to keep transferring; restart the \
                 process when the leaked total passes your threshold"
            ),
        );
    }

    /// Keep `host` allocated until this fabric closes; see [`crate::LentBuffer`].
    /// Only the endpoint closing guarantees no accepted write can still land in it.
    fn park(&self, host: HostMemory) {
        let bytes = host.len();
        self.inner
            .parked
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .push(host);
        let total = self.inner.parked_bytes.fetch_add(bytes, Ordering::Relaxed) + bytes;
        glide_logger::log_debug(
            "rdma fabric",
            format!(
                "parked {bytes} bytes of registered memory until the fabric closes, {total} in all"
            ),
        );
    }

    /// Bytes parked by loans that ended without a reply. Only grows; freed when the
    /// last handle drops, and every buffer, loan and session on the fabric holds one.
    /// To get them back sooner, drop this fabric's buffers and create a new client,
    /// which opens a new fabric.
    ///
    /// Stays at zero on efa-direct, where a successful close stops the server's writes
    /// and the memory is freed at once.
    pub fn parked_bytes(&self) -> usize {
        self.inner.parked_bytes.load(Ordering::Relaxed)
    }

    /// Bytes kept for the life of the process because their region failed to close.
    /// Only grows.
    ///
    /// After a failed close the device may still let the server write into the memory,
    /// so it is never freed, and this fabric can never close, which also keeps any
    /// [`Self::parked_bytes`] for good. A failed close needs a device or driver error,
    /// and each one is logged at error level.
    ///
    /// Restart the process when the leaked total passes your chosen threshold.
    pub fn leaked_bytes(&self) -> usize {
        self.inner.leaked_bytes.load(Ordering::Relaxed)
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
    pub(crate) fn fi_close(&self, fid: *mut glide_libfabric_sys::bindgen::fid) -> i32 {
        #[cfg(test)]
        if self.inner.hooks.record_close() {
            return -libc::EBUSY;
        }
        // this is the domain synchronization lock
        let _domain = self.endpoint();
        // SAFETY: the caller owns `fid`, it is live, and is closed exactly once. The
        // guard above excludes every other call into this domain for the duration.
        unsafe { glide_libfabric_sys::bindgen::fi_close(fid) }
    }

    fn endpoint(&self) -> MutexGuard<'_, LibfabricEndpoint> {
        self.inner
            .endpoint
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
    }

    fn region_ref(&self, registration: &Registration) -> RegionRef {
        RegionRef {
            remote_key: registration.remote_key(),
            remote_address: if self.inner.uses_virtual_addressing {
                registration.address()
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
    use std::sync::atomic::{AtomicU32, Ordering};
    use std::sync::{Mutex, MutexGuard, PoisonError};

    impl RdmaFabric {
        /// How many distinct peer addresses the address vector currently holds.
        fn peer_count(&self) -> usize {
            self.peers().len()
        }

        /// How many buffers' memory is parked until this fabric closes.
        pub(crate) fn parked(&self) -> usize {
            self.inner
                .parked
                .lock()
                .unwrap_or_else(PoisonError::into_inner)
                .len()
        }

        /// How many handles hold this fabric open, this one included.
        pub(crate) fn holders(&self) -> usize {
            std::sync::Arc::strong_count(&self.inner)
        }

        /// How many transfers are keeping the progress thread polling.
        pub(crate) fn transfers_in_flight(&self) -> usize {
            self.inner
                .progress
                .as_ref()
                .map_or(0, ProgressDriver::active)
        }

        /// Make the next `count` calls to [`RdmaFabric::fi_close`] on this fabric fail.
        pub(crate) fn fail_next_closes(&self, count: u32) {
            self.inner.hooks.failures.store(count, Ordering::SeqCst);
        }

        /// How many times [`RdmaFabric::fi_close`] has been called on this fabric.
        pub(crate) fn close_calls(&self) -> u32 {
            self.inner.hooks.calls.load(Ordering::SeqCst)
        }

        /// Hold every [`RdmaFabric::fi_close`] on this fabric until the guard drops,
        /// standing in for a close that takes a long time.
        pub(crate) fn hold_closes(&self) -> MutexGuard<'_, ()> {
            self.inner
                .hooks
                .gate
                .lock()
                .unwrap_or_else(PoisonError::into_inner)
        }
    }

    /// Per fabric, because closes run on the fabric's closing thread, not the test's.
    #[derive(Debug, Default)]
    pub(super) struct CloseHooks {
        /// How many of the next `fi_close` calls fail without closing.
        failures: AtomicU32,
        /// Calls to `fi_close`, failed or not.
        calls: AtomicU32,
        /// Held by a test to keep `fi_close` from returning.
        gate: Mutex<()>,
    }

    impl CloseHooks {
        /// Count this `fi_close`; whether it should fail, using up one failure if so.
        pub(super) fn record_close(&self) -> bool {
            drop(self.gate.lock().unwrap_or_else(PoisonError::into_inner));
            self.calls.fetch_add(1, Ordering::SeqCst);
            let mut left = self.failures.load(Ordering::SeqCst);
            while left > 0 {
                match self.failures.compare_exchange(
                    left,
                    left - 1,
                    Ordering::SeqCst,
                    Ordering::SeqCst,
                ) {
                    Ok(_) => return true,
                    Err(actual) => left = actual,
                }
            }
            false
        }
    }

    fn fabric() -> RdmaFabric {
        RdmaFabric::open(&FabricConfig::new(Provider::Tcp)).expect("tcp should open")
    }

    /// A tcp fabric that treats a successful region close the way efa-direct's is
    /// treated, for testing that path on a host without efa.
    pub(crate) fn fabric_whose_close_stops_writes() -> RdmaFabric {
        let mut fabric = fabric();
        std::sync::Arc::get_mut(&mut fabric.inner)
            .expect("nothing else holds a fresh fabric")
            .close_stops_writes = true;
        fabric
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
