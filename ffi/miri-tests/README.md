# FFI MIRI tests

These tests are for running MIRI over the FFI layer.

The `mock-redis`, `mock-glide-core`, `mock-telemetry`, `mock-tokio` and `mock-logger-core` crates
found here are used to mock out components that prevented MIRI tests from
running. This is because MIRI does not support calling foreign code, which
happens in a few places in `tokio` and Rust's `std` library.

## Running the tests

The tests can be run from the `miri-tests` directory, using this command:
`cargo miri test`
