// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// ═══════════════════════════════════════════════════════════════════════════════
// RDMA FFI
//
// Registers caller-owned memory with the client's fabric and runs RDMA get/set
// transfers against it. The RESP channel coordinates the transfers with the server,
// but the server reads/writes the registered region directly.
// ═══════════════════════════════════════════════════════════════════════════════

use super::*;

/// Whether this build has RDMA compiled in via the `rdma` feature.
#[unsafe(no_mangle)]
pub extern "C" fn rdma_available() -> bool {
    cfg!(feature = "rdma")
}

/// Whether this machine can actually carry an RDMA transfer.
///
/// Requires the build to have had the `rdma` feature enabled and the machine
/// to have libfabric installed and fabric hardware available.
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
    /// and put back when the transfer hands it back. Stays empty only if a
    /// cancelled transfer could not close the region.
    buffer: Option<glide_rdma::RdmaBuffer>,
    capacity: usize,
}

#[cfg(feature = "rdma")]
impl RdmaRegion {
    /// Take the buffer out for a transfer, or say why there is none.
    fn take(&mut self) -> Result<glide_rdma::RdmaBuffer, RdmaResult> {
        self.buffer.take().ok_or_else(|| {
            RdmaResult::rejected(
                "the region was lost when a cancelled transfer could not close it; free \
                 it and register the memory again"
                    .to_string(),
            )
        })
    }
}

/// Placeholder type for a registered region of caller-owned memory
/// when the `rdma` feature is not enabled.
#[cfg(not(feature = "rdma"))]
pub enum RdmaRegion {}

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
        }
    }

    #[cfg(feature = "rdma")]
    fn transferred(receipt: &glide_rdma::ReadReceipt) -> Self {
        Self {
            error_message: std::ptr::null(),
            error_type: RequestErrorType::Unspecified,
            found: true,
            bytes_written: receipt.bytes_written,
            has_checksum: receipt.checksum.is_some(),
            checksum: receipt.checksum.unwrap_or(0),
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
#[cfg(feature = "rdma")]
struct ForeignRegion {
    memory: *mut u8,
    length: usize,
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
        match adapter
            .core
            .client
            .register_rdma_region(ForeignRegion { memory, length })
        {
            Ok(buffer) => RdmaRegistration {
                error_message: std::ptr::null(),
                region: Box::into_raw(Box::new(RdmaRegion {
                    capacity: buffer.capacity(),
                    buffer: Some(buffer),
                })),
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
/// After an error the server sent, the region can be used again. After any
/// other error, such as a lost connection, the server might still be using the
/// memory, so the region is revoked first and cannot be used again either.
///
/// TODO: `length` argument
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
        region.buffer = buffer;

        match outcome {
            Ok(Some(receipt)) => RdmaResult::transferred(&receipt).into_raw(),
            Ok(None) => RdmaResult::missing().into_raw(),
            Err(err) => {
                RdmaResult::failed(errors::error_message(&err), errors::error_type(&err)).into_raw()
            }
        }
    }
}

/// Store a value the server reads directly out of the
/// `[offset, offset + length)` window of a registered region.
///
/// Like [`rdma_get`], it has no timeout, and calling [`close_client`] from
/// another thread cancels it.
///
/// # Returns
///
/// A [`RdmaResult`] the caller frees with [`free_rdma_result`].
///
/// # Safety
///
/// Same contract as [`rdma_get`]. The region is read, not written, but the
/// caller must still hold it exclusively for the duration of the call.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn rdma_set(
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
            block_on_transfer(&adapter, client.rdma_set(key, buffer, offset, length));
        region.buffer = buffer;

        match outcome {
            Ok(()) => RdmaResult::stored().into_raw(),
            Err(err) => {
                RdmaResult::failed(errors::error_message(&err), errors::error_type(&err)).into_raw()
            }
        }
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
/// The caller's memory is not freed: it was never owned here. Once this
/// returns, the allocation is the caller's to free or reuse. Null is a no-op.
///
/// # Safety
///
/// * `region` must be null or a region from [`register_rdma_region`].
/// * `free_rdma_region` may be called only once per region.
/// * No transfer against the region may be in flight.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_rdma_region(region: *mut RdmaRegion) {
    // Without the `rdma` feature no region can ever have been handed out, so
    // there is nothing to release.
    #[cfg(feature = "rdma")]
    if !region.is_null() {
        drop(unsafe { Box::from_raw(region) });
    }
    #[cfg(not(feature = "rdma"))]
    let _ = region;
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
            rdma_set(std::ptr::null(), key.as_ptr(), key.len(), region, 0, 3)
        });
        assert_eq!(error.as_deref(), Some("cannot transfer without a client"));
    }

    #[test]
    fn a_transfer_without_a_key_is_refused() {
        let region = std::ptr::dangling_mut::<RdmaRegion>();
        let error =
            take_error(unsafe { rdma_get(unread_client_ptr(), std::ptr::null(), 0, region, 0, 0) });
        assert_eq!(error.as_deref(), Some("cannot transfer without a key"));

        let error =
            take_error(unsafe { rdma_set(unread_client_ptr(), std::ptr::null(), 0, region, 0, 0) });
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
            )
        });
        assert_eq!(
            error.as_deref(),
            Some("cannot transfer without a registered region")
        );
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
        };
        region.as_mut().copy_from_slice(b"12345678");
        assert_eq!(memory, b"12345678");
    }
}
