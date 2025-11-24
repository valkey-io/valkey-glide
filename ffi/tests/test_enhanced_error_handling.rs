use glide_core::request_type::RequestType;
use glide_ffi::{create_named_otel_span, create_otel_span_with_parent, drop_otel_span};
use std::ffi::CString;

#[test]
fn test_enhanced_error_handling() {
    // Initialize logger to capture error messages
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test 1: create_named_otel_span with null pointer
    let null_span_ptr = unsafe { create_named_otel_span(std::ptr::null()) };
    assert_eq!(null_span_ptr, 0, "Null pointer should return 0");

    // Test 2: create_named_otel_span with very long name (over 256 chars)
    let long_name = "a".repeat(300);
    let long_name_cstring = CString::new(long_name).expect("CString::new failed");
    let long_span_ptr = unsafe { create_named_otel_span(long_name_cstring.as_ptr()) };
    assert_eq!(long_span_ptr, 0, "Long name should return 0");

    // Test 3: create_named_otel_span with valid name should succeed
    let valid_name = CString::new("test_span").expect("CString::new failed");
    let valid_span_ptr = unsafe { create_named_otel_span(valid_name.as_ptr()) };
    assert_ne!(valid_span_ptr, 0, "Valid name should succeed");

    // Test 4: create_named_otel_span with empty name should succeed (as per test expectations)
    let empty_name = CString::new("").expect("CString::new failed");
    let empty_span_ptr = unsafe { create_named_otel_span(empty_name.as_ptr()) };
    assert_ne!(empty_span_ptr, 0, "Empty name should succeed");

    // Test 5: create_otel_span_with_parent with null parent should fallback to independent span
    let child_with_null_parent = unsafe { create_otel_span_with_parent(RequestType::Get, 0) };
    assert_ne!(
        child_with_null_parent, 0,
        "Null parent should fallback to independent span"
    );

    // Test 6: create_otel_span_with_parent with garbage parent should fallback to independent span
    let child_with_garbage_parent =
        unsafe { create_otel_span_with_parent(RequestType::Get, 0xDEADBEEF) };
    assert_ne!(
        child_with_garbage_parent, 0,
        "Garbage parent should fallback to independent span"
    );

    // Test 7: create_otel_span_with_parent with invalid request type should return 0
    let child_with_invalid_request =
        unsafe { create_otel_span_with_parent(RequestType::InvalidRequest, valid_span_ptr) };
    assert_eq!(
        child_with_invalid_request, 0,
        "Invalid request type should return 0"
    );

    // Test 8: create_otel_span_with_parent with valid parent should succeed
    let child_with_valid_parent =
        unsafe { create_otel_span_with_parent(RequestType::Set, valid_span_ptr) };
    assert_ne!(child_with_valid_parent, 0, "Valid parent should succeed");

    // Test 9: drop_otel_span with null pointer should not crash
    unsafe {
        drop_otel_span(0); // Should not crash
    }

    // Test 10: drop_otel_span with garbage pointer should not crash
    unsafe {
        drop_otel_span(0xDEADBEEF); // Should not crash, should log error
    }

    // Clean up valid spans
    unsafe {
        drop_otel_span(valid_span_ptr);
        drop_otel_span(empty_span_ptr);
        drop_otel_span(child_with_null_parent);
        drop_otel_span(child_with_garbage_parent);
        drop_otel_span(child_with_valid_parent);
    }
}
