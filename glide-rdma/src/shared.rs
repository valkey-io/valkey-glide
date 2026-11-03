// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! A buffer registered once whose windows are lent to many transfers at the
//! same time, so long as no window the server writes overlaps another.
//!
//! [`crate::RdmaBuffer`] is lent whole, so N concurrent transfers over the same
//! memory need N registrations. A [`RdmaSharedBuffer`] needs one. The region is
//! still revoked as a whole, so one transfer ending without a reply ends every
//! transfer through the buffer.

use std::fmt;
use std::mem::ManuallyDrop;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, PoisonError};

use crate::buffer::{HostMemory, RdmaRevoker};
use crate::command::{self, RdmaCommand, ReadReceipt, SetChecksum, TransferReply};
use crate::endpoint::Registration;
use crate::error::RdmaError;
use crate::fabric::RdmaFabric;
use crate::progress::ProgressGuard;
use crate::region_ref::RegionRef;

/// Host memory registered with a [`RdmaFabric`], lent a window at a time to any
/// number of concurrent transfers, from any thread or client on the fabric.
///
/// Clones are the same buffer. The memory is let go of once every clone and
/// every [`LentWindow`] is dropped; if a transfer ended without a reply, it is
/// then parked or freed as [`crate::LentBuffer`] describes.
#[derive(Clone)]
pub struct RdmaSharedBuffer {
    inner: Arc<SharedBufferInner>,
}

struct SharedBufferInner {
    /// Taken by `Drop`.
    registration: ManuallyDrop<Registration>,
    region_ref: RegionRef,
    /// Freed only once the region is closed.
    host: ManuallyDrop<HostMemory>,
    fabric: RdmaFabric,
    /// `[start, end)` of each window lent, and whether the server writes it (a
    /// get). A linear scan suffices for the tens of transfers a buffer serves at
    /// once.
    lent: Mutex<Vec<(usize, usize, bool)>>,
    /// Whether a loan ended without a reply, which decides whether the memory
    /// is parked; see [`crate::LentBuffer`].
    unanswered: AtomicBool,
}

// SAFETY: the bytes are reached only through `LentWindow::bytes`, a slice of one
// lent window taken from the raw pointer, and no lent window overlaps one the
// server writes. The
// owner is only dropped, once, in `Drop`. The other fields are locked, atomic or
// `Sync`.
unsafe impl Sync for SharedBufferInner {}

impl RdmaSharedBuffer {
    pub(crate) fn new(
        registration: Registration,
        region_ref: RegionRef,
        host: HostMemory,
        fabric: RdmaFabric,
    ) -> Self {
        Self {
            inner: Arc::new(SharedBufferInner {
                registration: ManuallyDrop::new(registration),
                region_ref,
                host: ManuallyDrop::new(host),
                fabric,
                lent: Mutex::new(Vec::new()),
                unanswered: AtomicBool::new(false),
            }),
        }
    }

    /// Bytes registered.
    pub fn capacity(&self) -> usize {
        self.inner.host.len()
    }

    /// Whether this buffer was registered with `fabric`.
    pub fn is_registered_on(&self, fabric: &RdmaFabric) -> bool {
        self.inner.fabric.is(fabric)
    }

    /// The fabric this buffer was registered with.
    pub fn fabric(&self) -> &RdmaFabric {
        &self.inner.fabric
    }

    /// Lend `[at, at + length)` as [`crate::RdmaBuffer::lend_for_get`] does.
    ///
    /// # Errors
    ///
    /// When the window runs past the end or overlaps a lent window, or the buffer
    /// is revoked.
    pub fn lend_for_get(
        &self,
        key: &[u8],
        at: usize,
        length: usize,
    ) -> Result<(RdmaCommand, LentWindow), RdmaError> {
        self.lend(at, length, true, |_, window| {
            command::get(key, length, window)
        })
    }

