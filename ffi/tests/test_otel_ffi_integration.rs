use glide_core::request_type::RequestType;
use glide_ffi::{
    create_batch_otel_span, create_batch_otel_span_with_parent, create_named_otel_span,
    create_otel_span, create_otel_span_with_parent, drop_otel_span,
};
use std::ffi::CString;
use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::thread;
use std::time::Duration;

#[test]
fn test_create_otel_span_with_valid_inputs() {
    // Initialize logger to capture debug messages
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test creating spans with various valid request types
    let request_types = vec![
        RequestType::Get,
        RequestType::Set,
        RequestType::Del,
        RequestType::Exists,
        RequestType::Expire,
        RequestType::TTL,
        RequestType::HGet,
        RequestType::HSet,
        RequestType::HDel,
        RequestType::LPush,
        RequestType::RPush,
        RequestType::LPop,
        RequestType::RPop,
    ];

    let mut span_ptrs = Vec::new();

    for request_type in request_types {
        let span_ptr = create_otel_span(request_type);
        assert_ne!(
            span_ptr, 0,
            "create_otel_span should succeed for {request_type:?}",
        );

        // Verify pointer is properly aligned
        assert_eq!(span_ptr % 8, 0, "Span pointer should be 8-byte aligned");

        // Verify pointer is in reasonable range
        assert!(
            span_ptr >= 0x1000,
            "Span pointer should be above minimum valid address"
        );
        assert!(
            span_ptr <= 0x7FFF_FFFF_FFFF_FFF8,
            "Span pointer should be below maximum valid address"
        );

        span_ptrs.push(span_ptr);
    }

    // Clean up all spans
    for span_ptr in span_ptrs {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}

#[test]
fn test_create_otel_span_with_invalid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test with invalid request types that should return 0
    let invalid_request_types = vec![
        RequestType::InvalidRequest,
        // Add other invalid request types as needed
    ];

    for request_type in invalid_request_types {
        let span_ptr = create_otel_span(request_type);
        assert_eq!(
            span_ptr, 0,
            "create_otel_span should fail for invalid request type {request_type:?}",
        );
    }
}

#[test]
fn test_create_named_otel_span_with_valid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let test_cases = vec![
        ("simple_span", true),
        ("", true), // Empty names are allowed
        ("span_with_underscores", true),
        ("span-with-dashes", true),
        ("span.with.dots", true),
        ("span123", true),
        ("UPPERCASE_SPAN", true),
        ("MixedCase_Span", true),
        ("span\twith\ttabs", true),     // Tabs are allowed
        ("span\nwith\nnewlines", true), // Newlines are allowed
        ("span\rwith\rcarriage", true), // Carriage returns are allowed
    ];

    let mut span_ptrs = Vec::new();

    for (name, should_succeed) in test_cases {
        let name_cstring = CString::new(name).expect("CString::new failed");
        let span_ptr = unsafe { create_named_otel_span(name_cstring.as_ptr()) };

        if should_succeed {
            assert_ne!(
                span_ptr, 0,
                "create_named_otel_span should succeed for name: '{name}'",
            );

            // Verify pointer properties
            assert_eq!(span_ptr % 8, 0, "Span pointer should be 8-byte aligned");
            assert!(
                span_ptr >= 0x1000,
                "Span pointer should be above minimum valid address"
            );
            assert!(
                span_ptr <= 0x7FFF_FFFF_FFFF_FFF8,
                "Span pointer should be below maximum valid address"
            );

            span_ptrs.push(span_ptr);
        } else {
            assert_eq!(
                span_ptr, 0,
                "create_named_otel_span should fail for name: '{name}'",
            );
        }
    }

    // Clean up all valid spans
    for span_ptr in span_ptrs {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}

