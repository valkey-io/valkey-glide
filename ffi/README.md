# glide_ffi

## Overview

The glide_ffi crate provides a C-compatible Foreign Function Interface (FFI) for interacting with `glide-core`. It serves as a bridge for internal wrappers written in other languages, such as Go, to integrate seamlessly with `glide-core`.

## Structure
• src/lib.rs: Defines the FFI interface.
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

## Generating the C Header File

```bash
cargo install cbindgen
cbindgen --config cbindgen.toml --crate glide-ffi --output lib.h --lang c
```

## Running Linters and Formatting

```bash
rustup component add clippy rustfmt
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --manifest-path ./Cargo.toml --all
```