    /// Lend `[at, at + length)` as [`crate::RdmaBuffer::lend_for_set`] does.
    ///
    /// Sets may overlap each other, since the server only reads their windows.
    ///
    /// # Errors
    ///
    /// When the window runs past the end or overlaps a window lent to a get, or
    /// the buffer is revoked.
    pub fn lend_for_set(
        &self,
        key: &[u8],
        at: usize,
        length: usize,
        checksum: SetChecksum,
    ) -> Result<(RdmaCommand, LentWindow), RdmaError> {
        self.lend(at, length, false, |bytes, window| {
            let checksum = match checksum {
                SetChecksum::Skip => None,
                SetChecksum::Compute => Some(crate::checksum(bytes)),
                SetChecksum::Known(checksum) => Some(checksum),
            };
            command::set(key, length, checksum, window)
        })
    }

    fn lend(
        &self,
        at: usize,
        length: usize,
        written: bool,
        build: impl FnOnce(&[u8], &RegionRef) -> RdmaCommand,
    ) -> Result<(RdmaCommand, LentWindow), RdmaError> {
        self.inner.registration.check_open()?;
        let end = at
            .checked_add(length)
            .filter(|end| *end <= self.capacity())
            .ok_or_else(|| {
                RdmaError::Configuration(format!(
                    "window [{at}, {at}+{length}) runs past the end of a registered region \
                     of {} bytes",
                    self.capacity()
                ))
            })?;
        let window = u64::try_from(at)
            .ok()
            .and_then(|at| self.inner.region_ref.remote_address.checked_add(at))
            .map(|remote_address| RegionRef {
                remote_key: self.inner.region_ref.remote_key,
                remote_address,
            })
            .ok_or_else(|| RdmaError::Configuration("window address overflows".into()))?;
        {
            let mut lent = self.lent();
            // An empty window overlaps nothing.
            if length > 0
                && lent.iter().any(|&(start, stop, other_written)| {
                    (written || other_written) && start < stop && at < stop && start < end
                })
            {
                return Err(RdmaError::Configuration(format!(
                    "window [{at}, {end}) overlaps a window of this buffer already lent \
                     to a transfer, and the server writes one of them"
                )));
            }
            lent.push((at, end, written));
        }
        let loan = LentWindow {
            buffer: self.clone(),
            at,
            length,
            progress: self.inner.fabric.drive_progress(),
        };
        let command = build(loan.bytes(), &window);
        Ok((command, loan))
    }

    /// Close the region, ending every transfer through the buffer without a
    /// reply. A no-op once revoked.
    pub fn revoke(&self) -> Result<(), RdmaError> {
        self.inner.registration.revoke()
    }

    /// Whether the buffer has been revoked.
    pub fn is_revoked(&self) -> bool {
        self.inner.registration.is_revoked()
    }

    /// Whether no window can be lent again: the buffer was revoked, or closing
    /// its region failed, which [`Self::is_revoked`] does not report.
    pub fn is_closed(&self) -> bool {
        self.inner.registration.check_open().is_err()
    }

    /// Resolves once the buffer is revoked.
    pub fn revoked(&self) -> impl Future<Output = ()> + Send + 'static {
        self.inner.registration.revoked()
    }

    /// As [`crate::RdmaBuffer::revoker`].
    pub fn revoker(&self) -> RdmaRevoker {
        RdmaRevoker::new(self.inner.registration.handle())
    }

    fn lent(&self) -> std::sync::MutexGuard<'_, Vec<(usize, usize, bool)>> {
        self.inner
            .lent
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
    }

    fn give_back(&self, at: usize, length: usize) {
        let mut lent = self.lent();
        // A written window overlaps no other, so windows with equal bounds are interchangeable.
        if let Some(index) = lent
            .iter()
            .position(|&(start, end, _)| (start, end) == (at, at + length))
        {
            lent.swap_remove(index);
        }
    }

    /// Revoke after a loan ended without a reply.
    fn revoke_unanswered(&self) -> Result<(), RdmaError> {
        self.inner.unanswered.store(true, Ordering::Release);
        self.revoke()
    }
}