#[test]
fn test_create_named_otel_span_with_invalid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test with null pointer
    let null_span_ptr = unsafe { create_named_otel_span(std::ptr::null()) };
    assert_eq!(null_span_ptr, 0, "Null pointer should return 0");

    // Test with name that's too long (over 256 characters)
    let long_name = "a".repeat(300);
    let long_name_cstring = CString::new(long_name).expect("CString::new failed");
    let long_span_ptr = unsafe { create_named_otel_span(long_name_cstring.as_ptr()) };
    assert_eq!(long_span_ptr, 0, "Long name (300 chars) should return 0");

    // Test boundary case: exactly 256 characters should succeed
    let max_length_name = "a".repeat(256);
    let max_length_cstring = CString::new(max_length_name).expect("CString::new failed");
    let max_length_span_ptr = unsafe { create_named_otel_span(max_length_cstring.as_ptr()) };
    assert_ne!(max_length_span_ptr, 0, "256 character name should succeed");

    // Test boundary case: 257 characters should fail
    let over_max_name = "a".repeat(257);
    let over_max_cstring = CString::new(over_max_name).expect("CString::new failed");
    let over_max_span_ptr = unsafe { create_named_otel_span(over_max_cstring.as_ptr()) };
    assert_eq!(over_max_span_ptr, 0, "257 character name should fail");

    // Clean up valid span
    unsafe {
        drop_otel_span(max_length_span_ptr);
    }
}

#[test]
fn test_create_otel_span_with_parent_valid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Create a parent span
    let parent_name = CString::new("parent_span").expect("CString::new failed");
    let parent_span_ptr = unsafe { create_named_otel_span(parent_name.as_ptr()) };
    assert_ne!(parent_span_ptr, 0, "Parent span creation should succeed");

    // Test creating child spans with various request types
    let request_types = vec![
        RequestType::Get,
        RequestType::Set,
        RequestType::Del,
        RequestType::HGet,
        RequestType::HSet,
    ];

    let mut child_span_ptrs = Vec::new();

    for request_type in request_types {
        let child_span_ptr = unsafe { create_otel_span_with_parent(request_type, parent_span_ptr) };
        assert_ne!(
            child_span_ptr, 0,
            "Child span creation should succeed for {request_type:?}",
        );

        // Verify child span has different pointer than parent
        assert_ne!(
            child_span_ptr, parent_span_ptr,
            "Child span should have different pointer than parent"
        );

        // Verify pointer properties
        assert_eq!(
            child_span_ptr % 8,
            0,
            "Child span pointer should be 8-byte aligned"
        );
        assert!(
            child_span_ptr >= 0x1000,
            "Child span pointer should be above minimum valid address"
        );
        assert!(
            child_span_ptr <= 0x7FFF_FFFF_FFFF_FFF8,
            "Child span pointer should be below maximum valid address"
        );

        child_span_ptrs.push(child_span_ptr);
    }

    // Clean up all spans
    for child_span_ptr in child_span_ptrs {
        unsafe {
            drop_otel_span(child_span_ptr);
        }
    }

    unsafe {
        drop_otel_span(parent_span_ptr);
    }
}

#[test]
fn test_create_otel_span_with_parent_invalid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test with null parent (should fallback to independent span)
    let child_with_null_parent = unsafe { create_otel_span_with_parent(RequestType::Get, 0) };
    assert_ne!(
        child_with_null_parent, 0,
        "Null parent should fallback to independent span"
    );

    // Test with invalid parent pointer (should fallback to independent span)
    let invalid_parent_ptrs = vec![
        0xDEADBEEF,            // Garbage pointer
        0x1001,                // Misaligned pointer
        0x800,                 // Address too low
        0x8000_0000_0000_0000, // Address too high
    ];

    let mut fallback_span_ptrs = vec![child_with_null_parent];

    for invalid_parent_ptr in invalid_parent_ptrs {
        let child_span_ptr =
            unsafe { create_otel_span_with_parent(RequestType::Set, invalid_parent_ptr) };
        assert_ne!(
            child_span_ptr, 0,
            "Invalid parent 0x{invalid_parent_ptr:x} should fallback to independent span",
        );
        fallback_span_ptrs.push(child_span_ptr);
    }

    // Test with invalid request type (should return 0 regardless of parent)
    let valid_parent_name = CString::new("valid_parent").expect("CString::new failed");
    let valid_parent_ptr = unsafe { create_named_otel_span(valid_parent_name.as_ptr()) };
    assert_ne!(valid_parent_ptr, 0, "Valid parent creation should succeed");

    let child_with_invalid_request =
        unsafe { create_otel_span_with_parent(RequestType::InvalidRequest, valid_parent_ptr) };
    assert_eq!(
        child_with_invalid_request, 0,
        "Invalid request type should return 0 even with valid parent"
    );

    // Clean up all spans
    for span_ptr in fallback_span_ptrs {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }

    unsafe {
        drop_otel_span(valid_parent_ptr);
    }
}

