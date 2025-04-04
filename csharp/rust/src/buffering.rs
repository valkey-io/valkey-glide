use crate::helpers;
use std::ffi::{c_void, CString};
use std::os::raw::c_char;
use std::ptr::null;
use crate::value::{StringPair, Value};

/// # Summary
/// This struct is an optimization technique used to reduce the general number of allocations
/// required to pass the FFI boundary
///
/// # State
/// This struct will start out in a so-called "count mode", in which writing to the buffer will
/// not do anything. Instead, the buffer will count how many bytes are needed.
///
/// Depending on the output size needed, it may allocate a single buffer on Heap, or use the
/// in-struct buffer, greatly improving performance for low-allocation scenarios.
///
/// ## How to use
/// 1. Call `let mut buffer: Buffering::new()` to create a new buffer.
/// 2. Use `buffer.write_to_buffer(...)` to count the required buffer size.
/// 3. Call `buffer.switch_mode()`.
/// 4. Use THE SAME `buffer.write_to_buffer(...)` calls to actually write out to the buffer.
pub struct FFIBuffer {
    // Small buffer for stack-based optimization
    small_buffer: [u8; SMALL_BUFFER_SIZE],

    // Index into small_buffer
    small_buffer_index: usize,

    // A large buffer if the small buffer is exceeded
    // If the data size is too big for small_buffer, only use large_buffer
    large_buffer: Vec<u8>,

    // The variable counting the capacity needs
    buffer_size_need: usize,

    // Whether the count (aka: buffer_size_need being modified) mode is active or
    // the data is actually written to the buffers
    count_mode: bool,

    // Whether the large buffer is being used or the small buffer.
    is_large_buffer_active: bool,
}

const SMALL_BUFFER_SIZE: usize = 1024;
impl FFIBuffer {
    pub fn new() -> Self {
        Self {
            small_buffer: [0; SMALL_BUFFER_SIZE],
            small_buffer_index: 0,
            large_buffer: Vec::new(),
            buffer_size_need: 0,
            count_mode: true,
            is_large_buffer_active: false,
        }
    }

    /// Changes the mode into count_mode, allowing buffer usage.
    pub fn switch_mode(&mut self) {
        assert!(self.count_mode);
        self.count_mode = false;
        if self.buffer_size_need > SMALL_BUFFER_SIZE {
            self.is_large_buffer_active = true;
            self.large_buffer = Vec::with_capacity(self.buffer_size_need);
        } else {
            self.is_large_buffer_active = false;
        }
        self.buffer_size_need = 0;
        self.small_buffer_index = 0;
    }

    /// # Summary
    /// Writes to the buffer, returning a pointer into the buffer if count_mode is false
    ///
    /// # Note
    /// This struct has state! See the struct documentation for usage.
    ///
    /// # Regarding safety
    /// This method is not safe for use if used incorrectly!
    /// The stateflow MUST always be kept!
    pub fn write_to_buffer(&mut self, data: &[u8]) -> *const u8 {
        if self.count_mode {
            self.buffer_size_need += data.len();
            null()
        } else {
            unsafe {
                if self.is_large_buffer_active {
                    let ptr = self.large_buffer.as_ptr().add(self.large_buffer.len());
                    self.large_buffer.extend_from_slice(data);
                    ptr
                } else {
                    let ptr = self.small_buffer.as_mut_ptr().add(self.small_buffer_index);
                    self.small_buffer[self.small_buffer_index..data.len()].copy_from_slice(data);
                    self.small_buffer_index += data.len();
                    ptr
                }
            }
        }
    }

    pub fn write_values_to_buffer(&mut self, data: &[Value]) -> *const u8 {
        let data = unsafe { helpers::any_as_u8_slice(&data) };
        self.write_to_buffer(data)
    }
    pub fn write_string_pair_to_buffer(&mut self, first: &str, second: &str) -> *const u8 {
        unsafe {
            let a = self.write_to_buffer(first.as_bytes()) as *mut c_void;
            let b = self.write_to_buffer(second.as_bytes()) as *mut c_void;
            debug_assert!((a.is_null() && b.is_null()) || a.add(first.len()) == b);
            let output = StringPair {
                a_start: a as *mut c_char,
                a_end: a.add(first.len()) as *mut c_char,
                b_start: b as *mut c_char,
                b_end: b.add(second.len()) as *mut c_char,
            };
            let data = helpers::any_as_u8_slice(&output);
            self.write_to_buffer(data)
        }
    }

    pub fn write_string_to_buffer(&mut self, data: &CString) -> *const u8 {
        let data = data.as_bytes_with_nul();
        self.write_to_buffer(data)
    }
}
