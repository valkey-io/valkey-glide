// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Tests that the `CmdInfo` struct layout matches the declarations bindings use: the CFFI
//! `typedef struct CmdInfo` in `python/glide-shared/glide_shared/_glide_ffi.py`, and Go's
//! cbindgen-generated `lib.h`. A binding built against a different layout makes the core read
//! past the end of each `CmdInfo` it passes.

use glide_ffi::CmdInfo;
use std::mem;

#[test]
#[cfg(target_pointer_width = "64")]
fn cmd_info_layout() {
    assert_eq!(
        mem::size_of::<CmdInfo>(),
        40,
        "CmdInfo size changed! Update the CFFI declaration in \
         python/glide-shared/glide_shared/_glide_ffi.py and every binding that builds CmdInfo"
    );
    assert_eq!(mem::offset_of!(CmdInfo, args), 8);
    assert_eq!(mem::offset_of!(CmdInfo, arg_count), 16);
    assert_eq!(mem::offset_of!(CmdInfo, args_len), 24);
    assert_eq!(mem::offset_of!(CmdInfo, route_info), 32);
}
