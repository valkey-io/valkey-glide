// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// ═══════════════════════════════════════════════════════════════════════════════
// RDMA FFI
//
// Registers caller-owned memory with the client's fabric and runs RDMA get/set
// transfers against it. The RESP channel coordinates the transfers with the server,
// but the server reads/writes the registered region directly.
// ═══════════════════════════════════════════════════════════════════════════════

use super::*;
#[cfg(feature = "rdma")]
use std::sync::atomic::{AtomicBool, Ordering};

/// Whether this build has RDMA compiled in via the `rdma` feature.
#[unsafe(no_mangle)]
pub extern "C" fn rdma_available() -> bool {
    cfg!(feature = "rdma")
}

/// Whether this build has RDMA compiled in and can load a compatible libfabric
/// on this machine.
///
/// It does not look for fabric hardware, so creating a client can still fail,
/// for example with efa-direct on a machine without an EFA device.
#[unsafe(no_mangle)]
pub extern "C" fn rdma_usable() -> bool {
    #[cfg(feature = "rdma")]
    {
        glide_rdma::ensure_loaded().is_ok()
    }
    #[cfg(not(feature = "rdma"))]
    {
        false
    }
}

/// A registered region of caller-owned memory.
///
/// Not thread-safe: a transfer takes the region exclusively, so two threads must
/// not use one region concurrently. Freed with [`free_rdma_region`].
#[cfg(feature = "rdma")]
pub struct RdmaRegion {
    /// Taken out for the length of each transfer, which lends it to the server,
    /// and put back when the transfer hands it back. Stays empty once a transfer
    /// ends without the server's reply: the fabric then decides when the memory
    /// is safe to let go, and [`Self::released`] says when it has.
    buffer: Option<glide_rdma::RdmaBuffer>,
    capacity: usize,
    /// Set once the fabric has let go of the caller's memory; see [`ForeignRegion`].
    released: Arc<AtomicBool>,
    /// The fabric the buffer was registered with, kept even once the buffer is
    /// gone, so [`free_rdma_region`] can wait for the region to finish closing.
    fabric: glide_rdma::RdmaFabric,
}

/// What [`free_rdma_region`] hands back when the fabric still holds the caller's
/// memory, because a write may yet land in it or its region failed to close.
///
/// The caller must keep the memory alive until [`rdma_memory_released`] says so,
/// then free this with [`free_rdma_memory_hold`].
#[cfg(feature = "rdma")]
pub struct RdmaMemoryHold {
    released: Arc<AtomicBool>,
}

#[cfg(feature = "rdma")]
impl RdmaRegion {
    /// A region for a freshly registered `buffer` over the caller's memory, whose
    /// release `released` reports.
    fn new(buffer: glide_rdma::RdmaBuffer, released: Arc<AtomicBool>) -> Self {
        Self {
            capacity: buffer.capacity(),
            fabric: buffer.fabric().clone(),
            buffer: Some(buffer),
            released,
        }
    }

    /// Take the buffer out for a transfer, or say why there is none.
    fn take(&mut self) -> Result<glide_rdma::RdmaBuffer, RdmaResult> {
        self.buffer.take().ok_or_else(|| RdmaResult {
            region_closed: true,
            ..RdmaResult::rejected(
                "the region was closed when a transfer ended without the server's reply; \
                 free it and register the memory again"
                    .to_string(),
            )
        })
    }

    /// Put back what a transfer handed back, and mark `result` with whether the
    /// region can still transfer.
    fn finish(&mut self, buffer: Option<glide_rdma::RdmaBuffer>, result: RdmaResult) -> RdmaResult {
        self.buffer = buffer;
        RdmaResult {
            region_closed: self.is_closed(),
            ..result
        }
    }

    /// Whether no transfer can use this region again: a transfer ended without the
    /// server's reply, so no buffer came back, or the region was revoked, for
    /// example by [`close_client`].
    fn is_closed(&self) -> bool {
        self.buffer
            .as_ref()
            .is_none_or(glide_rdma::RdmaBuffer::is_revoked)
    }
}

/// Placeholder type for a registered region of caller-owned memory
/// when the `rdma` feature is not enabled.
#[cfg(not(feature = "rdma"))]
pub enum RdmaRegion {}

#[cfg(not(feature = "rdma"))]
pub enum RdmaMemoryHold {}

/// The outcome of [`register_rdma_region`].
///
/// Exactly one field is set: `region` on success, `error_message` on failure.
/// Free it with [`free_rdma_registration`], which also frees the message. The
/// region outlives the registration and is freed separately.
#[repr(C)]
pub struct RdmaRegistration {
    /// Null on success, otherwise an owned C string describing the failure.
    pub error_message: *const c_char,
    /// Null on failure, otherwise the registered region.
    pub region: *mut RdmaRegion,
}