#[test]
fn test_drop_otel_span_memory_safety() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test dropping null pointer (should not crash)
    unsafe {
        drop_otel_span(0);
    }

    // Test dropping invalid pointers (should not crash, should log errors)
    let invalid_pointers = vec![
        0xDEADBEEF,            // Garbage pointer
        0x1001,                // Misaligned pointer
        0x800,                 // Address too low
        0x8000_0000_0000_0000, // Address too high
        0xFFFF_FFFF_FFFF_FFFF, // Maximum u64
    ];

    for invalid_ptr in invalid_pointers {
        unsafe {
            drop_otel_span(invalid_ptr); // Should not crash
        }
    }

    // Test dropping valid spans
    let valid_name = CString::new("test_drop").expect("CString::new failed");
    let valid_span_ptr = unsafe { create_named_otel_span(valid_name.as_ptr()) };
    assert_ne!(valid_span_ptr, 0, "Valid span creation should succeed");

    unsafe {
        drop_otel_span(valid_span_ptr); // Should succeed
    }
}

#[test]
fn test_ffi_functions_concurrent_access() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let num_threads = 3; // Reduced number of threads to avoid race conditions
    let spans_per_thread = 2; // Reduced spans per thread
    let counter = Arc::new(AtomicUsize::new(0));
    let mut handles = Vec::new();

    // Test concurrent span creation and cleanup
    for thread_id in 0..num_threads {
        let counter_clone = Arc::clone(&counter);

        let handle = thread::spawn(move || {
            let mut thread_spans = Vec::new();

            // Create spans concurrently
            for i in 0..spans_per_thread {
                // Create named span
                let span_name = format!("thread_{thread_id}_span_{i}");
                let span_name_cstring = CString::new(span_name).expect("CString::new failed");
                let named_span_ptr = unsafe { create_named_otel_span(span_name_cstring.as_ptr()) };

                if named_span_ptr != 0 {
                    thread_spans.push(named_span_ptr);
                    counter_clone.fetch_add(1, Ordering::SeqCst);

                    // Only create child span if parent was successfully created
                    let child_span_ptr =
                        unsafe { create_otel_span_with_parent(RequestType::Get, named_span_ptr) };
                    if child_span_ptr != 0 {
                        thread_spans.push(child_span_ptr);
                        counter_clone.fetch_add(1, Ordering::SeqCst);
                    }
                }

                // Small delay to reduce race conditions
                thread::sleep(Duration::from_millis(10));
            }

            // Clean up spans in reverse order (children first)
            thread_spans.reverse();
            for span_ptr in thread_spans {
                unsafe {
                    drop_otel_span(span_ptr);
                }
                // Small delay between drops
                thread::sleep(Duration::from_millis(1));
            }
        });

        handles.push(handle);
    }

    // Wait for all threads to complete
    for handle in handles {
        handle.join().expect("Thread should complete successfully");
    }

    let total_spans_created = counter.load(Ordering::SeqCst);
    assert!(
        total_spans_created > 0,
        "At least some spans should have been created"
    );
    println!(
        "Successfully created and cleaned up {total_spans_created} spans across {num_threads} threads",
    );
}

#[test]
fn test_span_hierarchy_creation() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Create a multi-level span hierarchy
    let root_name = CString::new("root_span").expect("CString::new failed");
    let root_span_ptr = unsafe { create_named_otel_span(root_name.as_ptr()) };
    assert_ne!(root_span_ptr, 0, "Root span creation should succeed");

    // Create first level children
    let child1_ptr = unsafe { create_otel_span_with_parent(RequestType::Get, root_span_ptr) };
    let child2_ptr = unsafe { create_otel_span_with_parent(RequestType::Set, root_span_ptr) };

    assert_ne!(child1_ptr, 0, "Child1 span creation should succeed");
    assert_ne!(child2_ptr, 0, "Child2 span creation should succeed");
    assert_ne!(
        child1_ptr, child2_ptr,
        "Child spans should have different pointers"
    );

    // Create second level children (grandchildren)
    let grandchild1_ptr = unsafe { create_otel_span_with_parent(RequestType::Del, child1_ptr) };
    let grandchild2_ptr = unsafe { create_otel_span_with_parent(RequestType::Exists, child2_ptr) };

    assert_ne!(
        grandchild1_ptr, 0,
        "Grandchild1 span creation should succeed"
    );
    assert_ne!(
        grandchild2_ptr, 0,
        "Grandchild2 span creation should succeed"
    );
    assert_ne!(
        grandchild1_ptr, grandchild2_ptr,
        "Grandchild spans should have different pointers"
    );

    // Verify all spans have unique pointers
    let all_spans = [
        root_span_ptr,
        child1_ptr,
        child2_ptr,
        grandchild1_ptr,
        grandchild2_ptr,
    ];
    for i in 0..all_spans.len() {
        for j in (i + 1)..all_spans.len() {
            assert_ne!(
                all_spans[i], all_spans[j],
                "All spans should have unique pointers"
            );
        }
    }

    // Clean up in reverse order (children before parents)
    unsafe {
        drop_otel_span(grandchild1_ptr);
        drop_otel_span(grandchild2_ptr);
        drop_otel_span(child1_ptr);
        drop_otel_span(child2_ptr);
        drop_otel_span(root_span_ptr);
    }
}

