# DEVELOPER guide — `glide-rust`

## Prerequisites
- Rust 1.85+ (edition 2024; developed on 1.95). No MSRV is declared, matching the
  upstream valkey-glide Rust crates.
- The crate depends on `glide-core` and its vendored `redis-rs` via **git
  ("remote") dependencies** pinned to a specific commit of
  `github.com/valkey-io/valkey-glide` in `Cargo.toml`, so Cargo fetches them
  automatically — **no local monorepo checkout is required**. You do need network
  access to GitHub on the first build (Cargo caches it afterwards). To build
  against a different revision, update the `rev = "..."` in `Cargo.toml`.
- A `valkey-server` (or `redis-server`) binary for integration tests / benches.
  The harness auto-discovers one on `PATH`; override with:
  ```bash
  export VALKEY_SERVER_PATH=/path/to/valkey-server
  ```

## Required build environment
The vendored `redis-rs` reads two variables at **compile time** (via `env!`).
They are provided by `.cargo/config.toml` in this repo, so a plain `cargo build`
works out of the box:
```toml
[env]
GLIDE_NAME = { value = "GlideRust", force = true }
GLIDE_VERSION = "unknown"   # override at build time: `GLIDE_VERSION=1.2.3 cargo build`
AWS_LC_SYS_NO_JITTER_ENTROPY = "1"
```

## Build
```bash
cargo build            # debug
cargo build --release  # optimized
```

## Test
```bash
cargo test --lib              # fast, pure unit tests (no server)
cargo test --test it_string   # a single live integration suite (spawns valkey-server)
cargo test                    # everything, incl. doctests
```
Integration tests each boot their own ephemeral server on a free port and tear it
down on drop. When no server binary is found, they print `SKIP` and pass.

## Lint & format
```bash
cargo clippy --all-targets
cargo fmt
```

## Benchmarks
```bash
cargo bench
```
Prints a manual throughput probe (ops/sec at several concurrency levels) and runs
Criterion latency benchmarks for `SET`/`GET`/`INCR`.

## Layout
```
src/
  lib.rs          crate root + public re-exports
  error.rs        GlideError (mirrors Python exceptions)
  config/         client configuration -> glide_core ConnectionRequest
    common.rs     shared types + builder-setter macro + request lowering
    standalone.rs GlideClientConfiguration
    cluster.rs    GlideClusterClientConfiguration
  routes.rs       cluster routing (Route -> RoutingInfo)
  value.rs        redis::Value -> typed Rust conversions (RESP2 + RESP3)
  executor.rs     CommandExecutor seam + custom_command
  client/
    mod.rs        GlideClient / GlideClusterClient (async)
    connection.rs typed Pipeline execution (PipelineExt::query_glide)
  pipeline_options.rs  Pipeline execution options (execute_pipeline)
  script.rs       Script (SHA-caching EVALSHA with EVAL fallback)
  telemetry.rs    OpenTelemetry config + init
  sync/mod.rs     blocking clients over a shared runtime
  commands/
    core.rs       the unified command table (AsyncCommands / Commands)
    scan.rs       GLIDE-owned scan iterators
    <family>.rs   extension traits (blanket impls over CommandExecutor)
tests/
  common/         shared harness (server, cluster, timeout, pubsub, macros)
  mock_commands/  server-free encoding/decoding tests for the extensions
  it_*.rs         per-family live tests (one file per command family)
benches/
  throughput.rs   latency + throughput
```

## Adding a command
1. Pick the family module in `src/commands/`.
2. Add an `async fn` to that family's trait following the template in
   `string.rs`: build a `redis::Cmd`, call `self.execute_command(cmd, None)`,
   convert with a `crate::value::*` helper.
3. Add an integration test in the family's `tests/it_<family>.rs` (use the
   `resp_test!` macro for RESP2/RESP3 coverage), and a server-free encoding test
   in `tests/mock_commands/<family>.rs`.
4. `cargo test && cargo clippy --all-targets`.

## Extending value conversion
Because the client negotiates **RESP3** by default, replies may arrive as
`Value::Map`, `Value::Double`, `Value::Boolean`, or `Value::VerbatimString`.
Prefer the helpers in `src/value.rs`, which already normalize these, and add new
shapes there rather than in individual commands.

## Maintaining the unified command table

The unified `AsyncCommands` / `Commands` traits are defined by the
**hand-maintained** command table in `src/commands/core.rs` (one
`implement_glide_commands!` invocation; each `fn name<G: Bound>(args);` entry
expands to both the async and the blocking method, delegating to the fork's
`Cmd::<name>()` constructor for identical wire encoding).

To add or change an entry, edit the table directly — then run the
signature-parity guard, which compares every entry against the vendored
redis-rs fork's `implement_commands!` table (names, generic order, argument
lists) and fails on any divergence. It also checks the `scan*` methods
(names, generics, and arguments must match the fork's macro definitions;
receivers and return types deliberately deviate — GLIDE-owned iterators on
the owned-send path, see `src/commands/scan.rs`):

```bash
cargo test --test it_parity_guard   # pure Rust; resolves the fork via cargo metadata
```

When the pinned fork rev is bumped, run the verifier to see what changed in
the fork's surface, update the table deliberately, and refresh the pinned rev
references (`NOTICE`). Commands beyond the fork's surface belong
in the per-family extension traits (`src/commands/<family>.rs`), not in the
table.