impl Drop for SharedBufferInner {
    /// Frees, parks or leaks the memory as [`crate::RdmaBuffer`] and
    /// [`crate::LentBuffer`] do, on the fabric's closing thread.
    fn drop(&mut self) {
        // SAFETY: each is taken once, here.
        let (registration, host) = unsafe {
            (
                ManuallyDrop::take(&mut self.registration),
                ManuallyDrop::take(&mut self.host),
            )
        };
        let unanswered = self.unanswered.load(Ordering::Acquire);
        self.fabric.clone().close_later(move |fabric| {
            let closed = registration.revoke().is_ok();
            if unanswered {
                fabric.release_unanswered(host, closed);
            } else if closed {
                drop(host);
            } else {
                fabric.leak(host);
            }
            drop(registration);
        });
    }
}

impl fmt::Debug for RdmaSharedBuffer {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("RdmaSharedBuffer")
            .field("capacity", &self.capacity())
            .field("region_ref", &self.inner.region_ref)
            .field("lent", &self.lent().len())
            .finish()
    }
}

/// A window of a [`RdmaSharedBuffer`] lent to one transfer.
///
/// Ends as a [`crate::LentBuffer`] does, but hands nothing back: the window just
/// becomes lendable again. Ending without a reply revokes the whole buffer.
pub struct LentWindow {
    buffer: RdmaSharedBuffer,
    at: usize,
    length: usize,
    progress: Option<ProgressGuard>,
}

impl LentWindow {
    /// The window's bytes, formed only for the moment they are read, as
    /// [`HostMemory::from_raw`] requires.
    fn bytes(&self) -> &[u8] {
        let start = self.buffer.inner.host.bytes_ptr().cast::<u8>();
        // SAFETY: the window is in bounds, and no other loan's window that the
        // server writes overlaps it. It is read only before the command is sent
        // or after the reply, when the server is not writing it.
        unsafe { std::slice::from_raw_parts(start.as_ptr().add(self.at), self.length) }
    }

    /// End the loan with the server's reply, verifying a read's checksum.
    ///
    /// # Safety
    ///
    /// As [`crate::LentBuffer::reclaim`].
    ///
    /// # Errors
    ///
    /// [`RdmaError::PayloadTooLarge`] when the server reports writing past the
    /// window; other windows may then hold its bytes, so the buffer is revoked.
    /// [`RdmaError::ChecksumMismatch`] when the bytes differ from the reply.
    pub unsafe fn reclaim(self, reply: TransferReply) -> Result<Option<ReadReceipt>, RdmaError> {
        let result = match reply {
            TransferReply::Read(receipt) if receipt.bytes_written > self.length => {
                let _ = self.buffer.revoke();
                Err(RdmaError::PayloadTooLarge {
                    value_bytes: receipt.bytes_written,
                    capacity: self.length,
                })
            }
            TransferReply::Read(receipt) => receipt.verify(self.bytes()).map(|()| Some(receipt)),
            TransferReply::Missing | TransferReply::Stored | TransferReply::Failed => Ok(None),
        };
        self.finish();
        result
    }

    /// End the loan with no reply coming, revoking the buffer, as
    /// [`crate::LentBuffer::recall`] does.
    ///
    /// # Errors
    ///
    /// When libfabric fails to close the region; see [`RdmaFabric::leaked_bytes`].
    pub fn recall(self) -> Result<(), RdmaError> {
        let result = self.buffer.revoke_unanswered();
        self.finish();
        result
    }

    /// Whether the buffer has been revoked.
    pub fn is_revoked(&self) -> bool {
        self.buffer.is_revoked()
    }

    /// Resolves once the buffer is revoked.
    pub fn revoked(&self) -> impl Future<Output = ()> + Send + 'static {
        self.buffer.revoked()
    }

    /// Give the window back, skipping `Drop`.
    fn finish(self) {
        let mut this = ManuallyDrop::new(self);
        this.buffer.give_back(this.at, this.length);
        // SAFETY: `this` is never dropped, so each field is dropped once, here.
        unsafe {
            std::ptr::drop_in_place(&mut this.progress);
            std::ptr::drop_in_place(&mut this.buffer);
        }
    }
}