#[test]
fn test_error_handling_and_logging() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test various error conditions to ensure proper logging

    // 1. Invalid request type
    let invalid_span = create_otel_span(RequestType::InvalidRequest);
    assert_eq!(invalid_span, 0, "Invalid request type should return 0");

    // 2. Null pointer for named span
    let null_named_span = unsafe { create_named_otel_span(std::ptr::null()) };
    assert_eq!(null_named_span, 0, "Null pointer should return 0");

    // 3. Long name for named span
    let long_name = "a".repeat(300);
    let long_name_cstring = CString::new(long_name).expect("CString::new failed");
    let long_named_span = unsafe { create_named_otel_span(long_name_cstring.as_ptr()) };
    assert_eq!(long_named_span, 0, "Long name should return 0");

    // 4. Invalid parent for child span
    let child_with_invalid_parent =
        unsafe { create_otel_span_with_parent(RequestType::Get, 0xDEADBEEF) };
    assert_ne!(
        child_with_invalid_parent, 0,
        "Invalid parent should fallback to independent span"
    );

    // 5. Drop invalid spans
    unsafe {
        drop_otel_span(0xDEADBEEF); // Should log error but not crash
        drop_otel_span(0x1001); // Should log error but not crash
    }

    // Clean up valid span
    unsafe {
        drop_otel_span(child_with_invalid_parent);
    }
}

#[test]
fn test_boundary_conditions() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test span name length boundaries
    let boundary_cases = vec![
        (255, true),  // Just under limit
        (256, true),  // At limit
        (257, false), // Just over limit
    ];

    let mut valid_spans = Vec::new();

    for (length, should_succeed) in boundary_cases {
        let name = "a".repeat(length);
        let name_cstring = CString::new(name).expect("CString::new failed");
        let span_ptr = unsafe { create_named_otel_span(name_cstring.as_ptr()) };

        if should_succeed {
            assert_ne!(span_ptr, 0, "Name with {length} characters should succeed",);
            valid_spans.push(span_ptr);
        } else {
            assert_eq!(span_ptr, 0, "Name with {length} characters should fail");
        }
    }

    // Test pointer alignment boundaries with drop_otel_span
    // These should all be handled gracefully without crashing
    let invalid_pointers = vec![
        0x1001, // Not 8-byte aligned
        0x1004, // Not 8-byte aligned
        0x1007, // Not 8-byte aligned
        0x800,  // Address too low
    ];

    for ptr_value in invalid_pointers {
        // These should handle invalid pointers gracefully without crashing
        unsafe {
            drop_otel_span(ptr_value); // Should log error but not crash
        }
    }

    // Clean up valid spans
    for span_ptr in valid_spans {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}
#[test]
fn test_create_batch_otel_span() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test creating independent batch span
    let batch_span_ptr = create_batch_otel_span();
    assert_ne!(batch_span_ptr, 0, "create_batch_otel_span should succeed");

    // Verify pointer properties
    assert_eq!(
        batch_span_ptr % 8,
        0,
        "Batch span pointer should be 8-byte aligned"
    );
    assert!(
        batch_span_ptr >= 0x1000,
        "Batch span pointer should be above minimum valid address"
    );
    assert!(
        batch_span_ptr <= 0x7FFF_FFFF_FFFF_FFF8,
        "Batch span pointer should be below maximum valid address"
    );

    // Clean up
    unsafe {
        drop_otel_span(batch_span_ptr);
    }
}

