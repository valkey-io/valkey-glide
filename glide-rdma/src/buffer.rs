// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Registered memory lent to the server for one transfer at a time.
//!
//! A buffer is always in exactly one of two states, each with its own type:
//!
//! - [`RdmaBuffer`]: no active transfer, Rust may read and write it.
//! - [`LentBuffer`]: a transfer command has been built for the memory and the
//!   server has not replied yet. Nothing can reach the bytes.
//!
//! [`RdmaBuffer::lend_for_get`] and [`RdmaBuffer::lend_for_set`] are the only way to
//! build a transfer command and they consume the buffer to do it. The buffer comes
//! back from [`LentBuffer::reclaim`] once the server has replied, or from
//! [`LentBuffer::cancel_unsent`] if the command never went out. Any other end closes
//! the region and then frees the memory on efa-direct, or parks it in the fabric on
//! tcp.
//!
//! Dropping either type does not close the region on the dropping thread: the
//! fabric's closing thread does it, then frees, parks or leaks the memory exactly
//! as the drop would have. A close can take milliseconds on efa, and a `Drop`
//! cannot `.await`, so this keeps a cancelled transfer from stalling an async
//! runtime.

use std::fmt;
use std::mem::ManuallyDrop;
use std::ptr::{self, NonNull};

use crate::command::{self, RdmaCommand, ReadReceipt, SetChecksum, TransferReply};
use crate::endpoint::{Registration, RevokeHandle};
use crate::error::RdmaError;
use crate::fabric::RdmaFabric;
use crate::progress::ProgressGuard;
use crate::region_ref::RegionRef;

/// Memory handed over by the caller, kept at one fixed address until it is freed.
///
/// The caller's `AsMut::as_mut` is called exactly once when this is created. Every
/// later access goes through the pointer that call returned.
pub(crate) struct HostMemory {
    /// This is dropped to free the memory at the end.
    owner: NonNull<dyn AsMut<[u8]> + Send>,
    bytes: NonNull<[u8]>,
}

// SAFETY: `owner` is `Send`, and `bytes` points into memory `owner` owns, so moving
// both to another thread together is moving the caller's `Send` value.
unsafe impl Send for HostMemory {}

impl HostMemory {
    pub(crate) fn new(memory: impl AsMut<[u8]> + Send + 'static) -> Self {
        // Turned into a raw pointer before `bytes` is taken from it, so it is never
        // used as a reference afterwards. Writing through a reference to the
        // caller's value after taking `bytes` would invalidate `bytes` whenever the
        // data lives inside that value, as it does in a `[u8; N]`.
        let owner: NonNull<dyn AsMut<[u8]> + Send> = NonNull::from(Box::leak(Box::new(memory)));
        // SAFETY: `owner` was just leaked, so nothing else refers to it.
        let bytes = NonNull::from(unsafe { (*owner.as_ptr()).as_mut() });
        Self { owner, bytes }
    }

    pub(crate) fn len(&self) -> usize {
        self.bytes.len()
    }

    pub(crate) fn bytes(&self) -> &[u8] {
        // SAFETY: `bytes` stays valid until `self` drops. The only `&mut` to it comes
        // from `bytes_mut`, which needs `&mut self`, so the two never overlap.
        unsafe { self.bytes.as_ref() }
    }

    /// For registering: a peer writes through the region, so no `&[u8]`-derived pointer.
    pub(crate) fn bytes_ptr(&self) -> NonNull<[u8]> {
        self.bytes
    }

    fn bytes_mut(&mut self) -> &mut [u8] {
        // SAFETY: as in `bytes`, and `&mut self` makes this the only reference.
        unsafe { self.bytes.as_mut() }
    }
}

impl fmt::Debug for HostMemory {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("HostMemory")
            .field("address", &self.bytes.cast::<u8>().as_ptr())
            .field("len", &self.len())
            .finish()
    }
}

impl Drop for HostMemory {
    fn drop(&mut self) {
        // SAFETY: `owner` came from `Box::leak` in `new` and is freed only here.
        drop(unsafe { Box::from_raw(self.owner.as_ptr()) });
    }
}

/// Host memory registered with a [`RdmaFabric`] and not lent to any transfer.
///
/// Registration is expensive, so one buffer is meant to serve many transfers in turn:
/// lend it, get it back once the server replies, and lend it again.
pub struct RdmaBuffer {
    /// Taken by `Drop`, which hands it to the fabric's closing thread.
    registration: ManuallyDrop<Registration>,
    region_ref: RegionRef,
    /// Freed only once the region is closed; see the `Drop` impl.
    host: ManuallyDrop<HostMemory>,
    /// Held so the domain outlives the registration.
    fabric: RdmaFabric,
}

// A failed lend or reclaim returns the buffer inside the `Err` so the caller never
// loses memory it registered. That makes the `Err` large, but only on a rare path.
#[allow(clippy::result_large_err)]
impl RdmaBuffer {
    pub(crate) fn new(
        registration: Registration,
        region_ref: RegionRef,
        host: HostMemory,
        fabric: RdmaFabric,
    ) -> Self {
        Self {
            registration: ManuallyDrop::new(registration),
            region_ref,
            host: ManuallyDrop::new(host),
            fabric,
        }
    }

    /// Bytes registered, capping what one transfer can move through this buffer.
    pub fn capacity(&self) -> usize {
        self.host.len()
    }

    /// Whether this buffer was registered with `fabric`.
    pub fn is_registered_on(&self, fabric: &RdmaFabric) -> bool {
        self.fabric.is(fabric)
    }

    /// The fabric this buffer was registered with. Clone it to wait for the
    /// buffer's region to close after dropping it; see
    /// [`RdmaFabric::wait_for_closes`].
    pub fn fabric(&self) -> &RdmaFabric {
        &self.fabric
    }

    /// Lend `[at, at + length)` of this buffer to a `BLOB.RDMA_GET` of `key` for the server
    /// to write the stored object into.
    ///
    /// Returns the command to send and the loan to hold until the server replies.
    ///
    /// # Errors
    ///
    /// Hands the buffer back, unlent, when the window runs past the end of the buffer,
    /// the buffer has been revoked, or a revoke of it failed.
    pub fn lend_for_get(
        self,
        key: &[u8],
        at: usize,
        length: usize,
    ) -> Result<(RdmaCommand, LentBuffer), (Self, RdmaError)> {
        self.lend(at, length, |_, window| command::get(key, length, window))
    }