/// Whether [`rdma_set`] asks the server to check what it read against a CRC-32c
/// before storing it. With a checksum, the server refuses to store bytes whose
/// CRC-32c differs.
#[repr(C)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RdmaSetChecksumMode {
    /// Send no checksum. The server stores whatever it read.
    Skip = 0,
    /// Compute the CRC-32c of the window before sending, which reads every byte
    /// of the window once.
    Compute = 1,
    /// Send the `checksum` argument, a CRC-32c the caller already knows for the
    /// window's bytes.
    Known = 2,
}

#[cfg(feature = "rdma")]
impl RdmaSetChecksumMode {
    /// The choice to hand glide-core, with `checksum` used only for `Known`.
    fn with(self, checksum: u32) -> glide_rdma::SetChecksum {
        match self {
            Self::Skip => glide_rdma::SetChecksum::Skip,
            Self::Compute => glide_rdma::SetChecksum::Compute,
            Self::Known => glide_rdma::SetChecksum::Known(checksum),
        }
    }
}

/// The outcome of an RDMA transfer.
///
/// On success `error_message` is null. Free it with [`free_rdma_result`].
#[repr(C)]
pub struct RdmaResult {
    /// Null on success, otherwise an owned C string describing the failure.
    pub error_message: *const c_char,
    /// How to classify the failure. Meaningless when `error_message` is null.
    pub error_type: RequestErrorType,
    /// Read only: whether the key existed. A read of a missing key succeeds
    /// with this false and nothing written.
    pub found: bool,
    /// Read only: bytes the server transferred into the region.
    pub bytes_written: usize,
    /// Read only: whether `checksum` carries a value.
    pub has_checksum: bool,
    /// Read only: the CRC-32c the server reported over the transferred bytes.
    pub checksum: u32,
    /// Whether the region can no longer be used for a transfer, because a transfer
    /// ended without the server's reply or the region was revoked. Free it with
    /// [`free_rdma_region`] and register the memory again. Any error may come with
    /// this set or not, so check this rather than the error type.
    pub region_closed: bool,
}

impl RdmaResult {
    #[cfg(feature = "rdma")]
    fn missing() -> Self {
        Self {
            error_message: std::ptr::null(),
            error_type: RequestErrorType::Unspecified,
            found: false,
            bytes_written: 0,
            has_checksum: false,
            checksum: 0,
            region_closed: false,
        }
    }

    #[cfg(feature = "rdma")]
    fn stored() -> Self {
        Self {
            error_message: std::ptr::null(),
            error_type: RequestErrorType::Unspecified,
            found: true,
            bytes_written: 0,
            has_checksum: false,
            checksum: 0,
            region_closed: false,
        }
    }

    #[cfg(feature = "rdma")]
    fn transferred(receipt: &glide_rdma::ReadReceipt) -> Self {
        Self {
            error_message: std::ptr::null(),
            error_type: RequestErrorType::Unspecified,
            found: true,
            bytes_written: receipt.bytes_written,
            // A read reply always carries a checksum, so the flag is always set here.
            // It stays in the struct because the Python client reads this C layout.
            has_checksum: true,
            checksum: receipt.checksum,
            region_closed: false,
        }
    }

    fn failed(message: String, error_type: RequestErrorType) -> Self {
        Self {
            error_message: into_c_string(message),
            error_type,
            found: false,
            bytes_written: 0,
            has_checksum: false,
            checksum: 0,
            region_closed: false,
        }
    }

    /// A caller mistake rather than a transfer failure, e.g. a null pointer,
    /// a zero length, a client that cannot serve RDMA.
    fn rejected(message: String) -> Self {
        Self::failed(message, RequestErrorType::Unspecified)
    }

    fn into_raw(self) -> *mut RdmaResult {
        Box::into_raw(Box::new(self))
    }
}

/// Move an error message across the boundary as an owned C string.
fn into_c_string(message: String) -> *const c_char {
    CString::new(message)
        .unwrap_or_else(|_| {
            CString::new("RDMA error message contained a NUL byte")
                .expect("the replacement literal has no NUL")
        })
        .into_raw()
}

/// The error a build without RDMA support answers every call with.
#[cfg(not(feature = "rdma"))]
fn not_compiled_in() -> String {
    glide_core::rdma::RdmaUnavailable::NotCompiledIn.to_string()
}

/// Reconstruct the adapter without taking ownership of the caller's reference.
///
/// # Safety
/// `client_adapter_ptr` must be a non-null pointer from [`create_client`] that
/// [`close_client`] has not been called on.
#[cfg(feature = "rdma")]
unsafe fn borrow_adapter(client_adapter_ptr: *const c_void) -> Arc<ClientAdapter> {
    unsafe {
        // Incremented so dropping this Arc leaves the caller's reference intact.
        Arc::increment_strong_count(client_adapter_ptr as *const ClientAdapter);
        Arc::from_raw(client_adapter_ptr as *const ClientAdapter)
    }
}

/// Reject anything but a sync client.
#[cfg(feature = "rdma")]
fn require_sync_client(adapter: &ClientAdapter) -> Result<(), String> {
    match adapter.core.client_type {
        ClientType::SyncClient => Ok(()),
        ClientType::AsyncClient { .. } => Err(
            "RDMA is only available on synchronous clients; this client was created \
             as asynchronous"
                .to_string(),
        ),
    }
}