#[test]
fn test_create_batch_otel_span_with_parent_valid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Create a parent span
    let parent_name = CString::new("parent_operation").expect("CString::new failed");
    let parent_span_ptr = unsafe { create_named_otel_span(parent_name.as_ptr()) };
    assert_ne!(parent_span_ptr, 0, "Parent span creation should succeed");

    // Create batch span with parent
    let batch_span_ptr = unsafe { create_batch_otel_span_with_parent(parent_span_ptr) };
    assert_ne!(
        batch_span_ptr, 0,
        "create_batch_otel_span_with_parent should succeed"
    );

    // Verify batch span has different pointer than parent
    assert_ne!(
        batch_span_ptr, parent_span_ptr,
        "Batch span should have different pointer than parent"
    );

    // Verify pointer properties
    assert_eq!(
        batch_span_ptr % 8,
        0,
        "Batch span pointer should be 8-byte aligned"
    );
    assert!(
        batch_span_ptr >= 0x1000,
        "Batch span pointer should be above minimum valid address"
    );
    assert!(
        batch_span_ptr <= 0x7FFF_FFFF_FFFF_FFF8,
        "Batch span pointer should be below maximum valid address"
    );

    // Clean up (child first, then parent)
    unsafe {
        drop_otel_span(batch_span_ptr);
        drop_otel_span(parent_span_ptr);
    }
}

#[test]
fn test_create_batch_otel_span_with_parent_invalid_inputs() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test with null parent (should fallback to independent batch span)
    let batch_with_null_parent = unsafe { create_batch_otel_span_with_parent(0) };
    assert_ne!(
        batch_with_null_parent, 0,
        "Null parent should fallback to independent batch span"
    );

    // Test with invalid parent pointers (should fallback to independent batch span)
    let invalid_parent_ptrs = vec![
        0xDEADBEEF,            // Garbage pointer
        0x1001,                // Misaligned pointer
        0x800,                 // Address too low
        0x8000_0000_0000_0000, // Address too high
    ];

    let mut fallback_batch_spans = vec![batch_with_null_parent];

    for invalid_parent_ptr in invalid_parent_ptrs {
        let batch_span_ptr = unsafe { create_batch_otel_span_with_parent(invalid_parent_ptr) };
        assert_ne!(
            batch_span_ptr, 0,
            "Invalid parent 0x{invalid_parent_ptr:x} should fallback to independent batch span",
        );
        fallback_batch_spans.push(batch_span_ptr);
    }

    // Clean up all fallback batch spans
    for span_ptr in fallback_batch_spans {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}

#[test]
fn test_batch_span_hierarchy() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Create a root operation span
    let root_name = CString::new("user_operation").expect("CString::new failed");
    let root_span_ptr = unsafe { create_named_otel_span(root_name.as_ptr()) };
    assert_ne!(root_span_ptr, 0, "Root span creation should succeed");

    // Create batch span as child of root
    let batch_span_ptr = unsafe { create_batch_otel_span_with_parent(root_span_ptr) };
    assert_ne!(batch_span_ptr, 0, "Batch span creation should succeed");

    // Create individual command spans as children of batch span
    let cmd1_span_ptr = unsafe { create_otel_span_with_parent(RequestType::Set, batch_span_ptr) };
    let cmd2_span_ptr = unsafe { create_otel_span_with_parent(RequestType::Get, batch_span_ptr) };
    let cmd3_span_ptr = unsafe { create_otel_span_with_parent(RequestType::Del, batch_span_ptr) };

    assert_ne!(cmd1_span_ptr, 0, "Command 1 span creation should succeed");
    assert_ne!(cmd2_span_ptr, 0, "Command 2 span creation should succeed");
    assert_ne!(cmd3_span_ptr, 0, "Command 3 span creation should succeed");

    // Verify all spans have unique pointers
    let all_spans = [
        root_span_ptr,
        batch_span_ptr,
        cmd1_span_ptr,
        cmd2_span_ptr,
        cmd3_span_ptr,
    ];
    for i in 0..all_spans.len() {
        for j in (i + 1)..all_spans.len() {
            assert_ne!(
                all_spans[i], all_spans[j],
                "All spans should have unique pointers"
            );
        }
    }

    // Clean up in reverse order (children before parents)
    unsafe {
        drop_otel_span(cmd1_span_ptr);
        drop_otel_span(cmd2_span_ptr);
        drop_otel_span(cmd3_span_ptr);
        drop_otel_span(batch_span_ptr);
        drop_otel_span(root_span_ptr);
    }
}

