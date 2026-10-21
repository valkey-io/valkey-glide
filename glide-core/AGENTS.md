# AGENTS: Rust Core & Shared Crates Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively on `glide-core` and the thin shared Rust crates it ships alongside (`ffi/`, `glide-telemetry/`, `glide-logger/`), plus the vendored `redis-rs` fork. It builds on [../AGENTS.md](../AGENTS.md) (repo-wide conventions, DCO, commit format) — read that first. For the native Rust **client** (the crate published as `valkey-glide`), see [../rust/AGENTS.md](../rust/AGENTS.md) instead — it depends on these crates but has its own build/test/lint cycle.

## Repository Overview

`glide-core` is the shared async Rust engine every language binding (Java, Python, Node, Go, Rust) is built on. None of these crates are directly end-user-facing; they are consumed via FFI (Go and both Python clients), JNI (Java), NAPI (Node), or a direct Rust dependency (the `rust/` client).

**Crate Dependency Table** (none share a Cargo workspace — each has its own `Cargo.toml`/`Cargo.lock`, resolved via path+version dependencies):

| Package | Lib name | Directory | Depends on |
|---|---|---|---|
| `glide-core` | `glide_core` | `glide-core/` | `glide-core-engine`, `glide-telemetry`, `glide-logger` |
| `glide-core-engine` | `redis` | `glide-core/redis-rs/redis/` | `glide-telemetry`, `glide-logger` |
| `glide-ffi` | — | `ffi/` | `glide-logger`, `glide-core-engine`, `glide-core` (used by `go/`, `python/glide-sync/`) |
| `glide-telemetry` | `glide_telemetry` | `glide-telemetry/` | `glide-logger` |
| `glide-logger` | `glide_logger` | `glide-logger/` | none |

`glide-core-engine` is a **vendored fork of [redis-rs](https://github.com/redis-rs/redis-rs) 0.25.2** (lib name stays `redis` for drop-in compatibility with the upstream API). Treat `glide-core/redis-rs/` as GLIDE-owned, not third-party-frozen: it's a real fork we patch, not a copy to leave alone — but check `glide-core/redis-rs/README.md`/history before diverging further from upstream behavior.

## Architecture Quick Facts

**Core Implementation:** `glide-core` holds the async client logic, cluster-aware routing, batching, and command dispatch. Every binding ultimately calls into it.
**RESP2/RESP3 Normalization:** `glide-core/src/client/value_conversion.rs` converts both wire protocols to one consistent shape so bindings never need protocol-version branching. New commands with divergent RESP2/RESP3 shapes add/reuse an `ExpectedReturnType` variant and implement the conversion in `convert_to_expected_type`.
**ffi/:** Builds as `staticlib` + `rlib` + `cdylib` (`[lib] crate-type` in `ffi/Cargo.toml`) — it's the one crate here meant to be linked into non-Rust binaries. Consumed by the Go (CGO) and both Python (CFFI) bindings; Java and Node do **not** go through `ffi/` (JNI and NAPI link `glide-core` more directly through their own Rust glue in `java/src/` and `node/rust-client/`).
**glide-telemetry:** OpenTelemetry span/metrics export, consumed by `glide-core`'s `telemetry` support and the native Rust client.
**glide-logger:** The only crate with zero internal dependencies; a shared logger used by every other crate including `ffi/`.
**MSRV / edition:** `glide-core`, `ffi`, `glide-telemetry`, `glide-logger` don't pin a `rust-version` yet (`# TODO #7175` in each) and are `edition = "2024"`. The redis-rs fork is different: it pins `rust-version = "1.71"` and `edition = "2021"` (and is BSD-3-Clause, not Apache-2.0 — matters for `cargo deny` and new-file headers in that tree). Don't assume a shared MSRV floor.

## Build and Test Rules (Agents)

<!-- These commands are intentionally mirrored in glide-core/DEVELOPER.md (human guide). Keep both in sync when the build/test/lint commands change. -->

All four crates share the same lint gate (`.github/workflows/lint-rust/action.yml`), run from the crate's own directory. The same gate also runs against `java/`, `node/rust-client/`, `python/glide-async/` (binding glue), `benchmarks/rust/`, and `rust/` (see `../AGENTS.md`). `cargo deny` uses the repo-root `deny.toml` (`--config ${GITHUB_WORKSPACE}/deny.toml` in CI; adjust the relative path to the repo root for the crate you're in — `../deny.toml` only works for a depth-1 crate):

```bash
cargo fmt --all -- --check
cargo clippy --all-features --all-targets -- -D warnings
cargo clippy --all-targets -- -D warnings   # second pass with default features (not --all-features)
cargo install --locked cargo-deny && cargo deny --config "$(git rev-parse --show-toplevel)/deny.toml" check
RUSTDOCFLAGS="-Dwarnings" cargo doc --no-deps --document-private-items
```

**`glide-core/redis-rs/redis/` (the vendored engine fork) uses a DIFFERENT, separate gate** (`.github/workflows/glide-core-engine.yml`), not the shared one above:

```bash
cd glide-core/redis-rs/redis
cargo fmt --all -- --check
cargo clippy -- -D warnings   # no --all-features/--all-targets here: tests/benches carry required-features (test_async, bench_cluster, …), so --all-targets would fail to compile without selecting them
cargo install --locked cargo-deny && cargo deny --config "$(git rev-parse --show-toplevel)/deny.toml" --exclude-dev check all
cargo audit                   # separate from cargo-deny; fails the job if any advisory is found
cargo install --locked cargo-machete && cargo machete   # unused-dependency check; fails if any found
```
No `cargo doc` lint step here. Build/test for this crate also run from `redis-rs/redis/src/` for build/test/doc-tests but from `redis-rs/redis/` for lint/bench/audit/machete — mind the working directory:
```bash
cd glide-core/redis-rs/redis/src && cargo build --release && cargo test --release && cargo test --doc
cd glide-core/redis-rs/redis && cargo bench
```
`fmt-glide-core-engine.yml` is a bare nightly wrapper around this same workflow (no additional matrix of its own) — not something you need to reproduce locally for a normal PR.

Per-crate build/test (exact invocations CI runs — do not substitute a bare `cargo test`):

**glide-core/** (`.github/workflows/glide-core.yml` — these three are exactly what CI runs; it does NOT run `cargo build --release` or `cargo bench` as gates):
```bash
cd glide-core
cargo test --features "proto,socket-layer,standalone_heartbeat"   # tests self-boot servers via utils/cluster_manager.py (needs valkey-server/redis-server + python3 on PATH)
cargo check --benches --all-features
cargo check --no-default-features
```
`tests/` build with `socket-layer` + `test-util` automatically (a dev-dependency on `glide-core` itself enables them), so you don't add those by hand. Opt into others per test file: `iam_tests` (gates the credentialed IAM integration tests — see below), `mock-pubsub`. Single test: `cargo test --features proto <name>`. Benchmarks run via the `test-benchmark` CI action against `benchmarks/rust/`, not `cargo bench` here; `benches/memory_benchmark.rs` uses `gungraun` (valgrind-based), the others Criterion.

**ffi/** (`.github/workflows/ffi.yml`):
```bash
cd ffi
cargo test
cd miri-tests && cargo miri test   # UB checks; needs the nightly toolchain pinned in ffi/miri-tests/rust-toolchain.toml
```

**glide-telemetry/** (`.github/workflows/glide-telemetry.yml`):
```bash
cd glide-telemetry
cargo test --all-features -- --test-threads=1
# --test-threads=1 required: see https://github.com/valkey-io/valkey-glide/issues/4057
```

**glide-logger/** (`.github/workflows/glide-logger.yml`):
```bash
cd glide-logger
cargo test --all-features -- --test-threads=1
# --test-threads=1 required: tests install a global logger and share one glide-logs/ directory, so they cannot run in parallel
```

### IAM Authentication Tests (glide-core)

There are two separate IAM test tiers in `glide-core/tests/test_client.rs`:

- **Local mock-IAM test** (not feature-gated, runs under `proto`): uses a local test server, no real endpoint. This is the one that proves the IAM flow (token generation, connect, refresh) without validating real credentials.
- **Credentialed IAM integration tests** (`#[cfg(feature = "iam_tests")]`): target a real ElastiCache/MemoryDB endpoint and `panic!` on DNS failure. They are gated by the **cargo feature**, not by env vars — without `--features iam_tests` they compile out entirely (`cargo test test_iam_authentication` reports "0 passed", which is not the same as passing). `iam_tests` runs in no workflow, so this tier never runs in CI.

```bash
# Run the gated integration tier (needs a real endpoint configured in the test):
cargo test --features iam_tests test_iam_authentication
```

### DNS Tests (glide-core)

Add to `/etc/hosts` (or Windows equivalent):
```text
127.0.0.1 valkey.glide.test.tls.com
127.0.0.1 valkey.glide.test.no_tls.com
::1 valkey.glide.test.tls.com
::1 valkey.glide.test.no_tls.com
```
Then `export VALKEY_GLIDE_DNS_TESTS_ENABLED=1`. Skipped automatically if unset.

### Timeout Watchdog Diagnostics

See [DEVELOPER.md](./DEVELOPER.md) for the full architecture; summary: a dedicated OS thread (independent of Tokio) fires a bare timeout signal, and `send_command` enriches it at fire time with cause classification, p99 latency, inflight trend, and RSS — logged via `log_warn` at the `"timeout_watchdog"` target. No action needed to enable it; it's automatic whenever `request_timeout` is configured.

## Contribution Requirements

DCO signoff and Conventional Commits are required repo-wide — see [../AGENTS.md](../AGENTS.md#contribution-requirements) for the exact commands. Scope by crate, e.g. `fix(core): ...` (or the directory name if ambiguous).

## Guardrails & Policies

### Generated Outputs (Never Commit)
- `target/` — Cargo build artifacts (all crates)
- `glide-core/dump.rdb` — leftover from local manual testing against a Valkey instance; don't commit a stray one
- `ffi/miri-tests/Cargo.lock` churn unrelated to your change (separate lockfile from `ffi/Cargo.lock`)

### Rust-Specific Rules
- `glide-core/redis-rs/` is a real fork, not vendored-and-frozen — changes are allowed, but check for upstream drift before diverging further.
- `ffi/` must stay FFI-safe at its public boundary (`#[unsafe(no_mangle)]` — the `unsafe(...)` wrapper is required because `ffi/` is edition 2024); it's linked into non-Rust binaries (Go, both Python clients) as `staticlib`/`cdylib`. `ffi/` has a `pool-support` feature (default-on) gating its pool surface (`ffi/src/pool_ffi.rs`); `ffi/miri-tests/` mirrors it.
- **Miri gotcha:** `ffi/miri-tests/` recompiles `ffi/src/` against mock crates (`mock-tokio`, `mock-glide-core`, …) because Miri can't call foreign code — so a new dependency or newly-used upstream API in `ffi/src/` breaks `cargo miri test` at compile time until a mock is updated (see `ffi/miri-tests/README.md`).
- **protoc prerequisite:** `glide-core/build.rs` runs protobuf codegen whenever `proto` is enabled, so a fresh box needs `protoc` on PATH or the build fails in `build.rs` with no obvious explanation (CI installs it via `install-protoc`).
- When adding a feature flag to `glide-core`, update both the `cargo check --no-default-features` expectation and the CI feature list (`proto,socket-layer,standalone_heartbeat`) if the new code path needs to be exercised in CI.
- `glide-telemetry`'s and `glide-logger`'s tests are NOT safely parallel (`--test-threads=1`) — don't "fix" a flaky-looking test here by just re-running; check whether it's actually a shared-global-state collision (logger install, shared `glide-logs/` dir).

## Project Structure (Essential)

```text
glide-core/
├── src/
│   ├── client/value_conversion.rs   # RESP2/RESP3 normalization
│   └── ...
├── redis-rs/                        # Vendored fork of redis-rs 0.25.2 (lib name: redis)
│   └── redis/
├── tests/                           # Integration tests (need a live server)
├── benches/                         # Criterion benchmarks
├── Cargo.toml                       # features: proto, socket-layer, standalone_heartbeat, iam_tests, mock-pubsub, test-util
└── DEVELOPER.md                     # Timeout watchdog, IAM/DNS test setup

ffi/
├── src/lib.rs                       # staticlib + rlib + cdylib — linked into Go (CGO), both Python clients (CFFI)
├── miri-tests/                      # cargo miri test (separate Cargo.lock, nightly toolchain)
└── tests/

glide-telemetry/
└── src/{lib,open_telemetry,metrics_exporter_file,span_exporter_file}.rs

glide-logger/
└── src/lib.rs                       # Zero internal deps; shared by every other Rust crate here
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `cargo build --release` in each touched crate
- [ ] Tests pass with the crate's documented feature flags and thread-count args (see above — a bare `cargo test` under-covers glide-core and can falsely "pass" glide-telemetry/glide-logger by accident of thread scheduling)
- [ ] Lint passes: `cargo fmt --all -- --check`, both `cargo clippy` invocations, `cargo deny check`, `cargo doc --no-deps --document-private-items` in each touched crate
- [ ] If you changed `ffi/src/`, also run `cd ffi/miri-tests && cargo miri test`
- [ ] No unreconciled drift introduced into `glide-core/redis-rs/` without checking upstream
- [ ] DCO signoff present; conventional commit format used

## Quick Facts for Reasoners

**Crates:** `glide-core` (`glide_core`), `glide-core-engine` (redis-rs fork, lib `redis`), `glide-ffi`, `glide-telemetry`, `glide-logger` — independent crates, no cargo workspace
**MSRV:** not yet pinned (`# TODO #7175` in each `Cargo.toml`) — unlike `rust/`, which pins 1.94.1
**Consumed by:** Go + both Python clients via `ffi/`; Node via `node/rust-client/`; Java via `java/src/`; the native Rust client directly
**RESP2/3 normalization:** `glide-core/src/client/value_conversion.rs`
**Test caveats:** `glide-core` needs feature flags (`proto,socket-layer,standalone_heartbeat`); `glide-telemetry`/`glide-logger` need `--test-threads=1`; `ffi/miri-tests/` runs under Miri (nightly)

## If You Need More

- **Developer Guide:** [DEVELOPER.md](./DEVELOPER.md) — timeout watchdog internals, IAM/DNS test setup
- **Native Rust Client:** [../rust/AGENTS.md](../rust/AGENTS.md)
- **Repo-wide conventions:** [../AGENTS.md](../AGENTS.md)
- **redis-rs upstream:** [redis-rs/redis-rs](https://github.com/redis-rs/redis-rs) (fork point: 0.25.2)