/// Run a transfer to completion on the calling thread.
#[cfg(feature = "rdma")]
fn block_on_transfer<F: Future>(adapter: &ClientAdapter, future: F) -> F::Output {
    let background = adapter
        .background_runtime
        .as_ref()
        .map(|runtime| runtime.handle().clone());
    adapter.runtime.block_on(async {
        let _guard = background.as_ref().map(|handle| handle.enter());
        future.await
    })
}

/// Caller-owned memory presented to the fabric as a registerable region.
///
/// The fabric drops this when it is done with the memory: at once, after the
/// server's reply or a region close that stops writes in flight; only when the
/// fabric closes, if a write could still land; never, if the region failed to
/// close. Dropping sets `released`, which is how the caller learns it may free the
/// memory.
#[cfg(feature = "rdma")]
struct ForeignRegion {
    memory: *mut u8,
    length: usize,
    released: Arc<AtomicBool>,
}

#[cfg(feature = "rdma")]
impl Drop for ForeignRegion {
    fn drop(&mut self) {
        self.released.store(true, Ordering::Release);
    }
}

// SAFETY: the pointer is only ever dereferenced through `as_mut`, and the
// caller guarantees the allocation stays valid, stays put, and is not touched
// through any other alias while the region lives. Under those terms moving the
// handle between threads is no less safe than holding it on one.
#[cfg(feature = "rdma")]
unsafe impl Send for ForeignRegion {}

#[cfg(feature = "rdma")]
impl AsMut<[u8]> for ForeignRegion {
    fn as_mut(&mut self) -> &mut [u8] {
        // SAFETY: `register_rdma_region` rejected a null pointer and a zero
        // length, and the caller guarantees the rest.
        unsafe { std::slice::from_raw_parts_mut(self.memory, self.length) }
    }
}

/// CRC-32c of `length` bytes at `data`.
///
/// # Returns
///
/// True with the checksum in `out`. False when this build has no RDMA support,
/// or when the arguments do not describe readable bytes, leaving `out` untouched.
///
/// # Safety
///
/// * `data` must point to `length` initialized bytes, or be null when `length`
///   is 0.
/// * `out` must point to a writable `uint32_t`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rdma_checksum(data: *const u8, length: usize, out: *mut u32) -> bool {
    if out.is_null() || (data.is_null() && length != 0) {
        return false;
    }

    #[cfg(not(feature = "rdma"))]
    {
        false
    }

    #[cfg(feature = "rdma")]
    {
        // A null pointer is only allowed alongside a zero length, and
        // `from_raw_parts` still wants a non-null, aligned pointer for that.
        let bytes = if length == 0 {
            &[][..]
        } else {
            unsafe { std::slice::from_raw_parts(data, length) }
        };
        unsafe { *out = glide_rdma::checksum(bytes) };
        true
    }
}

/// Register caller-owned memory so the server can transfer data into or out of it.
///
/// # Returns
///
/// A [`RdmaRegistration`] the caller frees with [`free_rdma_registration`]. On
/// success it carries a region the caller frees with [`free_rdma_region`].
///
/// # Safety
///
/// * `client_adapter_ptr` must be a pointer from [`create_client`] that
///   [`close_client`] has not been called on.
/// * `memory` must point to `length` writable, initialized bytes.
/// * The allocation must stay alive, stay at the same address, and not be
///   accessed through any other alias until [`free_rdma_region`] returns. The
///   server writes into it at times the caller does not control.
/// * The returned pointer must be freed with [`free_rdma_registration`].
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn register_rdma_region(
    client_adapter_ptr: *const c_void,
    memory: *mut u8,
    length: usize,
) -> *mut RdmaRegistration {
    let registration = register_region(client_adapter_ptr, memory, length);
    Box::into_raw(Box::new(registration))
}

fn register_region(
    client_adapter_ptr: *const c_void,
    memory: *mut u8,
    length: usize,
) -> RdmaRegistration {
    let failed = |message: String| RdmaRegistration {
        error_message: into_c_string(message),
        region: std::ptr::null_mut(),
    };

    if client_adapter_ptr.is_null() {
        return failed("cannot register a region without a client".to_string());
    }
    if memory.is_null() {
        return failed("cannot register a null pointer".to_string());
    }
    if length == 0 {
        return failed("cannot register 0 bytes".to_string());
    }

    #[cfg(not(feature = "rdma"))]
    {
        failed(not_compiled_in())
    }

    #[cfg(feature = "rdma")]
    {
        let adapter = unsafe { borrow_adapter(client_adapter_ptr) };
        if let Err(message) = require_sync_client(&adapter) {
            return failed(message);
        }
        let released = Arc::new(AtomicBool::new(false));
        match adapter.core.client.register_rdma_region(ForeignRegion {
            memory,
            length,
            released: released.clone(),
        }) {
            Ok(buffer) => RdmaRegistration {
                error_message: std::ptr::null(),
                region: Box::into_raw(Box::new(RdmaRegion::new(buffer, released))),
            },
            Err(err) => failed(errors::error_message(&err)),
        }
    }
}