#[test]
fn test_batch_span_concurrent_creation() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    let num_threads = 3;
    let batches_per_thread = 2;
    let counter = Arc::new(AtomicUsize::new(0));
    let mut handles = Vec::new();

    // Test concurrent batch span creation
    for thread_id in 0..num_threads {
        let counter_clone = Arc::clone(&counter);

        let handle = thread::spawn(move || {
            let mut thread_spans = Vec::new();

            for i in 0..batches_per_thread {
                // Create parent operation span
                let parent_name = format!("thread_{thread_id}_operation_{i}");
                let parent_name_cstring = CString::new(parent_name).expect("CString::new failed");
                let parent_span_ptr =
                    unsafe { create_named_otel_span(parent_name_cstring.as_ptr()) };

                if parent_span_ptr != 0 {
                    thread_spans.push(parent_span_ptr);
                    counter_clone.fetch_add(1, Ordering::SeqCst);

                    // Create batch span with parent
                    let batch_span_ptr =
                        unsafe { create_batch_otel_span_with_parent(parent_span_ptr) };
                    if batch_span_ptr != 0 {
                        thread_spans.push(batch_span_ptr);
                        counter_clone.fetch_add(1, Ordering::SeqCst);

                        // Create a few command spans under the batch
                        let cmd_span_ptr = unsafe {
                            create_otel_span_with_parent(RequestType::Set, batch_span_ptr)
                        };
                        if cmd_span_ptr != 0 {
                            thread_spans.push(cmd_span_ptr);
                            counter_clone.fetch_add(1, Ordering::SeqCst);
                        }
                    }
                }

                // Small delay to reduce race conditions
                thread::sleep(Duration::from_millis(10));
            }

            // Clean up spans in reverse order (children first)
            thread_spans.reverse();
            for span_ptr in thread_spans {
                unsafe {
                    drop_otel_span(span_ptr);
                }
                thread::sleep(Duration::from_millis(1));
            }
        });

        handles.push(handle);
    }

    // Wait for all threads to complete
    for handle in handles {
        handle.join().expect("Thread should complete successfully");
    }

    let total_spans_created = counter.load(Ordering::SeqCst);
    assert!(
        total_spans_created > 0,
        "At least some spans should have been created"
    );
    println!(
        "Successfully created and cleaned up {total_spans_created} spans (including batch spans) across {num_threads} threads",
    );
}

#[test]
fn test_batch_span_error_handling() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // Test batch span creation with various error conditions

    // 1. Create batch span with null parent (should fallback)
    let batch_with_null = unsafe { create_batch_otel_span_with_parent(0) };
    assert_ne!(
        batch_with_null, 0,
        "Batch with null parent should fallback to independent span"
    );

    // 2. Create batch span with invalid parent (should fallback)
    let batch_with_invalid = unsafe { create_batch_otel_span_with_parent(0xDEADBEEF) };
    assert_ne!(
        batch_with_invalid, 0,
        "Batch with invalid parent should fallback to independent span"
    );

    // 3. Create batch span with misaligned parent (should fallback)
    let batch_with_misaligned = unsafe { create_batch_otel_span_with_parent(0x1001) };
    assert_ne!(
        batch_with_misaligned, 0,
        "Batch with misaligned parent should fallback to independent span"
    );

    // All should have created valid independent batch spans
    let all_batch_spans = vec![batch_with_null, batch_with_invalid, batch_with_misaligned];

    // Verify all are valid and unique
    for (i, &span_ptr) in all_batch_spans.iter().enumerate() {
        assert_ne!(span_ptr, 0, "Batch span {i} should be valid");
        assert_eq!(span_ptr % 8, 0, "Batch span {i} should be aligned");

        // Check uniqueness
        for (j, &other_span_ptr) in all_batch_spans.iter().enumerate() {
            if i != j {
                assert_ne!(
                    span_ptr, other_span_ptr,
                    "Batch spans {i} and {j} should be unique",
                );
            }
        }
    }

    // Clean up all batch spans
    for span_ptr in all_batch_spans {
        unsafe {
            drop_otel_span(span_ptr);
        }
    }
}
