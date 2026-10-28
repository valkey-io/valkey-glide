# glide_ffi

## Overview

The glide_ffi crate provides a C-compatible Foreign Function Interface (FFI) for interacting with `glide-core`. It serves as a bridge for internal wrappers written in other languages, such as Go, to integrate seamlessly with `glide-core`.

## Structure
• src/lib.rs: Defines the FFI interface.
• src/rdma_ffi.rs: The RDMA functions, compiled in with the `rdma` feature.
• cbindgen.toml: Configuration for generating the C header file (lib.h).
• Cargo.toml: Manages dependencies, including glide-core.

## Building the Library

To build the FFI library:
```bash
cargo build --release
```
To run tests:
```bash
cargo test
```

## RDMA

RDMA is opt-in at build time through the `rdma` feature, which builds in `glide-core`
and `glide-rdma`'s RDMA support:

```bash
cargo build --release --features rdma
```

Without the feature, the RDMA functions still exist but registering and transferring fail
with a message saying RDMA was not compiled in and `rdma_available()` returns false.

The feature does not link libfabric. `libfabric` 2.4 or newer is loaded when a client
first opens a fabric, so it only has to be installed where the library runs. Set
`GLIDE_LIBFABRIC_PATH` to the library file to use a copy the default search does not
find. On Linux the search tries the EFA installer's copy under `/opt/amazon/efa` first.
`rdma_usable()` says whether a compatible libfabric loads.

The RDMA functions take synchronous clients only, and a pool refuses a connection
request with an RDMA configuration.

`cargo test` runs the RDMA tests that need no feature. The rest need libfabric
installed, and the ones in `tests/ffi_client_tests.rs` also need `valkey-server` on
`PATH`. To run all of them:

```bash
cargo test --features rdma
```

## Generating the C Header File

```bash
cargo install --locked cbindgen@0.29.4
cbindgen --config cbindgen.toml --crate glide-ffi --output lib.h --lang c
```

## Running Linters and Formatting

```bash
rustup component add clippy rustfmt
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --manifest-path ./Cargo.toml --all
```