/// How many bytes a region covers. 0 if the pointer is null.
///
/// # Safety
///
/// * `region` must be null or a region from [`register_rdma_region`] that
///   [`free_rdma_region`] has not been called on.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rdma_region_capacity(region: *const RdmaRegion) -> usize {
    if region.is_null() {
        return 0;
    }
    #[cfg(not(feature = "rdma"))]
    {
        0
    }
    #[cfg(feature = "rdma")]
    {
        unsafe { (*region).capacity }
    }
}

/// Read a value directly into the `[offset, offset + length)` window of a
/// registered region.
///
/// Has no timeout, waits for the server to complete the transfer so memory
/// is not left in an undefined state.
///
/// To cancel a transfer, call [`close_client`] to revoke registered regions
/// so the server can no longer reach them. Bytes that landed before the close
/// stay, so the window then holds an unknown mix of old and new bytes. The
/// region cannot be used for another transfer; free it with [`free_rdma_region`].
///
/// After an error the server sent, or one raised before the command was sent
/// such as reaching the in-flight request limit, the region can be used again.
/// If the transfer ends any other way, such as a lost connection, the server
/// might still be using the memory, so the region is revoked first and cannot
/// be used again either. [`RdmaResult::region_closed`] says which happened.
///
/// # Returns
///
/// A [`RdmaResult`] the caller frees with [`free_rdma_result`].
///
/// # Safety
///
/// * `client_adapter_ptr` must be a pointer from [`create_client`] that
///   [`close_client`] has not been called on when this call starts.
/// * `key` must point to `key_len` initialized bytes, which must stay valid for
///   the duration of the call.
/// * `region` must be a region from [`register_rdma_region`] that
///   [`free_rdma_region`] has not been called on, and no other thread may use
///   it for the duration of the call.
/// * The returned pointer must be freed with [`free_rdma_result`].
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn rdma_get(
    client_adapter_ptr: *const c_void,
    key: *const u8,
    key_len: usize,
    region: *mut RdmaRegion,
    offset: usize,
    length: usize,
) -> *mut RdmaResult {
    let _ = (key_len, offset, length);
    if let Some(rejection) = reject_bad_transfer_args(client_adapter_ptr, key, region) {
        return rejection.into_raw();
    }

    #[cfg(not(feature = "rdma"))]
    {
        RdmaResult::rejected(not_compiled_in()).into_raw()
    }

    #[cfg(feature = "rdma")]
    {
        let adapter = unsafe { borrow_adapter(client_adapter_ptr) };
        if let Err(message) = require_sync_client(&adapter) {
            return RdmaResult::rejected(message).into_raw();
        }
        let key = unsafe { std::slice::from_raw_parts(key, key_len) };
        let region = unsafe { &mut *region };
        let buffer = match region.take() {
            Ok(buffer) => buffer,
            Err(rejection) => return rejection.into_raw(),
        };

        let mut client = adapter.core.client.clone();
        let (buffer, outcome) =
            block_on_transfer(&adapter, client.rdma_get(key, buffer, offset, length));

        let result = match outcome {
            Ok(Some(receipt)) => RdmaResult::transferred(&receipt),
            Ok(None) => RdmaResult::missing(),
            Err(err) => RdmaResult::failed(errors::error_message(&err), errors::error_type(&err)),
        };
        region.finish(buffer, result).into_raw()
    }
}

/// Store a value the server reads directly out of the
/// `[offset, offset + length)` window of a registered region.
///
/// Like [`rdma_get`], it has no timeout, and calling [`close_client`] from
/// another thread cancels it.
///
/// `checksum_mode` decides whether the server checks the bytes it read before
/// storing them; see [`RdmaSetChecksumMode`]. `checksum` is read only for
/// [`RdmaSetChecksumMode::Known`]. A mismatch fails with the server's error,
/// stores nothing, and leaves the region usable.
///
/// # Returns
///
/// A [`RdmaResult`] the caller frees with [`free_rdma_result`].
///
/// # Safety
///
/// Same contract as [`rdma_get`]. The region is read, not written, but the
/// caller must still hold it exclusively for the duration of the call.
/// `checksum_mode` must be one of the [`RdmaSetChecksumMode`] values.
#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
pub unsafe extern "C-unwind" fn rdma_set(
    client_adapter_ptr: *const c_void,
    key: *const u8,
    key_len: usize,
    region: *mut RdmaRegion,
    offset: usize,
    length: usize,
    checksum_mode: RdmaSetChecksumMode,
    checksum: u32,
) -> *mut RdmaResult {
    let _ = (key_len, offset, length, checksum_mode, checksum);
    if let Some(rejection) = reject_bad_transfer_args(client_adapter_ptr, key, region) {
        return rejection.into_raw();
    }

    #[cfg(not(feature = "rdma"))]
    {
        RdmaResult::rejected(not_compiled_in()).into_raw()
    }

    #[cfg(feature = "rdma")]
    {
        let adapter = unsafe { borrow_adapter(client_adapter_ptr) };
        if let Err(message) = require_sync_client(&adapter) {
            return RdmaResult::rejected(message).into_raw();
        }
        let key = unsafe { std::slice::from_raw_parts(key, key_len) };
        let region = unsafe { &mut *region };
        let buffer = match region.take() {
            Ok(buffer) => buffer,
            Err(rejection) => return rejection.into_raw(),
        };

        let mut client = adapter.core.client.clone();
        let (buffer, outcome) = block_on_transfer(
            &adapter,
            client.rdma_set(key, buffer, offset, length, checksum_mode.with(checksum)),
        );

        let result = match outcome {
            Ok(()) => RdmaResult::stored(),
            Err(err) => RdmaResult::failed(errors::error_message(&err), errors::error_type(&err)),
        };
        region.finish(buffer, result).into_raw()
    }
}

