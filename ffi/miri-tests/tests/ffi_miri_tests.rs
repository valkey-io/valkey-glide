use miri_tests::PushKind;
use miri_tests::create_client;
use std::ffi::c_void;

#[test]
fn create_client_test() {
    unsafe {
        create_client(
            std::ptr::null(),
            0,
            std::ptr::null(),
            std::mem::transmute::<
                *mut c_void,
                unsafe extern "C-unwind" fn(
                    client_ptr: usize,
                    kind: PushKind,
                    message: *const u8,
                    message_len: i64,
                    channel: *const u8,
                    channel_len: i64,
                    pattern: *const u8,
                    pattern_len: i64,
                ),
            >(std::ptr::null_mut()),
        )
    };
}