impl Drop for LentWindow {
    /// As [`Self::recall`], on the fabric's closing thread.
    fn drop(&mut self) {
        self.buffer.inner.unanswered.store(true, Ordering::Release);
        let buffer = self.buffer.clone();
        let progress = self.progress.take();
        let (at, length) = (self.at, self.length);
        self.buffer.inner.fabric.clone().close_later(move |_| {
            let _ = buffer.revoke();
            // Lendable again, and polled, only once the region is closed.
            buffer.give_back(at, length);
            drop(progress);
        });
    }
}

impl fmt::Debug for LentWindow {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("LentWindow")
            .field("at", &self.at)
            .field("length", &self.length)
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::{LentWindow, RdmaSharedBuffer};
    use crate::command::{RdmaCommand, ReadReceipt, SetChecksum, TransferReply};
    use crate::config::{FabricConfig, Provider};
    use crate::endpoint::LibfabricEndpoint;
    use crate::endpoint::tests::post_read;
    use crate::error::RdmaError;
    use crate::fabric::RdmaFabric;
    use crate::fabric::tests::fabric_whose_close_stops_writes;
    use glide_libfabric_sys::bindgen::{fi_close, fi_cq_entry, fi_cq_read};
    use std::ptr::NonNull;
    use std::sync::Arc;
    use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
    use std::time::{Duration, Instant};

    /// Sets its flag when dropped, which is when the fabric lets go of the memory.
    struct Owner(Arc<AtomicBool>);
    impl Drop for Owner {
        fn drop(&mut self) {
            self.0.store(true, Ordering::Release);
        }
    }

    fn region(fabric: &RdmaFabric, memory: &mut [u8]) -> (RdmaSharedBuffer, Arc<AtomicBool>) {
        let released = Arc::new(AtomicBool::new(false));
        // SAFETY: each test keeps `memory` alive until `released` is set.
        let region =
            unsafe { fabric.register_shared_raw(NonNull::from(memory), Owner(released.clone())) }
                .expect("registers");
        (region, released)
    }

    fn arguments(command: &RdmaCommand) -> Vec<String> {
        command
            .arguments()
            .iter()
            .map(|argument| String::from_utf8_lossy(argument).into_owned())
            .collect()
    }

    /// Bytes that differ at every offset, so a window read from the wrong place
    /// cannot pass for the right one.
    fn patterned(length: usize) -> Vec<u8> {
        (0..length).map(|index| (index % 251) as u8).collect()
    }

    fn fabric() -> RdmaFabric {
        RdmaFabric::open(&FabricConfig::new(Provider::Tcp)).expect("tcp should open")
    }

    #[test]
    fn disjoint_windows_are_lent_at_once_and_overlapping_ones_refused() {
        let fabric = fabric();
        let mut memory = vec![0u8; 4096];
        let (region, _) = region(&fabric, &mut memory);

        let (_, first) = region.lend_for_get(b"a", 0, 1024).expect("lends");
        let (_, second) = region
            .lend_for_get(b"b", 1024, 1024)
            .expect("a disjoint window lends");
        for (at, length) in [(0, 1), (1023, 2), (512, 1024), (0, 4096)] {
            let error = region.lend_for_get(b"c", at, length).unwrap_err();
            assert!(
                matches!(&error, RdmaError::Configuration(message) if message.contains("overlaps")),
                "[{at}, {at}+{length}) should overlap: {error:?}"
            );
        }
        assert!(
            region.lend_for_get(b"c", 4000, 200).is_err(),
            "past the end"
        );

        // SAFETY: no command was sent.
        unsafe { first.reclaim(TransferReply::Missing) }.expect("reclaims");
        let (_, again) = region
            .lend_for_get(b"a", 0, 1024)
            .expect("the window is free again");
        // SAFETY: as above.
        unsafe {
            again.reclaim(TransferReply::Missing).unwrap();
            second.reclaim(TransferReply::Stored).unwrap();
        }
        assert!(!region.is_revoked(), "replies leave the region open");
    }