/// Reject the argument mistakes both transfers share, or `None` if they pass.
fn reject_bad_transfer_args(
    client_adapter_ptr: *const c_void,
    key: *const u8,
    region: *const RdmaRegion,
) -> Option<RdmaResult> {
    if client_adapter_ptr.is_null() {
        return Some(RdmaResult::rejected(
            "cannot transfer without a client".to_string(),
        ));
    }
    if key.is_null() {
        return Some(RdmaResult::rejected(
            "cannot transfer without a key".to_string(),
        ));
    }
    if region.is_null() {
        return Some(RdmaResult::rejected(
            "cannot transfer without a registered region".to_string(),
        ));
    }
    None
}

/// Deregister a region, releasing the pinned pages.
///
/// Blocks until the region has closed, which can take milliseconds for a large
/// region on efa. The caller's memory is never freed here: it was never owned
/// here.
///
/// # Returns
///
/// Null when the caller may free or reuse the memory as soon as this returns. A
/// hold when the fabric still keeps it: a transfer on this region ended without
/// the server's reply and a write could still land, or the region failed to
/// close. The caller must then keep the memory alive until
/// [`rdma_memory_released`] returns true, and free the hold with
/// [`free_rdma_memory_hold`]. A hold for a region that failed to close is never
/// released. Null is a no-op that returns null.
///
/// # Safety
///
/// * `region` must be null or a region from [`register_rdma_region`].
/// * `free_rdma_region` may be called only once per region.
/// * No transfer against the region may be in flight.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_rdma_region(region: *mut RdmaRegion) -> *mut RdmaMemoryHold {
    // Without the `rdma` feature no region can ever have been handed out, so
    // there is nothing to release.
    #[cfg(feature = "rdma")]
    if !region.is_null() {
        let RdmaRegion {
            buffer,
            released,
            fabric,
            ..
        } = *unsafe { Box::from_raw(region) };
        // Dropping the buffer only queues its region's close on the fabric's
        // closing thread. Waiting for it means an idle region's memory is let go
        // of before this returns, so the caller gets null rather than a hold.
        drop(buffer);
        fabric.wait_for_closes();
        // If this was the fabric's last handle, dropping it closes the fabric and
        // lets go of any memory it parked, so check `released` only after.
        drop(fabric);
        if !released.load(Ordering::Acquire) {
            return Box::into_raw(Box::new(RdmaMemoryHold { released }));
        }
    }
    #[cfg(not(feature = "rdma"))]
    let _ = region;
    std::ptr::null_mut()
}

/// Whether the fabric has let go of the memory a [`RdmaMemoryHold`] keeps. Once
/// true, it stays true, and the caller may free the memory. True for null.
///
/// # Safety
///
/// * `hold` must be null or a live hold from [`free_rdma_region`].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rdma_memory_released(hold: *const RdmaMemoryHold) -> bool {
    #[cfg(feature = "rdma")]
    if !hold.is_null() {
        return unsafe { &*hold }.released.load(Ordering::Acquire);
    }
    #[cfg(not(feature = "rdma"))]
    let _ = hold;
    true
}

/// Free a hold from [`free_rdma_region`]. This does not free the caller's memory,
/// and is safe whether or not it has been released. Null is a no-op.
///
/// # Safety
///
/// * `hold` must be null or a hold from [`free_rdma_region`], freed only once.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_rdma_memory_hold(hold: *mut RdmaMemoryHold) {
    #[cfg(feature = "rdma")]
    if !hold.is_null() {
        drop(unsafe { Box::from_raw(hold) });
    }
    #[cfg(not(feature = "rdma"))]
    let _ = hold;
}

/// Deallocate a [`RdmaRegistration`] and the error message it carries.
///
/// The region it carries is not freed: it outlives the registration and is
/// freed with [`free_rdma_region`]. Null is a no-op.
///
/// # Safety
///
/// * `registration` must be null or a pointer from [`register_rdma_region`].
/// * `free_rdma_registration` may be called only once per registration.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_rdma_registration(registration: *mut RdmaRegistration) {
    if registration.is_null() {
        return;
    }
    let registration = unsafe { Box::from_raw(registration) };
    if !registration.error_message.is_null() {
        drop(unsafe { CString::from_raw(registration.error_message as *mut c_char) });
    }
}

