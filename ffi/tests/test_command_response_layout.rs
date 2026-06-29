// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Tests that the `CommandResponse` struct layout matches the CFFI declarations
//! in `python/glide-shared/glide_shared/_glide_ffi.py` and the fast response
//! parser in `python/glide-shared/src/lib.rs`.
//!
//! If any of these tests fail after changing `CommandResponse`, update:
//! 1. The CFFI `typedef struct CommandResponse` in `python/glide-shared/glide_shared/_glide_ffi.py`
//! 2. The field offset reads in `python/glide-shared/src/lib.rs`
//! 3. The expected values in this test file

use glide_ffi::{CommandResponse, ResponseType};
use std::mem;

#[test]
fn command_response_size() {
    // On 64-bit platforms: 13 fields, mostly pointers and longs.
    // This test catches any accidental field additions or removals.
    let size = mem::size_of::<CommandResponse>();
    assert!(size > 0, "CommandResponse should have a non-zero size");
    // Record the expected size so changes are caught.
    // 64-bit with repr(C): verified empirically.
    #[cfg(target_pointer_width = "64")]
    assert_eq!(
        size, 104,
        "CommandResponse size changed! Update CFFI declarations in \
         python/glide-shared/glide_shared/_glide_ffi.py and the fast response \
         parser in python/glide-shared/src/lib.rs"
    );
}

#[test]
fn command_response_field_offsets() {
    // Verify each field is at the expected offset. If these change, the CFFI
    // declarations and fast response parser must be updated to match.
    #[cfg(target_pointer_width = "64")]
    {
        assert_eq!(
            mem::offset_of!(CommandResponse, response_type),
            0,
            "response_type offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, int_value),
            8,
            "int_value offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, float_value),
            16,
            "float_value offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, bool_value),
            24,
            "bool_value offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, string_value),
            32,
            "string_value offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, string_value_len),
            40,
            "string_value_len offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, array_value),
            48,
            "array_value offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, array_value_len),
            56,
            "array_value_len offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, map_key),
            64,
            "map_key offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, map_value),
            72,
            "map_value offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, sets_value),
            80,
            "sets_value offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, sets_value_len),
            88,
            "sets_value_len offset changed"
        );
        assert_eq!(
            mem::offset_of!(CommandResponse, arena_ptr),
            96,
            "arena_ptr offset changed"
        );
    }
}

#[test]
fn response_type_enum_values() {
    // The ResponseType enum values must match the CFFI typedef enum.
    assert_eq!(ResponseType::Null as u32, 0);
    assert_eq!(ResponseType::Int as u32, 1);
    assert_eq!(ResponseType::Float as u32, 2);
    assert_eq!(ResponseType::Bool as u32, 3);
    assert_eq!(ResponseType::String as u32, 4);
    assert_eq!(ResponseType::Array as u32, 5);
    assert_eq!(ResponseType::Map as u32, 6);
    assert_eq!(ResponseType::Sets as u32, 7);
    assert_eq!(ResponseType::Ok as u32, 8);
    assert_eq!(ResponseType::Error as u32, 9);
}

#[test]
fn response_type_size() {
    // ResponseType is repr(C) enum, should be 4 bytes (c_uint).
    assert_eq!(
        mem::size_of::<ResponseType>(),
        4,
        "ResponseType size changed"
    );
}