    #[test]
    fn sets_may_share_a_window_but_a_get_shares_with_nothing() {
        let fabric = fabric();
        let mut memory = vec![0u8; 4096];
        let (region, _) = region(&fabric, &mut memory);
        let overlaps = |result: Result<(RdmaCommand, LentWindow), RdmaError>| match result {
            Err(RdmaError::Configuration(message)) => message.contains("overlaps"),
            _ => false,
        };

        // The same block stored under two keys at once.
        let (_, first) = region
            .lend_for_set(b"a", 0, 1024, SetChecksum::Compute)
            .expect("lends");
        let (_, second) = region
            .lend_for_set(b"b", 0, 1024, SetChecksum::Compute)
            .expect("the server only reads a set's window, so sets may share one");
        assert!(overlaps(region.lend_for_get(b"c", 1000, 100)));

        // SAFETY: no command was sent.
        unsafe { first.reclaim(TransferReply::Stored) }.unwrap();
        assert!(
            overlaps(region.lend_for_get(b"c", 1000, 100)),
            "the other set still holds the window"
        );
        // SAFETY: as above.
        unsafe { second.reclaim(TransferReply::Stored) }.unwrap();

        let (_, get) = region
            .lend_for_get(b"c", 1000, 100)
            .expect("the sets are done");
        assert!(overlaps(region.lend_for_set(
            b"d",
            1050,
            10,
            SetChecksum::Compute
        )));
        // SAFETY: as above.
        unsafe { get.reclaim(TransferReply::Missing) }.unwrap();
        assert!(!region.is_revoked(), "refusals leave the region open");
    }

    #[test]
    fn lending_names_the_window_and_checksums_only_it() {
        let fabric = fabric();
        let mut memory = patterned(4096);
        let expected = crate::checksum(&memory[2048..2304]);
        assert_ne!(expected, crate::checksum(&memory[..256]));
        let (buffer, _) = region(&fabric, &mut memory);
        let base = buffer.inner.region_ref.clone();

        let (get, _get) = buffer.lend_for_get(b"key", 1024, 256).unwrap();
        assert_eq!(get.name(), "BLOB.RDMA_GET");
        assert_eq!(
            arguments(&get),
            [
                "key".to_string(),
                base.remote_key.to_string(),
                (base.remote_address + 1024).to_string(),
                "256".to_string(),
            ]
        );

        let (set, _set) = buffer
            .lend_for_set(b"key", 2048, 256, SetChecksum::Compute)
            .unwrap();
        assert_eq!(set.name(), "BLOB.RDMA_SET");
        assert_eq!(
            arguments(&set)[2..],
            [
                "CRC".to_string(),
                expected.to_string(),
                base.remote_key.to_string(),
                (base.remote_address + 2048).to_string(),
                "256".to_string(),
            ]
        );
    }

    #[test]
    fn a_read_is_verified_against_its_window_alone() {
        let fabric = fabric();
        let mut memory = patterned(4096);
        let landed = ReadReceipt {
            bytes_written: 100,
            checksum: crate::checksum(&memory[512..612]),
        };
        let (buffer, _) = region(&fabric, &mut memory);

        let (_, loan) = buffer.lend_for_get(b"a", 512, 128).unwrap();
        // SAFETY: no command was sent.
        let receipt = unsafe { loan.reclaim(TransferReply::Read(landed)) };
        assert_eq!(receipt.expect("the window holds those bytes"), Some(landed));

        let (_, loan) = buffer.lend_for_get(b"a", 0, 128).unwrap();
        // SAFETY: as above.
        let error = unsafe { loan.reclaim(TransferReply::Read(landed)) }.unwrap_err();
        assert!(
            matches!(error, RdmaError::ChecksumMismatch { .. }),
            "{error:?}"
        );
        assert!(!buffer.is_closed(), "a mismatch leaves the buffer usable");
        buffer
            .lend_for_get(b"a", 0, 128)
            .expect("and the window lendable");
    }