    /// Lend `[at, at + length)` of this buffer to a `BLOB.RDMA_SET` of `key` for the server
    /// to read those bytes and store them.
    ///
    /// `checksum` decides whether the command carries a CRC-32c of the window for
    /// the server to check before it stores the bytes; see [`SetChecksum`].
    ///
    /// Returns the command to send and the loan to hold until the server replies.
    ///
    /// # Errors
    ///
    /// Hands the buffer back, unlent, when the window runs past the end of the buffer,
    /// the buffer has been revoked, or a revoke of it failed.
    pub fn lend_for_set(
        self,
        key: &[u8],
        at: usize,
        length: usize,
        checksum: SetChecksum,
    ) -> Result<(RdmaCommand, LentBuffer), (Self, RdmaError)> {
        self.lend(at, length, |bytes, window| {
            let checksum = match checksum {
                SetChecksum::Skip => None,
                SetChecksum::Compute => Some(crate::checksum(bytes)),
                SetChecksum::Known(checksum) => Some(checksum),
            };
            command::set(key, length, checksum, window)
        })
    }

    fn lend(
        self,
        at: usize,
        length: usize,
        build: impl FnOnce(&[u8], &RegionRef) -> RdmaCommand,
    ) -> Result<(RdmaCommand, LentBuffer), (Self, RdmaError)> {
        if let Err(error) = self.registration.check_open() {
            return Err((self, error));
        }
        let Some(window) = self.window(at, length) else {
            let error = RdmaError::Configuration(format!(
                "window [{at}, {at}+{length}) runs past the end of a registered region \
                 of {} bytes",
                self.capacity()
            ));
            return Err((self, error));
        };
        // `window` succeeded, so the range is in bounds.
        let command = build(&self.as_host()[at..at + length], &window);
        let progress = self.fabric.drive_progress();
        let loan = LentBuffer {
            buffer: ManuallyDrop::new(self),
            window_length: length,
            _progress: progress,
        };
        Ok((command, loan))
    }

    /// The region reference for `[at, at + length)` of this registration, or `None` if
    /// that runs past the end.
    ///
    /// The remote key covers the whole region, so only the address moves.
    fn window(&self, at: usize, length: usize) -> Option<RegionRef> {
        if self.capacity() < at.checked_add(length)? {
            return None;
        }
        Some(RegionRef {
            remote_key: self.region_ref.remote_key,
            remote_address: self
                .region_ref
                .remote_address
                .checked_add(u64::try_from(at).ok()?)?,
        })
    }

    /// Deregister this buffer so the server can never reach it again and it can
    /// never be lent again. A no-op if it is already revoked.
    ///
    /// The memory itself stays readable and writable through this handle; only the
    /// fabric's access to it ends. To use the memory for transfers again, register a
    /// new buffer.
    ///
    /// A failed close is final: later revokes report the same error, and the
    /// memory is leaked on drop rather than freed; see [`RdmaFabric::leaked_bytes`].
    pub fn revoke(&self) -> Result<(), RdmaError> {
        self.registration.revoke()
    }

    /// Whether this buffer has been revoked.
    pub fn is_revoked(&self) -> bool {
        self.registration.is_revoked()
    }

    /// Resolves once this buffer is revoked.
    ///
    /// Borrows nothing so it can still be awaited after the buffer is lent. If the
    /// buffer is dropped, it resolves once the region closes, never if that failed.
    pub fn revoked(&self) -> impl Future<Output = ()> + Send + 'static {
        self.registration.revoked()
    }

    /// A handle that revokes this buffer from elsewhere, such as another thread, even
    /// while it is lent.
    ///
    /// It does not keep the buffer or its registration alive: once the buffer is
    /// dropped, revoking through the handle does nothing.
    pub fn revoker(&self) -> RdmaRevoker {
        RdmaRevoker(self.registration.handle())
    }

    /// The registered bytes.
    pub fn as_host(&self) -> &[u8] {
        self.host.bytes()
    }

    /// The registered bytes, for writing.
    pub fn as_host_mut(&mut self) -> &mut [u8] {
        self.host.bytes_mut()
    }

    /// Copy `value` into the front of the buffer, returning the bytes staged, or `None`
    /// without staging when it will not fit.
    pub fn copy_from(&mut self, value: &[u8]) -> Option<usize> {
        let destination = self.as_host_mut().get_mut(..value.len())?;
        destination.copy_from_slice(value);
        Some(value.len())
    }
}

impl RdmaBuffer {
    /// Take the buffer apart without running its `Drop`.
    fn into_parts(self) -> (Registration, HostMemory, RdmaFabric) {
        let this = ManuallyDrop::new(self);
        // SAFETY: `this` is never dropped, so each field is read out exactly once.
        unsafe {
            (
                ManuallyDrop::into_inner(ptr::read(&this.registration)),
                ManuallyDrop::into_inner(ptr::read(&this.host)),
                ptr::read(&this.fabric),
            )
        }
    }
}

impl Drop for RdmaBuffer {
    /// Free the memory only after the region over it is closed. If the region cannot
    /// be closed, the server may still reach the memory, so it is leaked instead.
    ///
    /// Both happen on the fabric's closing thread, so this returns without waiting
    /// for the close.
    ///
    /// Only an unlent buffer gets here; a loan decides for itself, see [`LentBuffer`].
    fn drop(&mut self) {
        // SAFETY: both are taken exactly once, here, and not touched again.
        let (registration, host) = unsafe {
            (
                ManuallyDrop::take(&mut self.registration),
                ManuallyDrop::take(&mut self.host),
            )
        };
        self.fabric.clone().close_later(move |fabric| {
            if registration.revoke().is_ok() {
                drop(host);
            } else {
                fabric.leak(host);
            }
            drop(registration);
        });
    }
}

impl fmt::Debug for RdmaBuffer {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("RdmaBuffer")
            .field("capacity", &self.capacity())
            .field("region_ref", &self.region_ref)
            .field("registration", &self.registration)
            .finish()
    }
}

