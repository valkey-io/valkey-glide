# ofi-libfabric-sys

The official distribution of lightweight Rust bindings for Libfabric - a communication API for high-performance parallel and distributed applications, by the OFI Working Group.

### Motivation

Increasing number of HPC networking code is being written in Rust. Naturally, to
support Libfabric usage in Rust, there needs a proper Rust library that wraps
Libfabric APIs written in C. This practice is commonly referred as a Rust
binding / FFI (foreign function interface).

This library builds a lightweight Rust binding via bindgen. Lightweight, meaning
there's no additional abstraction on top of the automatically generated code via
bindgen, aside from the `wrapper.[ch]` which is strictly used to support
`static inline` functions to be properly bound, by introducing a new translation
unit upon compilation.

### Build

```
// Build. Needs a C compiler.
cargo build

// Regenerate `src/bindings.rs` from the vendored headers. Needs libclang.
cargo build --features regenerate-bindings
```

A program built from this crate alone does not link: see "Notes for
valkey-glide" below.

### How to use the library

Add the crate dependency under your Rust application's `Cargo.toml` file. Then;

```rust
use ofi_libfabric_sys::bindgen as ffi;
use std::ffi::CString;
use std::ptr;

fn test_get_info() {
    unsafe {
        // Configure hints.
        let hints = ffi::fi_allocinfo();
        assert_eq!(hints.is_null(), false);

        (*hints).caps = ffi::FI_MSG as u64;
        (*hints).mode = ffi::FI_CONTEXT;
        (*(*hints).ep_attr).type_ = ffi::fi_ep_type_FI_EP_RDM;
        (*(*hints).domain_attr).mr_mode = ffi::FI_MR_LOCAL as i32;
        let prov_name = CString::new("tcp").unwrap();
        (*(*hints).fabric_attr).prov_name = prov_name.into_raw() as *mut i8;

        // Get Fabric info based on the hints.
        let mut info_ptr = ptr::null_mut();
        let version = ffi::fi_version();
        let ret = ffi::fi_getinfo(
            version,
            ptr::null_mut(),
            ptr::null_mut(),
            0,
            hints,
            &mut info_ptr,
        );

        assert_eq!(ret, 0);

        // Free the info structure returned by fi_getinfo.
        if !info_ptr.is_null() {
            ffi::fi_freeinfo(info_ptr);
        }

        // Free the hints structure we allocated.
        ffi::fi_freeinfo(hints);
    }
}
```

### Files

- `build.rs`: The actual build script for the bindgen.
- `src/lib.rs`: The generated binding is copy-pasted programmatically and
  publicly exported under `bindgen` namespace.
- `wrapper.[ch]`: Wrapper source files that simply calls the static inline
  functions. This way, an isolated translation unit for each static inline
  function is made, for which the Rust bindgen is able to link against it.

---

## Notes for valkey-glide

This is a vendored copy of `bindings/rust` from
[ofiwg/libfabric](https://github.com/ofiwg/libfabric) at revision `6953579a5035`.
It differs from upstream in six ways, the first four so that a build of GLIDE
does not need libfabric installed and a shipped binary does not hard-link
libfabric on the runtime machine.

1. **Crate metadata is inlined** rather than inherited from libfabric's Cargo
   workspace, which is not vendored with it.

2. **libfabric is never linked.** Upstream emits `cargo:rustc-link-lib=fabric`
   which requires the runtime host to have libfabric even if they weren't
   going to use the `glide-rdma` feature. Here, `glide-rdma` defines libfabric
   symbols and resolves them at runtime by the dlopen shim in `glide-rdma`.
   This crate is only usable together with `glide-rdma`.

3. **libfabric's public headers are vendored under `include/`.** Upstream builds
   against whatever `pkg-config` finds on the build machine.

4. **The bindgen output is generated once and kept as `src/bindings.rs`.**
   One consequence to know about: some of the constants bindgen copies out are
   the build machine's, not the target's. The `FI_E*` codes below 256 are
   defined as the platform's own `errno` values, which differ (`FI_ENODATA` is
   61 on Linux and 96 on macOS), so the checked-in file carries whichever set
   belonged to the machine that last ran bindgen. Do not compare a libfabric
   return code against these; take the value from `libc` instead, which follows
   the target. libfabric's own codes, from 256 up (`FI_ETOOSMALL` and later),
   are the same everywhere.

5. **The `cargo:warning` lines listing the include paths are removed.** Upstream
   prints them on every build, and cargo repeats them for everyone who depends on
   the crate. They read as debug output left behind.

6. **Upstream's `asan` feature and unit tests are removed.**  The bindings are
   exercised through `glide-rdma`'s tests instead.

Together, (3) and (4) mean a release builder needs neither `libfabric-dev` nor
`libclang`.

### Regenerating the bindings

After changing anything under `include/`:

```
cargo build --features regenerate-bindings   # needs libclang
```

This rewrites `src/bindings.rs` in place. Commit the result. The API version it
describes — `FI_MAJOR_VERSION` and `FI_MINOR_VERSION` near the top — is the
version `glide-rdma` will refuse to run against anything older than, so a header
bump is a deliberate change to what the client supports.
