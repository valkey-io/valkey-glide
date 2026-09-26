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
//! [`LentBuffer::recall`] when no reply is coming.

use std::fmt;
use std::mem::ManuallyDrop;
use std::ptr::NonNull;

use crate::command::{self, RdmaCommand, ReadReceipt, TransferReply};
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

    /// Where the bytes start in this process's address space.
    pub(crate) fn address(&self) -> u64 {
        self.bytes.cast::<u8>().as_ptr().addr() as u64
    }

    pub(crate) fn bytes(&self) -> &[u8] {
        // SAFETY: `bytes` stays valid until `self` drops. The only `&mut` to it comes
        // from `bytes_mut`, which needs `&mut self`, so the two never overlap.
        unsafe { self.bytes.as_ref() }
    }

    fn bytes_mut(&mut self) -> &mut [u8] {
        // SAFETY: as in `bytes`, and `&mut self` makes this the only reference.
        unsafe { self.bytes.as_mut() }
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
    /// Declared first so the region deregisters before the domain that owns it closes.
    registration: Registration,
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
            registration,
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

    /// Lend `[at, at + length)` of this buffer to a `LO.GET` of `key` for the server
    /// to write the stored object into.
    ///
    /// Returns the command to send and the loan to hold until the server replies.
    ///
    /// # Errors
    ///
    /// Hands the buffer back, unlent, when the window runs past the end of the buffer
    /// or the buffer has been revoked.
    pub fn lend_for_get(
        self,
        key: &[u8],
        at: usize,
        length: usize,
    ) -> Result<(RdmaCommand, LentBuffer), (Self, RdmaError)> {
        self.lend(at, length, |window| command::get(key, window))
    }

    /// Lend `[at, at + length)` of this buffer to a `LO.SET` of `key` for the server
    /// to read those bytes and store them.
    ///
    /// Returns the command to send and the loan to hold until the server replies.
    ///
    /// # Errors
    ///
    /// Hands the buffer back, unlent, when the window runs past the end of the buffer
    /// or the buffer has been revoked.
    pub fn lend_for_set(
        self,
        key: &[u8],
        at: usize,
        length: usize,
    ) -> Result<(RdmaCommand, LentBuffer), (Self, RdmaError)> {
        self.lend(at, length, |window| command::set(key, length, window))
    }

    fn lend(
        self,
        at: usize,
        length: usize,
        build: impl FnOnce(&RegionRef) -> RdmaCommand,
    ) -> Result<(RdmaCommand, LentBuffer), (Self, RdmaError)> {
        if self.is_revoked() {
            return Err((self, RdmaError::Revoked));
        }
        let Some(window) = self.window(at, length) else {
            let error = RdmaError::Configuration(format!(
                "window [{at}, {at}+{length}) runs past the end of a registered region \
                 of {} bytes",
                self.capacity()
            ));
            return Err((self, error));
        };
        let command = build(&window);
        let progress = self.fabric.drive_progress();
        let loan = LentBuffer {
            buffer: self,
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
    /// When libfabric fails to close the region, the buffer is still registered.
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
    /// buffer is dropped, it resolves once the region closes, which may be never if
    /// closing it keeps failing.
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

impl Drop for RdmaBuffer {
    /// Free the memory only after the region over it is closed. If the region cannot
    /// be closed, the server may still reach the memory, so it is leaked instead.
    fn drop(&mut self) {
        if self.registration.revoke().is_ok() {
            // SAFETY: the region is closed, and `host` is not touched again after this.
            unsafe { ManuallyDrop::drop(&mut self.host) };
        }
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
/// There are three ways for a loan to end:
///
/// - [`Self::reclaim`] once the server has replied. The buffer can then be read and
///   lent again.
/// - [`Self::recall`] when no reply is coming, for example because the connection
///   failed or the caller gave up. This revokes the buffer first, so the server can
///   no longer reach the memory.
/// - Dropping the loan, for example when the future awaiting the reply is
///   cancelled. This also revokes then frees the memory. If the revoke fails, the
///   memory is leaked rather than freed while the server might still reach it.
///
/// On tcp, the fabric's progress thread keeps polling for as long as a loan exists,
/// because that provider moves no bytes unless the client polls.
#[derive(Debug)]
pub struct LentBuffer {
    /// Never exposed. Declared first so that when a loan is dropped, the region is
    /// revoked before progress polling stops.
    buffer: RdmaBuffer,
    window_length: usize,
    _progress: Option<ProgressGuard>,
}

// As for `RdmaBuffer`: the buffer rides in the `Err` so it is never lost.
#[allow(clippy::result_large_err)]
impl LentBuffer {
    /// End the loan with the server's reply to the transfer command.
    ///
    /// # Errors
    ///
    /// [`RdmaError::PayloadTooLarge`] when the server reports writing more than the
    /// window held. The bytes past the window may have overwritten other parts of the
    /// buffer. The buffer is still handed back, because the server has replied and so
    /// is done with the memory.
    pub fn reclaim(
        self,
        reply: TransferReply,
    ) -> Result<(RdmaBuffer, Option<ReadReceipt>), (RdmaBuffer, RdmaError)> {
        let Self {
            buffer,
            window_length,
            _progress,
        } = self;
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

    /// End the loan without a reply: revoke the buffer, then hand it back.
    ///
    /// The returned buffer is revoked, so it cannot be lent again. What the server
    /// moved before the revoke stays, so after a cancelled `LO.GET` the window holds
    /// an unknown mix of old and new bytes.
    ///
    /// # Errors
    ///
    /// When libfabric fails to close the region, the server may still reach the
    /// memory, so the loan is handed back unchanged. Retry, or drop it to leak the
    /// memory.
    pub fn recall(self) -> Result<RdmaBuffer, (Self, RdmaError)> {
        match self.buffer.revoke() {
            Ok(()) => Ok(self.buffer),
            Err(error) => Err((self, error)),
        }
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
}

/// Revokes a [`RdmaBuffer`] from elsewhere, whether or not it is lent. From
/// [`RdmaBuffer::revoker`] or [`LentBuffer::revoker`].
#[derive(Debug, Clone)]
pub struct RdmaRevoker(RevokeHandle);

impl RdmaRevoker {
    /// Revoke the buffer, as [`RdmaBuffer::revoke`] does. A no-op if it is already
    /// revoked or was dropped and its region closed.
    ///
    /// If the buffer was dropped but its region failed to close, this tries closing
    /// it again.
    ///
    /// # Errors
    ///
    /// When libfabric fails to close the region.
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
    use super::{LentBuffer, RdmaBuffer};
    use crate::command::{RdmaCommand, ReadReceipt, TransferReply};
    use crate::config::{FabricConfig, Provider};
    use crate::error::RdmaError;
    use crate::fabric::RdmaFabric;
    use crate::fabric::tests::fail_next_closes;
    use crate::region_ref::RegionRef;
    use std::sync::Arc;
    use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
    use std::time::Duration;

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
            checksum: None,
        })
    }

    fn lent_for_get(buffer: RdmaBuffer) -> LentBuffer {
        buffer.lend_for_get(b"key", 0, 64).expect("lends").1
    }

    fn lent_for_set(buffer: RdmaBuffer) -> LentBuffer {
        buffer.lend_for_set(b"key", 0, 64).expect("lends").1
    }

    #[test]
    fn lending_for_get_names_the_window() {
        let buffer = fabric().register(vec![0u8; 4096]).unwrap();
        let region_ref = buffer.region_ref().clone();

        let (command, _loan) = buffer.lend_for_get(b"key", 1024, 256).expect("lends");

        assert_eq!(command.name(), "LO.GET");
        assert_eq!(
            arguments(&command),
            [
                "key".to_string(),
                region_ref.remote_key.to_string(),
                (region_ref.remote_address + 1024).to_string(),
            ]
        );
    }

    #[test]
    fn lending_for_set_names_the_window_and_its_length() {
        let buffer = fabric().register(vec![0u8; 4096]).unwrap();
        let region_ref = buffer.region_ref().clone();

        let (command, _loan) = buffer.lend_for_set(b"key", 1024, 256).expect("lends");

        assert_eq!(command.name(), "LO.SET");
        assert_eq!(
            arguments(&command),
            [
                "key".to_string(),
                "256".to_string(),
                region_ref.remote_key.to_string(),
                (region_ref.remote_address + 1024).to_string(),
            ]
        );
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
        let (mut buffer, error) = buffer.lend_for_set(b"key", 0, 1025).unwrap_err();
        assert!(matches!(error, RdmaError::Configuration(_)), "{error:?}");

        // Still usable, right up to the last byte.
        for (at, length) in [(0, 1024), (1024, 0)] {
            let (_, loan) = buffer
                .lend_for_get(b"key", at, length)
                .unwrap_or_else(|(_, error)| panic!("[{at}, +{length}) fits: {error}"));
            buffer = loan.reclaim(TransferReply::Missing).unwrap().0;
        }
    }

    #[test]
    fn a_revoked_buffer_is_not_lent() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        buffer.revoke().unwrap();

        let (buffer, error) = buffer.lend_for_get(b"key", 0, 64).unwrap_err();
        assert_eq!(error, RdmaError::Revoked);
        let (_, error) = buffer.lend_for_set(b"key", 0, 64).unwrap_err();
        assert_eq!(error, RdmaError::Revoked);
    }

    #[test]
    fn a_read_reply_returns_the_buffer_and_its_receipt() {
        let mut buffer = fabric().register(vec![0u8; 64]).unwrap();
        buffer.copy_from(b"landed").unwrap();

        let (buffer, reported) = lent_for_get(buffer).reclaim(receipt(6)).expect("reclaims");

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
        let (buffer, _) = moved_loan.reclaim(receipt(1)).unwrap();

        assert_eq!(buffer.as_host().as_ptr(), registered_at);
        assert!(size_of::<RdmaBuffer>() < 256, "the handle stays small");
        assert!(size_of::<LentBuffer>() < 256, "the loan stays small");
    }

    #[test]
    fn a_receipt_that_fills_the_window_exactly_is_accepted() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        assert!(lent_for_get(buffer).reclaim(receipt(64)).is_ok());
    }

    #[test]
    fn a_receipt_larger_than_the_window_is_an_error_but_returns_the_buffer() {
        let buffer = fabric().register(vec![0u8; 128]).unwrap();
        let (_, loan) = buffer.lend_for_get(b"key", 0, 64).unwrap();

        let (buffer, error) = loan.reclaim(receipt(65)).unwrap_err();

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

        let (_, reported) = lent_for_get(register())
            .reclaim(TransferReply::Missing)
            .expect("a missing key ends a LO.GET");
        assert_eq!(reported, None);

        let (_, reported) = lent_for_set(register())
            .reclaim(TransferReply::Stored)
            .expect("a store ends a LO.SET");
        assert_eq!(reported, None);

        for loan in [lent_for_get(register()), lent_for_set(register())] {
            let (_, reported) = loan
                .reclaim(TransferReply::Failed)
                .expect("an error reply ends either");
            assert_eq!(reported, None);
        }
    }

    #[test]
    fn recalling_a_loan_revokes_it_and_keeps_the_memory() {
        let buffer = fabric().register(vec![7u8; 64]).unwrap();

        let buffer = lent_for_get(buffer).recall().expect("the region closes");

        assert!(buffer.is_revoked());
        assert_eq!(
            buffer.as_host(),
            &[7u8; 64],
            "only the fabric's access ends; the memory is still the caller's"
        );
        let (_, error) = buffer.lend_for_get(b"key", 0, 64).unwrap_err();
        assert_eq!(error, RdmaError::Revoked);
    }

    #[test]
    fn a_failed_recall_hands_back_the_loan() {
        let loan = lent_for_get(fabric().register(vec![0u8; 64]).unwrap());

        fail_next_closes(1);
        let (loan, error) = loan.recall().unwrap_err();

        assert!(matches!(error, RdmaError::Fabric { .. }), "{error:?}");
        assert!(!loan.is_revoked(), "the server may still reach the memory");
        loan.recall().expect("a retry closes the region");
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

        let (buffer, _) = loan.reclaim(TransferReply::Missing).unwrap();
        assert_eq!(fabric.transfers_in_flight(), 0);

        drop(lent_for_set(buffer));
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
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        let revoker = buffer.revoker();
        assert!(!revoker.is_released());

        drop(buffer);

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

        let (buffer, _) = lent_for_get(buffer).reclaim(receipt(6)).unwrap();

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
        let (buffer, _) = lent_for_get(buffer).reclaim(receipt(5)).unwrap();
        assert_eq!(&buffer.as_host()[..5], b"hello");

        assert_eq!(lookups.load(Ordering::SeqCst), 1);
    }

    #[test]
    fn dropping_a_buffer_frees_its_memory() {
        let (memory, freed, _) = tracked();
        let buffer = fabric().register(memory).unwrap();

        drop(buffer);

        assert!(freed.load(Ordering::SeqCst));
    }

    #[test]
    fn a_failed_close_on_drop_leaks_the_memory() {
        let (memory, freed, _) = tracked();
        let buffer = fabric().register(memory).unwrap();

        // One failure, so the registration's own drop still closes the region and
        // the test leaves nothing registered behind.
        fail_next_closes(1);
        drop(buffer);

        assert!(!freed.load(Ordering::SeqCst), "the memory must be leaked");
    }

    #[tokio::test]
    async fn a_region_that_fails_to_close_on_drop_stays_open_until_a_retry() {
        let fabric = fabric();
        let alone = fabric.holders();
        let buffer = fabric.register(vec![0u8; 64]).unwrap();
        let revoker = buffer.revoker();
        let mut revoked = tokio::spawn(buffer.revoked());

        // Both the buffer's close and the registration's retry on drop fail.
        fail_next_closes(2);
        drop(buffer);

        assert!(
            !revoker.is_released(),
            "the server may still reach the memory"
        );
        tokio::time::timeout(Duration::from_millis(50), &mut revoked)
            .await
            .expect_err("the region is still open, so a waiter keeps waiting");

        revoker.revoke().expect("a retry closes the region");
        assert!(revoker.is_released());
        tokio::time::timeout(Duration::from_secs(5), revoked)
            .await
            .expect("the waiter wakes once the region closes")
            .unwrap();
        assert_eq!(
            fabric.holders(),
            alone,
            "the closed region no longer holds the fabric open"
        );
    }

    /// The caller stopped waiting for the reply, so the server may still be using the
    /// memory: it must be revoked before it is freed.
    #[test]
    fn dropping_a_loan_revokes_and_then_frees() {
        let (memory, freed, _) = tracked();
        let loan = lent_for_get(fabric().register(memory).unwrap());
        let revoker = loan.revoker();

        drop(loan);

        assert!(revoker.is_released());
        assert!(freed.load(Ordering::SeqCst));
    }

    #[test]
    fn registering_nothing_frees_what_was_handed_over() {
        let (mut memory, freed, _) = tracked();
        memory.bytes.clear();

        let error = fabric().register(memory).unwrap_err();

        assert!(matches!(error, RdmaError::Configuration(_)), "{error:?}");
        assert!(freed.load(Ordering::SeqCst));
    }
}
