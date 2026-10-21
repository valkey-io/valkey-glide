# AGENTS: Native Rust Client Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively with the native Valkey GLIDE Rust client. It builds on [../AGENTS.md](../AGENTS.md) (repo-wide conventions, DCO, commit format, cross-crate Rust lint gate) — read that first.

## Repository Overview

This is the native **Rust** client binding for Valkey GLIDE: package `valkey-glide` (crates.io), lib name `glide`. Released as a **preview**. Unlike the Java/Python/Node/Go bindings, it links `glide-core` **directly as a Rust dependency** — no FFI layer, no socket bridge — making it the thinnest and fastest binding.

**Primary Language:** Rust
**Build System:** Cargo
**Architecture:** Thin async wrapper over `glide-core`, with an optional blocking `sync` layer on top

**Key Components:**
- `src/client/` — `GlideClient` (standalone), `GlideClusterClient` (cluster)
- `src/sync/` — blocking API mirroring the async surface (gated by the default `sync` feature; mirrors Python's `glide-sync`)
- `src/commands/`, `src/cmd.rs` — typed command surface (`src/commands/core.rs` holds the macro `implement_commands!` table)
- `src/value.rs` — RESP2/RESP3 conversion helpers (add new response shapes here)
- `src/pipeline.rs`, `src/pipeline_options.rs` — `pipe()` pipelines and MULTI/EXEC transactions
- `src/telemetry.rs` — OpenTelemetry hooks (telemetry types come via `glide-core`, not a direct `glide-telemetry` dependency)
- `tests/it_*.rs` — integration tests, one file per command/concern area (e.g. `it_hash.rs`, `it_cluster.rs`, `it_tls.rs`); each self-boots its own ephemeral server (see Build and Test Rules)
- `src/mock_tests/`, `src/parity_tests/` (directories, `#[cfg(test)]`) — internal unit/parity tests, not under `tests/`

**Parity guard (the key gotcha):** the hand-maintained command table in `src/commands/core.rs` is checked fail-closed against a committed snapshot (`src/parity_tests/`, pinned to a redis-rs version) by `cargo test --lib`. Adding/changing a command means updating `src/parity_tests/differences.json`; bumping the pinned version fails the guard until the snapshot is regenerated. See DEVELOPER.md "Maintaining the unified command table".

## Architecture Quick Facts

**Dependencies:** this crate depends **directly** on `glide-core`, `glide-core-engine` (the vendored redis-rs fork, lib name `redis`, at `glide-core/redis-rs/redis/`), and `glide-logger` — **not** on `glide-telemetry` (that reaches it transitively through `glide-core`). See [../glide-core/AGENTS.md](../glide-core/AGENTS.md) for the full crate dependency table.
**Safety:** `#![forbid(unsafe_code)]` and `#![deny(missing_docs)]` at the crate root (`src/lib.rs`) — every public item needs a doc comment, and `unsafe` is categorically disallowed in this crate (the core/FFI layers carry any unsafe code).
**Extra lint hardening** (`Cargo.toml` `[lints.clippy]`, mirroring glide-core's async-safety posture): `await_holding_lock = "deny"` (never hold a lock guard across an `.await`), `mutex_atomic = "warn"` (prefer atomics over `Mutex<bool>`/`Mutex<integer>`).
**Features:** `sync` is default-on and adds the blocking `src/sync/` module. (No lint pass uses `--no-default-features`, so the async-only surface is not separately linted in CI.)
**Doctests:** both `README.md` (via `#![doc = include_str!("../README.md")]`) and `migration.md` (via a `#[cfg(doctest)]` `MigrationDoctests` hook) are compiled as doctests — editing code examples in either can break `cargo doc-tests`.

## Build and Test Rules (Agents)

<!-- These commands are intentionally mirrored in rust/DEVELOPER.md (human guide). Keep both in sync when the build/test/lint commands change. -->

```bash
cd rust

# Build
cargo build            # debug
cargo build --release  # optimized

# Test — custom cargo aliases defined in rust/.cargo/config.toml
cargo unit-tests         # alias for: cargo test --lib        (no server needed; includes the parity guard)
cargo doc-tests          # alias for: cargo test --doc        (no server; compiles README.md + migration.md examples)
cargo integration-tests  # alias for: cargo test --test it_*  (self-boots an ephemeral server per harness; needs valkey-server/redis-server + python3 + a *-cli on PATH)

# Run a single integration test. matrix_test! expands one source test into
# standalone_resp2/resp3 + cluster_resp2/resp3, so filter on the expansion:
cargo test --test it_hash
cargo test --test it_hash hset::standalone_resp3

# Lint (same gate as every Rust crate in this repo — see ../AGENTS.md)
cargo fmt --all -- --check
cargo clippy --all-features --all-targets -- -D warnings
cargo clippy --all-targets -- -D warnings   # second pass (NOT no-default-features — sync is default-on)
cargo install --locked cargo-deny && cargo deny --config ../deny.toml check
RUSTDOCFLAGS="-Dwarnings" cargo doc --no-deps --document-private-items

# Format (mutating, not just check)
cargo fmt --all
```

CI (`.github/workflows/rust.yml`) pins the toolchain from `rust-version` in `Cargo.toml` (currently `1.94.1` — this crate's MSRV; `glide-core`/`ffi`/`glide-telemetry`/`glide-logger` don't pin one yet, but the redis-rs fork pins `1.71` — see `../glide-core/AGENTS.md`) and runs the three aliases with `--locked` (`cargo unit-tests --locked`, etc.) on x64 and arm64 Linux against a Valkey and a Redis engine. Run the same three aliases locally. Because CI uses `--locked`, a dependency edit **must** ship an updated `rust/Cargo.lock` or CI fails to resolve.

### IAM Config Tests

This crate's IAM coverage is env-var-free config-lowering unit tests in `src/config/tests.rs` (run by `cargo unit-tests`) — no AWS credentials or network needed. The credentialed IAM *integration* tests and the DNS tests live in `glide-core` and run as part of **its** suite, not this crate's (cargo does not run a dependency's tests); see [../glide-core/AGENTS.md](../glide-core/AGENTS.md) if you're working there.

## Contribution Requirements

DCO signoff and Conventional Commits are required repo-wide — see [../AGENTS.md](../AGENTS.md#contribution-requirements) for the exact commands. Use the `rust` scope, e.g. `feat(rust): add CLIENT NO-TOUCH support`.

## Guardrails & Policies

### Generated Outputs (Never Commit)
- `target/` — Cargo build artifacts

(Do commit `rust/Cargo.lock` changes that result from your own dependency edits — CI runs the tests with `--locked`, so an out-of-date lockfile fails resolution.)

### Rust-Specific Rules
- Do not add `unsafe` code to this crate — `#![forbid(unsafe_code)]` makes it a hard compile error. If a change genuinely needs `unsafe`, it belongs in `glide-core` or `ffi/`, not here.
- Every public item needs a doc comment (`#![deny(missing_docs)]` is a hard compile error, not just a lint warning).
- This crate's internal deps (`glide-core`, `glide-core-engine`, `glide-logger`) are declared with **both** `path` and `version` in `Cargo.toml` (path wins locally, version once published) — see `../glide-core/AGENTS.md` for why; keep both in sync when bumping internal crate versions.
- A monorepo checkout is required to build: the path dependencies resolve to sibling directories (`../glide-core`, etc.), not to crates.io, when building from source.

## Project Structure (Essential)

```text
rust/
├── src/
│   ├── lib.rs               # Crate root: #![forbid(unsafe_code)], module list, README+migration doctest hooks
│   ├── client/              # GlideClient / GlideClusterClient (mod.rs, pipeline.rs)
│   ├── sync/                # Blocking API (feature = "sync", default-on)
│   ├── commands/, cmd.rs    # Typed command surface (commands/core.rs = macro command table)
│   ├── value.rs             # RESP2/RESP3 conversion helpers
│   ├── config/, error.rs, executor.rs, routes.rs, script.rs, types.rs, write.rs
│   ├── pipeline.rs, pipeline_options.rs, telemetry.rs
│   ├── mock_tests/, parity_tests/  # #[cfg(test)] directories — not integration tests
│   └── ...
├── tests/it_*.rs            # Integration tests (self-boot an ephemeral server)
├── Cargo.toml               # rust-version = "1.94.1" (MSRV), features, lint overrides
├── .cargo/config.toml       # unit-tests / doc-tests / integration-tests aliases
├── README.md                # Crate docs, included verbatim into lib.rs via include_str!
├── DEVELOPER.md             # Human setup, crate dependency table, publishing/dual-versioning
├── migration.md             # Migrating from other clients; compiled as doctests
└── DESIGN.md                # Design rationale
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `cargo build --release` succeeds
- [ ] Unit + doc tests pass: `cargo unit-tests` and `cargo doc-tests` succeed
- [ ] Integration tests pass: `cargo integration-tests` succeeds (server running — see ../AGENTS.md)
- [ ] Lint passes: `cargo fmt --all -- --check`, both `cargo clippy` invocations (with and without `--all-features`), `cargo deny check`, `RUSTDOCFLAGS="-Dwarnings" cargo doc --no-deps --document-private-items`
- [ ] No `unsafe` introduced (`#![forbid(unsafe_code)]` will fail the build anyway, but check before pushing)
- [ ] New public items have doc comments (`#![deny(missing_docs)]`)
- [ ] DCO signoff present; conventional commit with `rust` scope

## Quick Facts for Reasoners

**Package:** `valkey-glide` on crates.io (lib name `glide`) | **Status:** preview
**MSRV:** 1.94.1 (`rust-version` in `Cargo.toml`)
**API Style:** async-first (`GlideClient`/`GlideClusterClient`), optional blocking `sync` layer (default feature)
**Links to core:** directly as a Rust dependency — no FFI, no socket bridge
**Safety:** `#![forbid(unsafe_code)]`, `#![deny(missing_docs)]`
**Test aliases:** `cargo unit-tests` / `doc-tests` / `integration-tests` (`rust/.cargo/config.toml`)

## If You Need More

- **Developer Guide:** [DEVELOPER.md](./DEVELOPER.md) — crate dependency table, dual path+version dependency pattern, publishing
- **Migration Guide:** [migration.md](./migration.md)
- **Design Rationale:** [DESIGN.md](./DESIGN.md)
- **Core:** [../glide-core/AGENTS.md](../glide-core/AGENTS.md)
- **Repo-wide conventions:** [../AGENTS.md](../AGENTS.md)