    #[test]
    fn windows_at_the_edges() {
        let fabric = fabric();
        let mut memory = vec![0u8; 4096];
        let (buffer, _) = region(&fabric, &mut memory);

        let error = buffer.lend_for_get(b"a", usize::MAX, 2).unwrap_err();
        assert!(matches!(error, RdmaError::Configuration(_)), "{error:?}");
        let (_, whole) = buffer
            .lend_for_get(b"a", 0, 4096)
            .expect("the whole buffer");
        let (_, empty) = buffer
            .lend_for_get(b"b", 100, 0)
            .expect("an empty window overlaps nothing");
        // SAFETY: no command was sent.
        unsafe {
            empty.reclaim(TransferReply::Missing).unwrap();
            whole.reclaim(TransferReply::Missing).unwrap();
        }
    }

    #[test]
    fn a_window_raced_for_is_lent_to_one_transfer_at_a_time() {
        let fabric = fabric();
        let mut memory = vec![0u8; 64];
        let (buffer, _) = region(&fabric, &mut memory);
        let holders = AtomicUsize::new(0);
        let lent = AtomicUsize::new(0);
        std::thread::scope(|scope| {
            for _ in 0..8 {
                scope.spawn(|| {
                    for _ in 0..1000 {
                        let Ok((_, loan)) = buffer.lend_for_get(b"k", 0, 64) else {
                            continue;
                        };
                        assert_eq!(holders.fetch_add(1, Ordering::SeqCst), 0, "lent twice");
                        lent.fetch_add(1, Ordering::Relaxed);
                        holders.fetch_sub(1, Ordering::SeqCst);
                        // SAFETY: no command was sent.
                        unsafe { loan.reclaim(TransferReply::Missing) }.unwrap();
                    }
                });
            }
        });
        assert!(lent.load(Ordering::Relaxed) > 0);
    }

    #[test]
    fn each_window_drives_progress_until_it_ends() {
        let fabric = fabric();
        let mut memory = vec![0u8; 128];
        let (buffer, _) = region(&fabric, &mut memory);
        let (_, first) = buffer.lend_for_get(b"a", 0, 64).unwrap();
        let (_, second) = buffer.lend_for_get(b"b", 64, 64).unwrap();
        assert_eq!(fabric.transfers_in_flight(), 2);

        // SAFETY: no command was sent.
        unsafe { first.reclaim(TransferReply::Missing) }.unwrap();
        assert_eq!(fabric.transfers_in_flight(), 1);
        drop(second);
        fabric.wait_for_closes();
        assert_eq!(fabric.transfers_in_flight(), 0);
    }

    #[test]
    fn revoking_from_elsewhere_ends_every_window() {
        let fabric = fabric();
        let mut memory = vec![0u8; 128];
        let (buffer, _) = region(&fabric, &mut memory);
        assert!(buffer.is_registered_on(&fabric) && buffer.fabric().is(&fabric));
        assert!(!buffer.is_registered_on(&self::fabric()));
        let (_, first) = buffer.lend_for_get(b"a", 0, 64).unwrap();
        let (_, second) = buffer
            .lend_for_set(b"b", 64, 64, SetChecksum::Skip)
            .unwrap();

        let revoker = buffer.revoker();
        revoker.revoke().expect("closes");
        assert!(first.is_revoked() && second.is_revoked() && buffer.is_closed());
        assert!(matches!(
            buffer.lend_for_get(b"c", 0, 1),
            Err(RdmaError::Revoked)
        ));

        // SAFETY: no command was sent.
        unsafe {
            first.reclaim(TransferReply::Missing).unwrap();
            second.reclaim(TransferReply::Stored).unwrap();
        }
        drop(buffer);
        fabric.wait_for_closes();
        assert!(revoker.is_released());
    }

