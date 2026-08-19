# Contributing to Valkey GLIDE for Rust

Thanks for your interest in contributing! This crate is the native Rust client
(`glide`) built directly on the shared `glide-core` engine. It is part of the
wider [valkey-io/valkey-glide](https://github.com/valkey-io/valkey-glide)
project — please also read that repository's
[CONTRIBUTING.md](https://github.com/valkey-io/valkey-glide/blob/main/CONTRIBUTING.md)
and [Code of Conduct](https://github.com/valkey-io/valkey-glide/blob/main/CONTRIBUTING.md#code-of-conduct).

## Development setup

This crate links `glide-core` and its vendored `redis-rs` via **path
dependencies** to a sibling `valkey-glide` monorepo checkout. See
[`DEVELOPER.md`](DEVELOPER.md) for the expected layout and required build
environment (the `.cargo/config.toml` env vars).

You also need a `valkey-server` (or `redis-server`) binary for the integration
tests. Point the harness at it with:

```bash
export VALKEY_SERVER_PATH=/path/to/valkey-server
```

## Before opening a PR

Run the same gates CI runs:

```bash
cargo fmt --all
cargo clippy --all-features --all-targets -- -D warnings
cargo clippy --all-targets -- -D warnings          # also lint without features
cargo test                                          # unit + doc + live integration
cargo doc --no-deps --document-private-items
cargo deny check                                    # advisories/licenses/bans/sources
```

## Guidelines

- Keep changes focused and covered by tests (a server-free mock test in
  `tests/mock_commands/` and/or a live test in `tests/it_*.rs`).
- Follow the existing idioms: build a `redis::Cmd`, dispatch through
  `execute_command`, and convert replies with the `crate::value::*` helpers.
- Update `DESIGN.md` / `DEVELOPER.md` when you change architecture or workflow.
- The crate carries the Apache-2.0 SPDX header on every source file; keep it.

## License

By contributing, you agree that your contributions will be licensed under the
[Apache-2.0](LICENSE) license.