/// A [`RdmaBuffer`] lent to one transfer from [`RdmaBuffer::lend_for_get`] or
/// [`RdmaBuffer::lend_for_set`].
///
/// The server may be using the memory at any moment while this exists.
/// There are four ways for a loan to end:
///
/// - [`Self::reclaim`] once the server has replied. The buffer can then be read and
///   lent again.
/// - [`Self::cancel_unsent`] when the command was never sent, which hands the
///   buffer straight back.
/// - [`Self::recall`] when no reply is coming, for example because the connection
///   failed or the caller gave up. This revokes the buffer and then frees or parks
///   its memory, as below.
/// - Dropping the loan, for example when the future awaiting the reply is
///   cancelled. As a recall, except that a failed revoke is not reported, and
///   the revoke runs on the fabric's closing thread, so the drop does not wait
///   for it.
///
/// What happens to the memory of a loan that ends without a reply depends on the
/// provider:
///
/// - On efa-direct, closing the region also aborts writes already in flight, so the
///   memory is freed as soon as the revoke succeeds.
/// - On tcp, closing a region does not stop a write the provider already accepted
///   (`fi_mr(3)`), so the memory is parked in the fabric and freed only when the
///   fabric closes.
/// - On either, if the revoke fails, the memory is leaked for the life of the process,
///   because the server may still reach it; see [`RdmaFabric::leaked_bytes`].
///
/// Parked memory only accumulates; [`RdmaFabric::parked_bytes`] reports how much. To
/// get it back, drop every buffer, loan, session and handle on the fabric. Register
/// fresh memory to carry on.
///
/// On tcp, the fabric's progress thread keeps polling for as long as a loan exists,
/// because that provider moves no bytes unless the client polls.
#[derive(Debug)]
pub struct LentBuffer {
    /// Never exposed; `reclaim`, `recall`, and `Drop` each take it apart.
    buffer: ManuallyDrop<RdmaBuffer>,
    window_length: usize,
    /// Declared last so the region is revoked before progress polling stops.
    _progress: Option<ProgressGuard>,
}

// As for `RdmaBuffer`: the buffer rides in the `Err` so it is never lost.
#[allow(clippy::result_large_err)]
impl LentBuffer {
    /// End the loan with the server's reply to the transfer command.
    ///
    /// # Safety
    ///
    /// `reply` must be the server's reply to this loan's command: until it replies
    /// the server may still be writing the window. A command never sent is also
    /// sound, though [`Self::cancel_unsent`] is the way to end that loan.
    ///
    /// # Errors
    ///
    /// [`RdmaError::PayloadTooLarge`] when the server reports writing more than the
    /// window held. The bytes past the window may have overwritten other parts of the
    /// buffer. The buffer is still handed back, because a reply means the server is
    /// done with the memory; see [`TransferReply`].
    pub unsafe fn reclaim(
        self,
        reply: TransferReply,
    ) -> Result<(RdmaBuffer, Option<ReadReceipt>), (RdmaBuffer, RdmaError)> {
        let (buffer, window_length, _progress) = self.dismantle();
        match reply {
            TransferReply::Read(receipt) if receipt.bytes_written > window_length => {
                let error = RdmaError::PayloadTooLarge {
                    value_bytes: receipt.bytes_written,
                    capacity: window_length,
                };
                Err((buffer, error))
            }
            TransferReply::Read(receipt) => Ok((buffer, Some(receipt))),
            TransferReply::Missing | TransferReply::Stored | TransferReply::Failed => {
                Ok((buffer, None))
            }
        }
    }

    /// End the loan without a reply: revoke the buffer, then free its memory on
    /// efa-direct or park it on tcp; see [`LentBuffer`].
    ///
    /// Nothing comes back. Parked memory stays untouchable until the fabric closes.
    ///
    /// # Errors
    ///
    /// When libfabric fails to close the region, the loan is handed back unchanged.
    /// A failed close is final; drop the loan, which leaks the memory for the life of
    /// the process; see [`RdmaFabric::leaked_bytes`].
    pub fn recall(self) -> Result<(), (Self, RdmaError)> {
        if let Err(error) = self.buffer.revoke() {
            return Err((self, error));
        }
        let (buffer, _, _progress) = self.dismantle();
        let (registration, host, fabric) = buffer.into_parts();
        fabric.release_unanswered(host, true);
        drop(registration);
        Ok(())
    }

    /// End a loan whose command was never sent, handing the buffer straight back.
    ///
    /// If an [`RdmaRevoker`] fired meanwhile, the buffer comes back revoked.
    ///
    /// # Safety
    ///
    /// The command this loan was lent for must not have been sent. Once it has, only
    /// the server's reply says it is done with the window.
    pub unsafe fn cancel_unsent(self) -> RdmaBuffer {
        let (buffer, _, _progress) = self.dismantle();
        buffer
    }

    /// Whether the buffer has been revoked, for example through an [`RdmaRevoker`].
    pub fn is_revoked(&self) -> bool {
        self.buffer.is_revoked()
    }

    /// Resolves once the buffer is revoked, so a caller can stop waiting for a reply
    /// that will now never be useful.
    pub fn revoked(&self) -> impl Future<Output = ()> + Send + 'static {
        self.buffer.revoked()
    }

    /// A handle that revokes the buffer from elsewhere as [`RdmaBuffer::revoker`].
    pub fn revoker(&self) -> RdmaRevoker {
        self.buffer.revoker()
    }

    /// Take the loan apart without running its `Drop`.
    fn dismantle(self) -> (RdmaBuffer, usize, Option<ProgressGuard>) {
        let mut this = ManuallyDrop::new(self);
        // SAFETY: `this` is never dropped, so each field is moved out exactly once.
        unsafe {
            (
                ManuallyDrop::take(&mut this.buffer),
                this.window_length,
                ptr::read(&this._progress),
            )
        }
    }
}

impl Drop for LentBuffer {
    /// Revoke the region, then free or park the memory as [`LentBuffer::recall`]
    /// does. A failed revoke is not returned; the memory is leaked, and the failure
    /// is logged and counted in [`RdmaFabric::leaked_bytes`].
    ///
    /// All of it runs on the fabric's closing thread, so this returns without
    /// waiting for the close.
    fn drop(&mut self) {
        // SAFETY: `buffer` is taken exactly once, here.
        let buffer = unsafe { ManuallyDrop::take(&mut self.buffer) };
        let progress = self._progress.take();
        let (registration, host, fabric) = buffer.into_parts();
        fabric.close_later(move |fabric| {
            let closed = registration.revoke().is_ok();
            fabric.release_unanswered(host, closed);
            drop(registration);
            // Polling stops only once the region is closed, as for any other loan.
            drop(progress);
        });
    }
}

/// Revokes a [`RdmaBuffer`] from elsewhere, whether or not it is lent. From
/// [`RdmaBuffer::revoker`] or [`LentBuffer::revoker`].
#[derive(Debug, Clone)]
pub struct RdmaRevoker(RevokeHandle);

impl RdmaRevoker {
    /// Revoke the buffer, as [`RdmaBuffer::revoke`] does. A no-op if it is already
    /// revoked or was dropped and its region closed.
    ///
    /// # Errors
    ///
    /// When libfabric failed to close the region, now or earlier.
    pub fn revoke(&self) -> Result<(), RdmaError> {
        self.0.revoke()
    }

    /// Whether the buffer's region is closed. A buffer dropped
    /// while its region failed to close is not released.
    pub fn is_released(&self) -> bool {
        self.0.is_released()
    }
}