    /// The server's side of a set, played by a second endpoint: it reads each
    /// window through the key and address its command names, all of them lent
    /// at once, and finds that window's bytes there.
    #[test]
    fn a_peer_reads_each_window_where_its_command_points() {
        const WINDOW: usize = 4096;
        const WINDOWS: usize = 4;
        let fabric = fabric();
        let mut memory = patterned(WINDOW * WINDOWS);
        let expected = memory.clone();
        let (buffer, _) = region(&fabric, &mut memory);
        let mut peer = LibfabricEndpoint::open(&FabricConfig::new(Provider::Tcp)).expect("opens");

        let loans: Vec<_> = (0..WINDOWS)
            .map(|index| {
                let (command, loan) = buffer
                    .lend_for_set(b"k", index * WINDOW, WINDOW, SetChecksum::Skip)
                    .unwrap();
                let arguments = arguments(&command);
                let key: u64 = arguments[2].parse().unwrap();
                let address: u64 = arguments[3].parse().unwrap();
                (key, address, loan)
            })
            .collect();

        for (index, (key, address, _)) in loans.iter().enumerate() {
            let mut landed = vec![0u8; WINDOW];
            // SAFETY: `landed` outlives the region, which is closed below.
            let region = unsafe {
                post_read(
                    &mut peer,
                    &mut landed,
                    fabric.local_address(),
                    *address,
                    *key,
                )
            };
            let deadline = Instant::now() + Duration::from_secs(5);
            loop {
                let mut entry = fi_cq_entry {
                    op_context: std::ptr::null_mut(),
                };
                // SAFETY: the queue is open, and `entry` has room for one entry.
                let read = unsafe {
                    fi_cq_read(
                        peer.completion_queue(),
                        std::ptr::from_mut(&mut entry).cast(),
                        1,
                    )
                };
                if read > 0 {
                    break;
                }
                assert_eq!(read, -(libc::EAGAIN as isize), "the read failed");
                assert!(Instant::now() < deadline, "the read never completed");
            }
            // SAFETY: the read has completed, and nothing uses the region after this.
            unsafe { fi_close(&raw mut (*region).fid) };
            assert!(
                landed == expected[index * WINDOW..(index + 1) * WINDOW],
                "window {index} was read from the wrong place"
            );
        }

        for (_, _, loan) in loans {
            // SAFETY: the peer's reads have all completed.
            unsafe { loan.reclaim(TransferReply::Stored) }.unwrap();
        }
    }

    #[test]
    fn a_recall_revokes_the_region_for_every_window() {
        let fabric = fabric();
        let mut memory = vec![0u8; 4096];
        let (region, released) = region(&fabric, &mut memory);
        let (_, kept) = region.lend_for_set(b"a", 0, 64, SetChecksum::Skip).unwrap();
        let (_, lost) = region
            .lend_for_set(b"b", 64, 64, SetChecksum::Skip)
            .unwrap();

        lost.recall().expect("closes");
        assert!(region.is_revoked() && kept.is_revoked());
        assert!(matches!(
            region.lend_for_get(b"c", 128, 64),
            Err(RdmaError::Revoked)
        ));

        // SAFETY: no command was sent.
        unsafe { kept.reclaim(TransferReply::Stored) }.unwrap();
        drop(region);
        fabric.wait_for_closes();
        // tcp parks memory a loan left without a reply until the fabric closes.
        assert!(!released.load(Ordering::Acquire));
        assert_eq!(fabric.parked_bytes(), 4096);
        drop(fabric);
        assert!(
            released.load(Ordering::Acquire),
            "freed once the fabric closed"
        );
    }