/// Deallocate a [`RdmaResult`] and the error message it carries.
///
/// Null is a no-op.
///
/// # Safety
///
/// * `result` must be null or a pointer from [`rdma_get`] or [`rdma_set`].
/// * `free_rdma_result` may be called only once per result.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_rdma_result(result: *mut RdmaResult) {
    if result.is_null() {
        return;
    }
    let result = unsafe { Box::from_raw(result) };
    if !result.error_message.is_null() {
        drop(unsafe { CString::from_raw(result.error_message as *mut c_char) });
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A non-null pointer that is never dereferenced.
    fn unread_client_ptr() -> *const c_void {
        std::ptr::dangling::<u8>() as *const c_void
    }

    /// Read an error message out of a result, then free the result.
    fn take_error(result: *mut RdmaResult) -> Option<String> {
        assert!(!result.is_null());
        let message = unsafe { (*result).error_message };
        let message = (!message.is_null()).then(|| {
            unsafe { CStr::from_ptr(message) }
                .to_string_lossy()
                .into_owned()
        });
        unsafe { free_rdma_result(result) };
        message
    }

    /// Read an error message out of a registration, then free the registration.
    /// Asserts no region came back, which a failed registration must guarantee.
    fn take_registration_error(registration: *mut RdmaRegistration) -> Option<String> {
        assert!(!registration.is_null());
        let (message, region) = unsafe { ((*registration).error_message, (*registration).region) };
        assert!(
            region.is_null(),
            "a failed registration must carry no region"
        );
        let message = (!message.is_null()).then(|| {
            unsafe { CStr::from_ptr(message) }
                .to_string_lossy()
                .into_owned()
        });
        unsafe { free_rdma_registration(registration) };
        message
    }

    fn register(client: *const c_void, memory: *mut u8, length: usize) -> *mut RdmaRegistration {
        unsafe { register_rdma_region(client, memory, length) }
    }

    #[test]
    fn registering_without_a_client_is_refused() {
        let mut memory = [0u8; 64];
        let error = take_registration_error(register(
            std::ptr::null(),
            memory.as_mut_ptr(),
            memory.len(),
        ));
        assert_eq!(
            error.as_deref(),
            Some("cannot register a region without a client")
        );
    }

    #[test]
    fn registering_a_null_pointer_is_refused() {
        let error =
            take_registration_error(register(unread_client_ptr(), std::ptr::null_mut(), 64));
        assert_eq!(error.as_deref(), Some("cannot register a null pointer"));
    }

    #[test]
    fn registering_an_empty_region_is_refused() {
        let mut memory = [0u8; 64];
        let error = take_registration_error(register(unread_client_ptr(), memory.as_mut_ptr(), 0));
        assert_eq!(error.as_deref(), Some("cannot register 0 bytes"));
    }

    #[test]
    fn a_transfer_without_a_client_is_refused() {
        let key = b"key";
        let region = std::ptr::dangling_mut::<RdmaRegion>();
        let error = take_error(unsafe {
            rdma_get(std::ptr::null(), key.as_ptr(), key.len(), region, 0, 3)
        });
        assert_eq!(error.as_deref(), Some("cannot transfer without a client"));

        let error = take_error(unsafe {
            rdma_set(
                std::ptr::null(),
                key.as_ptr(),
                key.len(),
                region,
                0,
                3,
                RdmaSetChecksumMode::Skip,
                0,
            )
        });
        assert_eq!(error.as_deref(), Some("cannot transfer without a client"));
    }

    #[test]
    fn a_transfer_without_a_key_is_refused() {
        let region = std::ptr::dangling_mut::<RdmaRegion>();
        let error =
            take_error(unsafe { rdma_get(unread_client_ptr(), std::ptr::null(), 0, region, 0, 0) });
        assert_eq!(error.as_deref(), Some("cannot transfer without a key"));

        let error = take_error(unsafe {
            rdma_set(
                unread_client_ptr(),
                std::ptr::null(),
                0,
                region,
                0,
                0,
                RdmaSetChecksumMode::Skip,
                0,
            )
        });
        assert_eq!(error.as_deref(), Some("cannot transfer without a key"));
    }

    #[test]
    fn a_transfer_without_a_region_is_refused() {
        let key = b"key";
        let error = take_error(unsafe {
            rdma_get(
                unread_client_ptr(),
                key.as_ptr(),
                key.len(),
                std::ptr::null_mut(),
                0,
                3,
            )
        });
        assert_eq!(
            error.as_deref(),
            Some("cannot transfer without a registered region")
        );

        let error = take_error(unsafe {
            rdma_set(
                unread_client_ptr(),
                key.as_ptr(),
                key.len(),
                std::ptr::null_mut(),
                0,
                3,
                RdmaSetChecksumMode::Skip,
                0,
            )
        });
        assert_eq!(
            error.as_deref(),
            Some("cannot transfer without a registered region")
        );
    }

    /// Each C mode maps to the matching choice, and only `Known` carries the
    /// caller's checksum: the value passed with the other modes is ignored.
    #[cfg(feature = "rdma")]
    #[test]
    fn each_checksum_mode_maps_to_its_set_checksum() {
        use glide_rdma::SetChecksum;
        assert_eq!(RdmaSetChecksumMode::Skip.with(7), SetChecksum::Skip);
        assert_eq!(RdmaSetChecksumMode::Compute.with(7), SetChecksum::Compute);
        assert_eq!(RdmaSetChecksumMode::Known.with(7), SetChecksum::Known(7));
    }

    /// The values are part of the C ABI that bindings declare by hand.
    #[test]
    fn checksum_modes_keep_their_c_values() {
        assert_eq!(RdmaSetChecksumMode::Skip as u32, 0);
        assert_eq!(RdmaSetChecksumMode::Compute as u32, 1);
        assert_eq!(RdmaSetChecksumMode::Known as u32, 2);
    }

    fn checksum_of(data: &[u8]) -> Option<u32> {
        let mut out = 0u32;
        unsafe { rdma_checksum(data.as_ptr(), data.len(), &mut out) }.then_some(out)
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn a_checksum_matches_the_known_crc32c_vectors() {
        // The same values the protocol's own checksum is tested against, so a
        // caller computing one here and the server computing one there agree.
        assert_eq!(checksum_of(b""), Some(0x0000_0000));
        assert_eq!(checksum_of(b"123456789"), Some(0xE306_9283));
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn a_checksum_of_nothing_is_accepted_through_a_null_pointer() {
        // An empty Python buffer can arrive as a null pointer, and zero bytes
        // still have a defined checksum.
        let mut out = 0xFFFF_FFFFu32;
        assert!(unsafe { rdma_checksum(std::ptr::null(), 0, &mut out) });
        assert_eq!(out, 0x0000_0000);
    }

    #[cfg(not(feature = "rdma"))]
    #[test]
    fn a_checksum_is_unavailable_without_rdma() {
        assert_eq!(checksum_of(b"123456789"), None);
    }

    #[test]
    fn a_checksum_refuses_arguments_that_describe_no_bytes() {
        let mut out = 7u32;
        // A null pointer with a non-zero length is a caller mistake, not an
        // empty buffer.
        assert!(!unsafe { rdma_checksum(std::ptr::null(), 8, &mut out) });
        assert!(!unsafe { rdma_checksum(b"abc".as_ptr(), 3, std::ptr::null_mut()) });
        assert_eq!(out, 7, "a refused call must not write through `out`");
    }

    #[test]
    fn freeing_null_is_a_no_op() {
        unsafe {
            free_rdma_region(std::ptr::null_mut());
            free_rdma_registration(std::ptr::null_mut());
            free_rdma_result(std::ptr::null_mut());
        }
    }

    #[test]
    fn a_null_region_has_no_capacity() {
        assert_eq!(unsafe { rdma_region_capacity(std::ptr::null()) }, 0);
    }

    #[cfg(not(feature = "rdma"))]
    #[test]
    fn a_build_without_rdma_says_so() {
        let mut memory = [0u8; 64];
        let error = take_registration_error(register(
            unread_client_ptr(),
            memory.as_mut_ptr(),
            memory.len(),
        ))
        .expect("registration must fail without the feature");
        assert!(
            error.contains("no RDMA support compiled in"),
            "unexpected message: {error}"
        );

        let key = b"key";
        let region = std::ptr::dangling_mut::<RdmaRegion>();
        let error = take_error(unsafe {
            rdma_get(unread_client_ptr(), key.as_ptr(), key.len(), region, 0, 3)
        })
        .expect("a transfer must fail without the feature");
        assert!(
            error.contains("no RDMA support compiled in"),
            "unexpected message: {error}"
        );

        let error = take_error(unsafe {
            rdma_set(
                unread_client_ptr(),
                key.as_ptr(),
                key.len(),
                region,
                0,
                3,
                RdmaSetChecksumMode::Known,
                7,
            )
        })
        .expect("a transfer must fail without the feature");
        assert!(
            error.contains("no RDMA support compiled in"),
            "unexpected message: {error}"
        );
    }

    /// The region hands the fabric the caller's own allocation, not a copy, so
    /// a write through it has to land in the caller's bytes.
    #[cfg(feature = "rdma")]
    #[test]
    fn a_foreign_region_writes_through_to_the_callers_memory() {
        let mut memory = vec![0u8; 8];
        let mut region = ForeignRegion {
            memory: memory.as_mut_ptr(),
            length: memory.len(),
            released: Arc::new(AtomicBool::new(false)),
        };
        region.as_mut().copy_from_slice(b"12345678");
        assert_eq!(memory, b"12345678");
    }

    /// A region over `memory` on `fabric`, as `register_rdma_region` builds one.
    #[cfg(feature = "rdma")]
    fn foreign_region(fabric: &glide_rdma::RdmaFabric, memory: &mut [u8]) -> Box<RdmaRegion> {
        let released = Arc::new(AtomicBool::new(false));
        let buffer = fabric
            .register(ForeignRegion {
                memory: memory.as_mut_ptr(),
                length: memory.len(),
                released: released.clone(),
            })
            .expect("registers");
        Box::new(RdmaRegion::new(buffer, released))
    }

    #[cfg(feature = "rdma")]
    fn tcp_fabric() -> glide_rdma::RdmaFabric {
        glide_rdma::RdmaFabric::open(&glide_rdma::FabricConfig::new(glide_rdma::Provider::Tcp))
            .expect("tcp opens")
    }

    /// An idle region closes cleanly, so the caller may free its memory at once.
    #[cfg(feature = "rdma")]
    #[test]
    fn freeing_an_idle_region_releases_the_memory_at_once() {
        let fabric = tcp_fabric();
        let mut memory = vec![0u8; 64];
        let region = foreign_region(&fabric, &mut memory);

        let hold = unsafe { free_rdma_region(Box::into_raw(region)) };

        assert!(hold.is_null(), "nothing holds the memory any more");
    }

    /// On tcp a transfer that ended without a reply leaves the memory parked in the
    /// fabric, because a write could still land. The caller has to keep it until
    /// the fabric closes.
    #[cfg(feature = "rdma")]
    #[test]
    fn a_parked_region_holds_the_memory_until_the_fabric_closes() {
        let fabric = tcp_fabric();
        let mut memory = vec![0u8; 64];
        let mut region = foreign_region(&fabric, &mut memory);
        let (_command, loan) = region
            .buffer
            .take()
            .unwrap()
            .lend_for_get(b"key", 0, 64)
            .expect("lends");
        loan.recall().expect("the region closes");

        let hold = unsafe { free_rdma_region(Box::into_raw(region)) };

        assert!(!hold.is_null(), "the fabric still holds the memory");
        assert!(!unsafe { rdma_memory_released(hold) });
        drop(fabric);
        assert!(
            unsafe { rdma_memory_released(hold) },
            "closing the fabric lets it go"
        );
        unsafe { free_rdma_memory_hold(hold) };
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn a_transfer_that_hands_the_buffer_back_leaves_the_region_open() {
        let fabric = tcp_fabric();
        let mut memory = vec![0u8; 64];
        let mut region = foreign_region(&fabric, &mut memory);
        let buffer = region.take().ok().expect("the region has its buffer");

        let failed = RdmaResult::rejected("the server refused it".to_string());
        let result = region.finish(Some(buffer), failed);

        assert!(!result.region_closed);
        assert!(
            region.take().is_ok(),
            "the next transfer can use the region"
        );
        unsafe { free_rdma_result(result.into_raw()) };
    }

    /// No buffer comes back when a transfer ends without the server's reply, so
    /// both that transfer and every later one report the region closed.
    #[cfg(feature = "rdma")]
    #[test]
    fn a_transfer_without_the_buffer_back_reports_the_region_closed() {
        let fabric = tcp_fabric();
        let mut memory = vec![0u8; 64];
        let mut region = foreign_region(&fabric, &mut memory);
        let (_command, loan) = region
            .take()
            .ok()
            .expect("the region has its buffer")
            .lend_for_get(b"key", 0, 64)
            .expect("lends");
        loan.recall().expect("the region closes");

        let lost = RdmaResult::rejected("the connection was lost".to_string());
        let result = region.finish(None, lost);
        assert!(result.region_closed);

        let next = match region.take() {
            Ok(_) => panic!("a closed region has no buffer to lend"),
            Err(rejection) => rejection,
        };
        assert!(next.region_closed);
        assert!(!next.error_message.is_null());
        unsafe {
            free_rdma_result(result.into_raw());
            free_rdma_result(next.into_raw());
            free_rdma_memory_hold(free_rdma_region(Box::into_raw(region)));
        }
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn a_buffer_handed_back_revoked_reports_the_region_closed() {
        let fabric = tcp_fabric();
        let mut memory = vec![0u8; 64];
        let mut region = foreign_region(&fabric, &mut memory);
        let buffer = region.take().ok().expect("the region has its buffer");
        buffer.revoke().expect("the region closes");

        let cancelled = RdmaResult::rejected("the client was closed".to_string());
        let result = region.finish(Some(buffer), cancelled);

        assert!(result.region_closed);
        unsafe { free_rdma_result(result.into_raw()) };
    }

    #[test]
    fn a_rejected_transfer_does_not_report_the_region_closed() {
        let key = b"key";
        let result = unsafe {
            rdma_get(
                std::ptr::null(),
                key.as_ptr(),
                key.len(),
                std::ptr::null_mut(),
                0,
                3,
            )
        };
        assert!(!unsafe { (*result).region_closed });
        unsafe { free_rdma_result(result) };
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn a_null_hold_is_released_and_freeing_it_is_a_no_op() {
        assert!(unsafe { rdma_memory_released(std::ptr::null()) });
        unsafe { free_rdma_memory_hold(std::ptr::null_mut()) };
        assert!(unsafe { free_rdma_region(std::ptr::null_mut()) }.is_null());
    }
}