#[cfg(test)]
mod tests {
    use super::{LentBuffer, RdmaBuffer, RdmaRevoker};
    use crate::command::{RdmaCommand, ReadReceipt, SetChecksum, TransferReply};
    use crate::config::{FabricConfig, Provider};
    use crate::error::RdmaError;
    use crate::fabric::RdmaFabric;
    use crate::fabric::tests::fabric_whose_close_stops_writes;
    use crate::region_ref::RegionRef;
    use std::sync::Arc;
    use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
    use std::sync::mpsc;
    use std::time::{Duration, Instant};

    impl RdmaBuffer {
        /// Where this buffer lives, as a transfer command names it.
        pub(crate) fn region_ref(&self) -> &RegionRef {
            &self.region_ref
        }
    }

    fn fabric() -> RdmaFabric {
        RdmaFabric::open(&FabricConfig::new(Provider::Tcp)).expect("tcp should open")
    }

    fn arguments(command: &RdmaCommand) -> Vec<String> {
        command
            .arguments()
            .iter()
            .map(|argument| String::from_utf8_lossy(argument).into_owned())
            .collect()
    }

    fn receipt(bytes_written: usize) -> TransferReply {
        TransferReply::Read(ReadReceipt {
            bytes_written,
            checksum: 0,
        })
    }

    fn lent_for_get(buffer: RdmaBuffer) -> LentBuffer {
        buffer.lend_for_get(b"key", 0, 64).expect("lends").1
    }

    fn lent_for_set(buffer: RdmaBuffer) -> LentBuffer {
        buffer
            .lend_for_set(b"key", 0, 64, SetChecksum::Skip)
            .expect("lends")
            .1
    }

    #[test]
    fn lending_for_get_names_the_window() {
        let buffer = fabric().register(vec![0u8; 4096]).unwrap();
        let region_ref = buffer.region_ref().clone();

        let (command, _loan) = buffer.lend_for_get(b"key", 1024, 256).expect("lends");

        assert_eq!(command.name(), "BLOB.RDMA_GET");
        assert_eq!(
            arguments(&command),
            [
                "key".to_string(),
                region_ref.remote_key.to_string(),
                (region_ref.remote_address + 1024).to_string(),
                "256".to_string(),
            ]
        );
    }

    #[test]
    fn lending_for_set_names_the_window_and_its_length() {
        let buffer = fabric().register(vec![0u8; 4096]).unwrap();
        let region_ref = buffer.region_ref().clone();

        let (command, _loan) = buffer
            .lend_for_set(b"key", 1024, 256, SetChecksum::Skip)
            .expect("lends");

        assert_eq!(command.name(), "BLOB.RDMA_SET");
        assert_eq!(
            arguments(&command),
            [
                "key".to_string(),
                "256".to_string(),
                region_ref.remote_key.to_string(),
                (region_ref.remote_address + 1024).to_string(),
                "256".to_string(),
            ]
        );
    }

    /// A computed checksum covers the lent window only, not the bytes around it.
    #[test]
    fn lending_for_set_checksums_only_the_window() {
        let mut buffer = fabric().register(vec![0u8; 4096]).unwrap();
        for (index, byte) in buffer.as_host_mut().iter_mut().enumerate() {
            *byte = (index % 251) as u8;
        }
        let expected = crate::checksum(&buffer.as_host()[1024..1280]);
        assert_ne!(expected, crate::checksum(&buffer.as_host()[..256]));

        let (command, _loan) = buffer
            .lend_for_set(b"key", 1024, 256, SetChecksum::Compute)
            .expect("lends");

        assert_eq!(
            arguments(&command)[2..4],
            ["CRC".to_string(), expected.to_string()]
        );
    }

    /// A known checksum is sent as given, without reading the window.
    #[test]
    fn lending_for_set_sends_a_known_checksum_as_given() {
        let buffer = fabric().register(vec![0u8; 4096]).unwrap();

        let (command, _loan) = buffer
            .lend_for_set(b"key", 0, 64, SetChecksum::Known(42))
            .expect("lends");

        assert_eq!(
            arguments(&command)[2..4],
            ["CRC".to_string(), "42".to_string()]
        );
    }

    /// A window that runs past the end is refused before any checksum is computed,
    /// so a bad window cannot read out of bounds.
    #[test]
    fn a_window_past_the_end_is_refused_before_its_checksum() {
        let buffer = fabric().register(vec![0u8; 1024]).unwrap();
        let (_, error) = buffer
            .lend_for_set(b"key", 1000, 64, SetChecksum::Compute)
            .unwrap_err();
        assert!(matches!(error, RdmaError::Configuration(_)), "{error:?}");
    }

    #[test]
    fn a_window_past_the_end_is_not_lent_and_the_buffer_comes_back() {
        let mut buffer = fabric().register(vec![0u8; 1024]).unwrap();

        for (at, length, why) in [
            (1000, 25, "runs past the end"),
            (0, 1025, "longer than the buffer"),
            (1024, 1, "starts at the end"),
            (usize::MAX, 1, "offset overflows"),
        ] {
            let (returned, error) = buffer.lend_for_get(b"key", at, length).unwrap_err();
            assert!(
                matches!(error, RdmaError::Configuration(_)),
                "{why}: {error:?}"
            );
            buffer = returned;
        }
        let (mut buffer, error) = buffer
            .lend_for_set(b"key", 0, 1025, SetChecksum::Skip)
            .unwrap_err();
        assert!(matches!(error, RdmaError::Configuration(_)), "{error:?}");

        // Still usable, right up to the last byte.
        for (at, length) in [(0, 1024), (1024, 0)] {
            let (_, loan) = buffer
                .lend_for_get(b"key", at, length)
                .unwrap_or_else(|(_, error)| panic!("[{at}, +{length}) fits: {error}"));
            buffer = unsafe { loan.reclaim(TransferReply::Missing) }.unwrap().0;
        }
    }

    #[test]
    fn a_revoked_buffer_is_not_lent() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        buffer.revoke().unwrap();