    #[test]
    fn a_recall_frees_the_memory_when_the_close_stops_writes() {
        let fabric = fabric_whose_close_stops_writes();
        let mut memory = vec![0u8; 4096];
        let (buffer, released) = region(&fabric, &mut memory);
        let (_, loan) = buffer.lend_for_get(b"a", 0, 64).unwrap();

        loan.recall().expect("closes");
        drop(buffer);
        fabric.wait_for_closes();
        assert!(
            released.load(Ordering::Acquire),
            "freed without waiting for the fabric"
        );
        assert_eq!(fabric.parked_bytes(), 0);
    }

    #[test]
    fn handles_cross_threads() {
        fn send_and_sync<T: Send + Sync>() {}
        fn send<T: Send>() {}
        send_and_sync::<RdmaSharedBuffer>();
        send::<super::LentWindow>();
    }

    #[test]
    fn a_buffer_whose_close_failed_is_closed_though_not_revoked() {
        let fabric = fabric();
        let mut memory = vec![0u8; 4096];
        let (buffer, released) = region(&fabric, &mut memory);
        let (_, loan) = buffer.lend_for_get(b"a", 0, 64).unwrap();

        fabric.fail_next_closes(1);
        loan.recall().expect_err("the close fails");
        assert!(
            !buffer.is_revoked(),
            "the server may still reach the memory"
        );
        assert!(buffer.is_closed(), "but nothing can be lent");
        assert!(buffer.lend_for_get(b"b", 64, 64).is_err());

        drop(buffer);
        fabric.wait_for_closes();
        assert!(!released.load(Ordering::Acquire), "the memory is leaked");
        assert_eq!(fabric.leaked_bytes(), 4096);
        // Still registered as far as the fabric knows, so never freed.
        std::mem::forget(memory);
    }

    #[test]
    fn a_dropped_loan_revokes_the_region_without_waiting() {
        let fabric = fabric();
        let mut memory = vec![0u8; 4096];
        let (region, _) = region(&fabric, &mut memory);
        let (_, loan) = region.lend_for_get(b"a", 0, 64).unwrap();
        drop(loan);
        fabric.wait_for_closes();
        assert!(region.is_revoked());
    }

    #[test]
    fn answered_loans_free_the_memory_when_the_last_handle_drops() {
        let fabric = fabric();
        let mut memory = vec![0u8; 4096];
        let (region, released) = region(&fabric, &mut memory);
        let clone = region.clone();
        let (_, loan) = clone.lend_for_get(b"a", 0, 64).unwrap();
        drop((region, clone));
        assert!(!released.load(Ordering::Acquire), "the loan still holds it");
        // SAFETY: no command was sent.
        unsafe { loan.reclaim(TransferReply::Missing) }.unwrap();
        fabric.wait_for_closes();
        assert!(released.load(Ordering::Acquire));
        assert_eq!(fabric.parked_bytes(), 0);
    }

    #[test]
    fn a_read_past_the_window_revokes_the_region() {
        let fabric = fabric();
        let mut memory = vec![0u8; 4096];
        let (region, _) = region(&fabric, &mut memory);
        let (_, loan) = region.lend_for_get(b"a", 0, 64).unwrap();
        let receipt = crate::command::ReadReceipt {
            bytes_written: 65,
            checksum: 0,
        };
        // SAFETY: no command was sent.
        let error = unsafe { loan.reclaim(TransferReply::Read(receipt)) }.unwrap_err();
        assert!(matches!(error, RdmaError::PayloadTooLarge { .. }));
        assert!(region.is_revoked(), "other windows may hold its bytes");
    }

    #[test]
    fn windows_lend_from_many_threads_at_once() {
        let fabric = fabric();
        let mut memory = vec![0u8; 64 * 16];
        let (region, _) = region(&fabric, &mut memory);
        std::thread::scope(|scope| {
            for t in 0..16 {
                let region = region.clone();
                scope.spawn(move || {
                    for _ in 0..1000 {
                        let (_, loan) = region.lend_for_get(b"k", t * 64, 64).expect("lends");
                        // SAFETY: no command was sent.
                        unsafe { loan.reclaim(TransferReply::Missing) }.unwrap();
                    }
                });
            }
        });
        assert!(!region.is_revoked());
    }
}
