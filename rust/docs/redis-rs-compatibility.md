# redis-rs compatibility

## Minimum version

Valkey GLIDE Rust client's minimum supported Rust version (MSRV) is
[1.94.1](https://releases.rs/docs/1.94.1/). This is required by the latest AWS
SDK dependencies, which are used for IAM authentication.

This is higher than
[redis-rs 1.7.0](https://github.com/redis-rs/redis-rs/releases/tag/redis-1.7.0),
which supports Rust [1.88.0](https://releases.rs/docs/1.88.0/).

## Edition

The Valkey GLIDE Rust client uses Rust edition 2024, matching redis-rs 1.7.0.