        let (buffer, error) = buffer.lend_for_get(b"key", 0, 64).unwrap_err();
        assert_eq!(error, RdmaError::Revoked);
        let (_, error) = buffer
            .lend_for_set(b"key", 0, 64, SetChecksum::Skip)
            .unwrap_err();
        assert_eq!(error, RdmaError::Revoked);
    }

    /// A failed close still frees the handle on efa, so the key could name some later
    /// region. The buffer is not revoked, but it must not be lent either.
    #[test]
    fn a_buffer_whose_revoke_failed_is_not_lent() {
        let fabric = fabric();
        let buffer = fabric.register(vec![0u8; 64]).unwrap();
        fabric.fail_next_closes(1);
        let failed = buffer.revoke().expect_err("the close fails");
        assert!(
            !buffer.is_revoked(),
            "the server may still reach the memory"
        );

        let closes = fabric.close_calls();
        let (buffer, error) = buffer.lend_for_get(b"key", 0, 64).unwrap_err();
        assert_eq!(error, failed);
        let (_, error) = buffer
            .lend_for_set(b"key", 0, 64, SetChecksum::Skip)
            .unwrap_err();
        assert_eq!(error, failed);
        assert_eq!(
            fabric.close_calls(),
            closes,
            "refusing to lend does not try the close again"
        );
    }

    #[test]
    fn a_read_reply_returns_the_buffer_and_its_receipt() {
        let mut buffer = fabric().register(vec![0u8; 64]).unwrap();
        buffer.copy_from(b"landed").unwrap();

        let (buffer, reported) =
            unsafe { lent_for_get(buffer).reclaim(receipt(6)) }.expect("reclaims");

        assert_eq!(reported.map(|receipt| receipt.bytes_written), Some(6));
        assert_eq!(&buffer.as_host()[..6], b"landed");
        assert!(!buffer.is_revoked(), "a reclaimed buffer can be lent again");
        assert!(buffer.lend_for_get(b"key", 0, 64).is_ok());
    }

    /// Lending moves the handle, not the memory: the bytes the server writes into stay
    /// at the address that was registered, and are never copied.
    #[test]
    fn lending_moves_the_handle_but_not_the_bytes() {
        let buffer = fabric().register(vec![0u8; 1 << 20]).unwrap();
        let registered_at = buffer.as_host().as_ptr();

        let (_, loan) = buffer.lend_for_get(b"key", 0, 1 << 20).unwrap();
        let moved_loan = Box::new(loan);
        let (buffer, _) = unsafe { moved_loan.reclaim(receipt(1)) }.unwrap();

        assert_eq!(buffer.as_host().as_ptr(), registered_at);
        assert!(size_of::<RdmaBuffer>() < 256, "the handle stays small");
        assert!(size_of::<LentBuffer>() < 256, "the loan stays small");
    }

    #[test]
    fn a_receipt_that_fills_the_window_exactly_is_accepted() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        assert!(unsafe { lent_for_get(buffer).reclaim(receipt(64)) }.is_ok());
    }

    #[test]
    fn a_receipt_larger_than_the_window_is_an_error_but_returns_the_buffer() {
        let buffer = fabric().register(vec![0u8; 128]).unwrap();
        let (_, loan) = buffer.lend_for_get(b"key", 0, 64).unwrap();

        let (buffer, error) = unsafe { loan.reclaim(receipt(65)) }.unwrap_err();

        assert_eq!(
            error,
            RdmaError::PayloadTooLarge {
                value_bytes: 65,
                capacity: 64
            }
        );
        assert!(!buffer.is_revoked(), "the server replied, so it is done");
    }

    #[test]
    fn every_reply_that_ends_a_transfer_returns_the_buffer() {
        let fabric = fabric();
        let register = || fabric.register(vec![0u8; 64]).unwrap();

        let (_, reported) = unsafe { lent_for_get(register()).reclaim(TransferReply::Missing) }
            .expect("a missing key ends a BLOB.RDMA_GET");
        assert_eq!(reported, None);

        let (_, reported) = unsafe { lent_for_set(register()).reclaim(TransferReply::Stored) }
            .expect("a store ends a BLOB.RDMA_SET");
        assert_eq!(reported, None);

        for loan in [lent_for_get(register()), lent_for_set(register())] {
            let (_, reported) =
                unsafe { loan.reclaim(TransferReply::Failed) }.expect("an error reply ends either");
            assert_eq!(reported, None);
        }
    }

    /// An accepted write can land after the region closes, so the memory must
    /// outlive the endpoint, not the loan. The cost is that parked memory is held
    /// for the fabric's whole lifetime.
    #[test]
    fn recalling_a_loan_parks_its_memory_until_the_fabric_closes() {
        let fabric = fabric();
        let (memory, freed, _) = tracked();
        let size = memory.bytes.len();
        let loan = lent_for_get(fabric.register(memory).unwrap());

        loan.recall().expect("the region closes");

        assert!(
            !freed.load(Ordering::SeqCst),
            "recalled memory is not freed"
        );
        assert_eq!(fabric.parked(), 1);
        assert_eq!(fabric.parked_bytes(), size);
        drop(fabric);
        assert!(freed.load(Ordering::SeqCst), "the fabric frees it on close");
    }

    /// Nothing was sent, so nothing can be in flight.
    #[test]
    fn cancelling_an_unsent_loan_hands_the_buffer_back() {
        let fabric = fabric();
        let (memory, freed, _) = tracked();
        let loan = lent_for_get(fabric.register(memory).unwrap());
        assert_eq!(fabric.transfers_in_flight(), 1);

        let buffer = unsafe { loan.cancel_unsent() };

        assert!(!buffer.is_revoked());
        assert_eq!(fabric.transfers_in_flight(), 0);
        assert_eq!(fabric.parked_bytes(), 0);
        drop(buffer);
        fabric.wait_for_closes();
        assert!(
            freed.load(Ordering::SeqCst),
            "freed as any unlent buffer is"
        );
    }

    #[test]
    fn cancelling_an_unsent_loan_after_a_revoke_hands_back_a_revoked_buffer() {
        let loan = lent_for_get(fabric().register(vec![0u8; 64]).unwrap());
        loan.revoker().revoke().unwrap();

        let buffer = unsafe { loan.cancel_unsent() };

        assert!(buffer.is_revoked());
        let (_, error) = buffer.lend_for_get(b"key", 0, 64).unwrap_err();
        assert!(matches!(error, RdmaError::Revoked), "{error:?}");
    }

    /// On a provider whose close also stops writes in flight, as efa-direct's does,
    /// nothing can land after the revoke, so the memory is freed at once.
    #[test]
    fn a_recall_frees_the_memory_when_the_close_stops_writes() {
        let fabric = fabric_whose_close_stops_writes();
        let (memory, freed, _) = tracked();
        let loan = lent_for_get(fabric.register(memory).unwrap());

        loan.recall().expect("the region closes");

        assert!(
            freed.load(Ordering::SeqCst),
            "freed without waiting for the fabric"
        );
        assert_eq!(fabric.parked(), 0);
        assert_eq!(fabric.parked_bytes(), 0);
    }

    #[test]
    fn dropping_a_loan_frees_the_memory_when_the_close_stops_writes() {
        let fabric = fabric_whose_close_stops_writes();
        let (memory, freed, _) = tracked();

        drop(lent_for_set(fabric.register(memory).unwrap()));
        fabric.wait_for_closes();

        assert!(freed.load(Ordering::SeqCst));
        assert_eq!(fabric.parked(), 0);
    }

    /// A revoker closed the region first, so ending the loan finds it already closed.
    #[test]
    fn a_loan_revoked_from_elsewhere_is_freed_when_the_close_stops_writes() {
        let fabric = fabric_whose_close_stops_writes();
        let (memory, freed, _) = tracked();
        let loan = lent_for_get(fabric.register(memory).unwrap());
        loan.revoker().revoke().expect("the region closes");

        drop(loan);
        fabric.wait_for_closes();

        assert!(freed.load(Ordering::SeqCst));
        assert_eq!(fabric.parked(), 0);
    }

    /// The server may still reach a region whose close failed, whatever the provider,
    /// so its memory is leaked, and even closing the fabric does not free it.
    #[test]
    fn a_failed_close_leaks_even_when_the_close_would_stop_writes() {
        let fabric = fabric_whose_close_stops_writes();
        let (memory, freed, _) = tracked();
        let size = memory.bytes.len();
        let loan = lent_for_get(fabric.register(memory).unwrap());

        fabric.fail_next_closes(1);
        let (loan, _) = loan.recall().expect_err("the close fails");
        drop(loan);
        fabric.wait_for_closes();

        assert!(
            !freed.load(Ordering::SeqCst),
            "the server may still reach it"
        );
        assert_eq!(fabric.leaked_bytes(), size);
        assert_eq!(fabric.parked_bytes(), 0, "leaked, not parked");
        drop(fabric);
        assert!(
            !freed.load(Ordering::SeqCst),
            "kept for the life of the process"
        );
    }

    #[test]
    fn dropping_a_loan_parks_its_memory_too() {
        let fabric = fabric();
        let (memory, freed, _) = tracked();

        drop(lent_for_set(fabric.register(memory).unwrap()));
        fabric.wait_for_closes();

        assert!(!freed.load(Ordering::SeqCst));
        assert_eq!(fabric.parked(), 1);
        drop(fabric);
        assert!(freed.load(Ordering::SeqCst));
    }

    /// A reply means the server is done, so a reclaimed buffer frees as usual.
    #[test]
    fn reclaiming_a_loan_hands_the_memory_back_unparked() {
        let fabric = fabric();
        let (memory, freed, _) = tracked();

        let loan = lent_for_get(fabric.register(memory).unwrap());
        let (buffer, _) = unsafe { loan.reclaim(TransferReply::Missing) }.unwrap();
        drop(buffer);
        fabric.wait_for_closes();

        assert!(freed.load(Ordering::SeqCst));
        assert_eq!(fabric.parked(), 0);
    }

    /// efa frees the handle even when `fi_close` fails, so it is never closed again.
    #[test]
    fn a_failed_recall_hands_back_the_loan_and_never_closes_the_region_again() {
        let fabric = fabric();
        let loan = lent_for_get(fabric.register(vec![0u8; 64]).unwrap());

        fabric.fail_next_closes(1);
        let (loan, error) = loan.recall().unwrap_err();
        assert!(matches!(error, RdmaError::Fabric { .. }), "{error:?}");
        assert!(!loan.is_revoked(), "the server may still reach the memory");

        let closes = fabric.close_calls();
        let (loan, again) = loan.recall().unwrap_err();
        assert_eq!(
            fabric.close_calls(),
            closes,
            "the handle was not passed to fi_close again"
        );
        assert!(matches!(again, RdmaError::Fabric { .. }), "{again:?}");

        drop(loan);
        fabric.wait_for_closes();
        assert_eq!(fabric.close_calls(), closes, "nor on drop");
        assert_eq!(
            fabric.leaked_bytes(),
            64,
            "the memory is leaked, never freed"
        );
        assert_eq!(fabric.parked(), 0);
    }

    /// Tcp moves no bytes unless the client polls, so the poller must run for exactly
    /// as long as a loan does.
    #[test]
    fn a_loan_drives_progress_until_it_ends() {
        let fabric = fabric();
        let buffer = fabric.register(vec![0u8; 64]).unwrap();
        assert_eq!(fabric.transfers_in_flight(), 0);

        let loan = lent_for_get(buffer);
        assert_eq!(fabric.transfers_in_flight(), 1);

        let (buffer, _) = unsafe { loan.reclaim(TransferReply::Missing) }.unwrap();
        assert_eq!(fabric.transfers_in_flight(), 0);

        drop(lent_for_set(buffer));
        fabric.wait_for_closes();
        assert_eq!(fabric.transfers_in_flight(), 0);
    }

    #[tokio::test]
    async fn a_revoker_taken_before_lending_revokes_the_loan() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        let revoker = buffer.revoker();
        let loan = lent_for_get(buffer);
        let waiter = loan.revoked();

        let thread = std::thread::spawn(move || revoker.revoke());

        tokio::time::timeout(std::time::Duration::from_secs(5), waiter)
            .await
            .expect("the waiter wakes once the region is revoked");
        thread.join().unwrap().expect("the region closes");
        assert!(loan.is_revoked());
    }

    #[test]
    fn revoking_twice_and_then_dropping_closes_the_region_once() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        buffer.revoke().unwrap();
        buffer.revoke().expect("revoking again is a no-op");
        buffer
            .revoker()
            .revoke()
            .expect("so is revoking through a handle");
        drop(buffer);
    }

    #[tokio::test]
    async fn waiting_on_an_already_revoked_buffer_returns_at_once() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        buffer.revoke().unwrap();

        tokio::time::timeout(std::time::Duration::from_secs(5), buffer.revoked())
            .await
            .expect("already revoked, so there is nothing to wait for");
    }

    /// Dropping the buffer closes the region too, so a waiter must not hang on a
    /// revoke that can now never happen.
    #[tokio::test]
    async fn dropping_the_buffer_wakes_a_waiter() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        let waiter = buffer.revoked();

        drop(buffer);

        tokio::time::timeout(std::time::Duration::from_secs(5), waiter)
            .await
            .expect("the drop closed the region");
    }

    /// A handle must never keep memory registered after its buffer is freed.
    #[test]
    fn a_revoker_does_not_outlive_its_buffer() {
        let fabric = fabric();
        let buffer = fabric.register(vec![0u8; 64]).unwrap();
        let revoker = buffer.revoker();
        assert!(!revoker.is_released());

        drop(buffer);
        fabric.wait_for_closes();

        assert!(revoker.is_released());
        revoker
            .revoke()
            .expect("nothing is left to revoke, which is not an error");
    }

    #[test]
    fn a_revoker_sees_a_revoke_made_through_the_buffer() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        let revoker = buffer.revoker();

        buffer.revoke().unwrap();

        assert!(revoker.is_released());
    }

    /// Memory whose bytes live inside the registered value itself, rather than in a
    /// separate allocation the way a `Vec`'s do. Reading and writing it must go
    /// through the pointer taken at registration, before and after a loan.
    #[test]
    fn memory_held_inline_is_readable_and_writable() {
        let mut buffer = fabric().register([0u8; 64]).unwrap();
        assert_eq!(buffer.copy_from(b"inline"), Some(6));

        let (buffer, _) = unsafe { lent_for_get(buffer).reclaim(receipt(6)) }.unwrap();

        assert_eq!(&buffer.as_host()[..6], b"inline");
    }

    #[test]
    fn staging_more_than_fits_writes_nothing() {
        let mut buffer = fabric().register(vec![0u8; 4]).unwrap();
        assert_eq!(buffer.copy_from(b"too long"), None);
        assert_eq!(buffer.as_host(), &[0u8; 4]);
    }

    /// Memory that records when it is freed and how often it was asked for its bytes.
    struct Tracked {
        bytes: Vec<u8>,
        freed: Arc<AtomicBool>,
        lookups: Arc<AtomicUsize>,
    }

    impl AsMut<[u8]> for Tracked {
        fn as_mut(&mut self) -> &mut [u8] {
            self.lookups.fetch_add(1, Ordering::SeqCst);
            &mut self.bytes
        }
    }

    impl Drop for Tracked {
        fn drop(&mut self) {
            self.freed.store(true, Ordering::SeqCst);
        }
    }

    fn tracked() -> (Tracked, Arc<AtomicBool>, Arc<AtomicUsize>) {
        let freed = Arc::new(AtomicBool::new(false));
        let lookups = Arc::new(AtomicUsize::new(0));
        let memory = Tracked {
            bytes: vec![0u8; 64],
            freed: freed.clone(),
            lookups: lookups.clone(),
        };
        (memory, freed, lookups)
    }

    /// Asking again could return different memory from what the fabric was told
    /// about, so the answer from registration is the only one ever used.
    #[test]
    fn the_memory_is_asked_for_its_bytes_only_once() {
        let (memory, _, lookups) = tracked();
        let mut buffer = fabric().register(memory).unwrap();

        buffer.copy_from(b"hello").unwrap();
        let (buffer, _) = unsafe { lent_for_get(buffer).reclaim(receipt(5)) }.unwrap();
        assert_eq!(&buffer.as_host()[..5], b"hello");

        assert_eq!(lookups.load(Ordering::SeqCst), 1);
    }

    #[test]
    fn dropping_a_buffer_frees_its_memory() {
        let fabric = fabric();
        let (memory, freed, _) = tracked();
        let buffer = fabric.register(memory).unwrap();

        drop(buffer);
        fabric.wait_for_closes();

        assert!(freed.load(Ordering::SeqCst));
    }

    #[test]
    fn a_failed_close_on_drop_leaks_the_memory() {
        let fabric = fabric();
        let (memory, freed, _) = tracked();
        let size = memory.bytes.len();
        let buffer = fabric.register(memory).unwrap();

        fabric.fail_next_closes(1);
        drop(buffer);
        fabric.wait_for_closes();

        assert!(!freed.load(Ordering::SeqCst), "the memory must be leaked");
        assert_eq!(fabric.leaked_bytes(), size, "and counted");
    }

    #[test]
    fn nothing_is_leaked_while_every_close_succeeds() {
        let fabric = fabric();
        drop(lent_for_get(fabric.register(vec![0u8; 64]).unwrap()));
        drop(fabric.register(vec![0u8; 64]).unwrap());
        fabric.wait_for_closes();

        assert_eq!(fabric.leaked_bytes(), 0);
    }

    #[tokio::test]
    async fn a_region_that_fails_to_close_on_drop_is_never_closed_again() {
        let fabric = fabric();
        let alone = fabric.holders();
        let buffer = fabric.register(vec![0u8; 64]).unwrap();
        let revoker = buffer.revoker();
        let mut revoked = tokio::spawn(buffer.revoked());

        fabric.fail_next_closes(1);
        drop(buffer);
        fabric.wait_for_closes();
        let closes = fabric.close_calls();

        assert!(
            !revoker.is_released(),
            "the server may still reach the memory"
        );
        tokio::time::timeout(Duration::from_millis(50), &mut revoked)
            .await
            .expect_err("the region may still be open, so a waiter keeps waiting");

        let error = revoker.revoke().expect_err("a failed close is final");
        assert!(matches!(error, RdmaError::Fabric { .. }), "{error:?}");
        assert_eq!(
            fabric.close_calls(),
            closes,
            "the handle was not passed to fi_close again"
        );
        assert!(!revoker.is_released());
        assert!(
            fabric.holders() > alone,
            "the region holds the fabric open, since the domain cannot close over it"
        );
    }

    /// The caller stopped waiting for the reply, so the server may still be using the
    /// memory: it is freed only with the fabric, which here closes with the loan.
    /// The loan held the last handle, so the fabric closes on its own closing
    /// thread, which must not wait for itself.
    #[test]
    fn dropping_a_loan_revokes_it_and_the_fabric_frees_the_memory_on_close() {
        let (memory, freed, _) = tracked();
        let loan = lent_for_get(fabric().register(memory).unwrap());
        let revoker = loan.revoker();

        drop(loan);

        eventually(
            || freed.load(Ordering::SeqCst),
            "the fabric closed and freed it",
        );
        assert!(revoker.is_released());
    }

    /// Wait up to five seconds for `condition`, for effects that land on another
    /// thread with no handle left to wait on.
    fn eventually(condition: impl Fn() -> bool, what: &str) {
        let deadline = Instant::now() + Duration::from_secs(5);
        while !condition() {
            assert!(Instant::now() < deadline, "timed out waiting: {what}");
            std::thread::sleep(Duration::from_millis(1));
        }
    }

    /// Run `drop_it` on a thread of its own and report whether it returned within
    /// five seconds, so a drop that blocks fails the test instead of hanging it.
    fn returns_promptly(drop_it: impl FnOnce() + Send + 'static) -> bool {
        let (done, finished) = mpsc::channel();
        std::thread::spawn(move || {
            drop_it();
            let _ = done.send(());
        });
        finished.recv_timeout(Duration::from_secs(5)).is_ok()
    }

    /// The point of the closing thread: a dropped loan returns while its close is
    /// still under way, and the region counts as closed only once it is.
    #[test]
    fn dropping_a_loan_does_not_wait_for_its_close() {
        let fabric = fabric();
        let (memory, freed, _) = tracked();
        let loan = lent_for_get(fabric.register(memory).unwrap());
        let revoker = loan.revoker();

        let held = fabric.hold_closes();
        assert!(
            returns_promptly(move || drop(loan)),
            "the drop waited for fi_close"
        );
        assert!(!revoker.is_released(), "the close has not finished yet");

        drop(held);
        fabric.wait_for_closes();
        assert!(revoker.is_released());
        assert!(!freed.load(Ordering::SeqCst), "parked, as on tcp");
        assert_eq!(fabric.parked(), 1);
    }

    #[test]
    fn dropping_a_buffer_does_not_wait_for_its_close() {
        let fabric = fabric();
        let (memory, freed, _) = tracked();
        let buffer = fabric.register(memory).unwrap();
        let revoker = buffer.revoker();

        let held = fabric.hold_closes();
        assert!(
            returns_promptly(move || drop(buffer)),
            "the drop waited for fi_close"
        );
        assert!(!revoker.is_released());
        assert!(
            !freed.load(Ordering::SeqCst),
            "the memory outlives the region over it"
        );

        drop(held);
        fabric.wait_for_closes();
        assert!(revoker.is_released());
        assert!(freed.load(Ordering::SeqCst));
    }

    /// The review's case: a transfer that times out on a single-threaded runtime
    /// drops its loan on the runtime's only thread, which must go on running other
    /// tasks while the region closes.
    #[test]
    fn a_timed_out_transfer_does_not_stall_its_runtime() {
        let fabric = fabric();
        let loan = lent_for_get(fabric.register(vec![0u8; 64]).unwrap());

        let held = fabric.hold_closes();
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_time()
            .build()
            .unwrap();
        let ran = returns_promptly(move || {
            runtime.block_on(async move {
                let awaiting_reply = async move {
                    let _loan = loan;
                    std::future::pending::<()>().await
                };
                tokio::time::timeout(Duration::from_millis(10), awaiting_reply)
                    .await
                    .expect_err("no reply ever comes");
                // Another task on the same thread still gets to run.
                tokio::time::sleep(Duration::from_millis(1)).await;
            })
        });
        assert!(ran, "the runtime stalled on the cancelled transfer's close");

        drop(held);
        fabric.wait_for_closes();
        assert_eq!(fabric.parked(), 1);
    }

    /// Registering waits for regions still closing, so memory dropped just before
    /// is no longer pinned against `RLIMIT_MEMLOCK` when the next one is.
    #[test]
    fn registering_waits_for_regions_still_closing() {
        let fabric = fabric();
        let buffer = fabric.register(vec![0u8; 64]).unwrap();
        let revoker = buffer.revoker();

        let held = fabric.hold_closes();
        drop(buffer);
        let (registered, done) = mpsc::channel();
        let registering = std::thread::spawn({
            let fabric = fabric.clone();
            move || {
                let buffer = fabric.register(vec![0u8; 64]);
                registered.send(()).unwrap();
                buffer
            }
        });
        assert!(
            done.recv_timeout(Duration::from_millis(100)).is_err(),
            "registered while a region was still closing"
        );

        drop(held);
        done.recv_timeout(Duration::from_secs(5))
            .expect("registers once the close finishes");
        assert!(revoker.is_released());
        registering.join().unwrap().expect("registers");
    }

    #[test]
    fn registering_nothing_frees_what_was_handed_over() {
        let (mut memory, freed, _) = tracked();
        memory.bytes.clear();

        let error = fabric().register(memory).unwrap_err();

        assert!(matches!(error, RdmaError::Configuration(_)), "{error:?}");
        assert!(freed.load(Ordering::SeqCst));
    }

    /// Memory that counts, in a counter shared by many buffers, when it is freed.
    struct Counted {
        bytes: Vec<u8>,
        freed: Arc<AtomicUsize>,
    }

    impl AsMut<[u8]> for Counted {
        fn as_mut(&mut self) -> &mut [u8] {
            &mut self.bytes
        }
    }

    impl Drop for Counted {
        fn drop(&mut self) {
            self.freed.fetch_add(1, Ordering::SeqCst);
        }
    }

    /// Register, lend, end the loan each possible way, drop, and register again,
    /// many times over on several fabrics at once, half of them treating a close
    /// as efa-direct's. Afterwards every region is closed, nothing is leaked, each
    /// buffer's memory is freed or parked as its ending requires, nothing still
    /// holds the fabric open, and closing the fabric frees whatever was parked.
    #[test]
    fn registering_and_dropping_over_and_over_frees_everything() {
        const FABRICS: usize = 4;
        const CYCLES: usize = 250;
        let threads: Vec<_> = (0..FABRICS)
            .map(|seed| {
                std::thread::spawn(move || {
                    let close_stops_writes = seed % 2 == 1;
                    let fabric = if close_stops_writes {
                        fabric_whose_close_stops_writes()
                    } else {
                        fabric()
                    };
                    let alone = fabric.holders();
                    let freed = Arc::new(AtomicUsize::new(0));
                    let mut unanswered = 0;
                    let mut revokers = Vec::with_capacity(CYCLES);

                    for cycle in 0..CYCLES {
                        let memory = Counted {
                            bytes: vec![0u8; 4096 * (1 + cycle % 4)],
                            freed: freed.clone(),
                        };
                        let buffer = fabric.register(memory).expect("registers");
                        revokers.push(buffer.revoker());
                        match (cycle + seed) % 5 {
                            0 => drop(buffer),
                            1 => {
                                let (buffer, _) =
                                    unsafe { lent_for_get(buffer).reclaim(TransferReply::Missing) }
                                        .expect("reclaims");
                                drop(buffer);
                            }
                            2 => drop(unsafe { lent_for_set(buffer).cancel_unsent() }),
                            3 => {
                                lent_for_get(buffer).recall().expect("the region closes");
                                unanswered += 1;
                            }
                            _ => {
                                drop(lent_for_set(buffer));
                                unanswered += 1;
                            }
                        }
                    }

                    fabric.wait_for_closes();
                    assert!(
                        revokers.iter().all(RdmaRevoker::is_released),
                        "every region closed"
                    );
                    assert_eq!(fabric.leaked_bytes(), 0);
                    assert_eq!(fabric.transfers_in_flight(), 0);
                    let parked = if close_stops_writes { 0 } else { unanswered };
                    assert_eq!(fabric.parked(), parked);
                    assert_eq!(freed.load(Ordering::SeqCst), CYCLES - parked);
                    assert_eq!(fabric.holders(), alone, "nothing holds the fabric open");

                    drop(fabric);
                    assert_eq!(
                        freed.load(Ordering::SeqCst),
                        CYCLES,
                        "closing the fabric frees what was parked"
                    );
                })
            })
            .collect();
        for thread in threads {
            thread.join().expect("a fabric's cycles passed");
        }
    }
}
