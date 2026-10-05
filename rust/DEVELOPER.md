# DEVELOPER guide — `valkey-glide`

## Prerequisites

- **Rust** — install via [rustup](https://rustup.rs)
- The crate depends on other in-repo crates via **path dependencies** (see the
  Crates & dependencies section below), so **a monorepo checkout is required** —
  it builds from a `valkey-io/valkey-glide` checkout where those crates sit
  alongside it (this crate lives under `rust/`). No network fetch is needed to
  resolve the dependencies.
- A `valkey-server` (or `redis-server`) binary for integration tests.
  The harness auto-discovers one on `PATH`; override with:

  ```bash
  export VALKEY_SERVER_PATH=/path/to/valkey-server
  ```

## Crates & dependencies

The `valkey-glide` crate depends on several internal crates:

| Package name        | Library name      | Directory                    | Depends on                                             |
| ------------------- | ----------------- | ---------------------------- | ------------------------------------------------------ |
| `valkey-glide`      | `glide`           | `rust/`                      | `glide-core`, `glide-core-engine`, `glide-logger`      |
| `glide-core`        | `glide_core`      | `glide-core/`                | `glide-core-engine`, `glide-telemetry`, `glide-logger` |
| `glide-core-engine` | `redis`           | `glide-core/redis-rs/redis/` | `glide-telemetry`, `glide-logger`                      |
| `glide-telemetry`   | `glide_telemetry` | `glide-telemetry/`           | `glide-logger`                                         |
| `glide-logger`      | `glide_logger`    | `glide-logger/`              | None                                                   |

The `glide-core-engine` crate is forked from redis-rs 0.25.2, so it uses the `redis` library name.

### Publishing and dual versioning

Cargo requires every dependency of a published crate to also be published, so
the internal crates it depends on must be published too. They are published
with `0.x` versions and marked as internal, consistent with Rust conventions.

In order to allow developers to build against the local source rather than a
released version, the internal dependencies are declared with **both** `path`
and `version` in `Cargo.toml`:

```toml
glide-core = { path = "../glide-core", version = "0.1.0" }
```

Cargo uses `path` when building locally, and `version` when the crate is resolved
from crates.io after publishing.

## Build

```bash
cargo build            # debug
cargo build --release  # optimized
```

## Test

```bash
cargo unit-tests         # unit tests
cargo doc-tests          # doctests
cargo integration-tests  # integration tests
cargo test               # all tests (unit, docs, and integration)
```

To run only some tests, pass a filter. A test runs if its full name (e.g.
`value::from_valkey_value_tests::from_owned_valkey_value_array`) contains the filter:

```bash
cargo unit-tests value::              # unit tests containing `value::`
cargo doc-tests cmd::Cmd              # doctests containing `cmd::Cmd`
cargo integration-tests get_missing   # integration tests containing `get_missing`
```

To run one integration test file, use `cargo test --test <file>`, with an
optional filter:

```bash
cargo test --test it_string               # every test in tests/it_string.rs
cargo test --test it_string get_missing   # only those containing `get_missing`
```

Integration tests each boot their own ephemeral server on a free port and tear it
down on drop. The test fails if no server binary is found (see
`VALKEY_SERVER_PATH` above) or the server cannot be started.

## Lint & format

Recommended checks before opening a PR:

```bash
cargo fmt --all -- --check
cargo clippy --all-features --all-targets -- -D warnings
cargo clippy --all-targets -- -D warnings
cargo deny --config ../deny.toml check
cargo doc --no-deps --document-private-items
```

## Layout

```text
src/
  lib.rs          crate root + public re-exports
  error.rs        GlideError (mirrors Python exceptions)
  cmd.rs          Cmd, GLIDE's owned command builder (cmd(), query_async)
  pipeline.rs     Pipeline, GLIDE's owned pipeline / transaction builder
  types.rs        typed reply types (ValueType, IntegerReplyOrNoOp)
  config/         client configuration -> glide_core ConnectionRequest
    common.rs     shared types + builder-setter macro + request lowering
    standalone.rs GlideClientConfiguration
    cluster.rs    GlideClusterClientConfiguration
  routes.rs       cluster routing (Route -> RoutingInfo)
  value.rs        ValkeyValue and typed conversions (RESP2 + RESP3)
  write.rs        ToValkeyArgs and ValkeyWrite argument encoding
  executor.rs     CommandExecutor seam + custom_command
  client/
    mod.rs        GlideClient / GlideClusterClient (async)
    pipeline.rs   pipeline dispatch + typed execution (PipelineExt::query_async)
  pipeline_options.rs  Pipeline execution options (exec)
  script.rs       Script (SHA-caching EVALSHA with EVAL fallback)
  telemetry.rs    OpenTelemetry config + init
  sync/
    mod.rs        blocking clients over a shared runtime
    pipeline.rs   pipeline dispatch + typed execution (sync::PipelineExt::query)
  mock_tests/     server-free encoding/decoding tests for the extensions
  parity_tests/   redis-rs signature-parity guard
    mod.rs        parser + comparison (the redis_parity_check test)
    redis_parity.json  cached redis-rs command-table snapshot
    differences.json   pinned deliberate differences
  test_utils.rs   shared unit-test helpers (assert_args)
  commands/
    core.rs       the command table, generating Cmd constructors, Pipeline methods,
                  and AsyncCommands / Commands and their typed counterparts
                  (including the scan methods)
    scan.rs       GLIDE-owned scan iterators (ScanIter, SyncScanIter)
    options.rs    option types shared across command families
    <family>.rs   extension traits (blanket impls over CommandExecutor)
tests/
  common/         shared harness (server, cluster, timeout, pubsub, macros)
  it_*.rs         per-family live tests (one file per command family)
```

## Adding a command

1. Pick the family module in `src/commands/`.
2. Add an `async fn` to that family's trait following the template in
   `string.rs`: build a `Cmd`, call `self.execute_command(cmd, None)`,
   convert with a `crate::value::*` helper.
3. Add an integration test in the family's `tests/it_<family>.rs` (use the
   `resp_test!` macro for RESP2/RESP3 coverage), and a server-free encoding test
   in `src/mock_tests/<family>.rs`.
4. `cargo test && cargo clippy --all-targets`.

## Extending value conversion

Because the client negotiates **RESP3** by default, replies may arrive as
`ValkeyValue::Map`, `ValkeyValue::Double`, `ValkeyValue::Boolean`, or
`ValkeyValue::VerbatimString`.
Prefer the helpers in `src/value.rs`, which already normalize these, and add new
shapes there rather than in individual commands.

## Maintaining the unified command table

The unified `AsyncCommands` / `Commands` traits and their typed counterparts
(`AsyncTypedCommands` / `TypedCommands`) are defined by the
**hand-maintained** command table in `src/commands/core.rs` (one
`implement_commands!` invocation; each `fn name<G: Bound>(args) -> (T) { body }`
entry expands to the async and blocking methods, the typed async and blocking
methods returning `T`, the pipeline method, and a `Cmd::<name>()` constructor).
Copy the return annotation from redis-rs: `-> (T)` for a concrete type, or
`-> Generic` to keep a caller-chosen `RV` in the typed traits too.

To add or change an entry, edit the table directly — then run the
signature-parity guard. It parses GLIDE's table (`src/commands/core.rs`)
and compares it against a committed snapshot of the redis-rs surface
(`src/parity_tests/redis_parity.json`).

```bash
cargo test --lib parity_tests
```

The snapshot is a **trusted baseline**: the guard does not re-verify it against
the redis-rs source on every run — it only rebuilds the snapshot when the file
is absent, fetching the upstream redis-rs sources for the targeted release tag
from GitHub (via `curl`). Normal runs are offline.

The targeted redis-rs version is `REDIS_RS_VERSION` in `src/parity_tests/mod.rs`
(currently `1.7.0`). Bumping the constant makes the committed snapshot's version
mismatch and the guard fail until the snapshot is regenerated (delete it and
re-run).

The guard is fail-closed: every redis-rs method must be implemented with a
matching signature (including the declared return type), and GLIDE's table
must not add methods. The only exceptions are the deliberate differences pinned
in `src/parity_tests/differences.json`, indexed by method name. Each entry has a
`reason` and the method as each side declares it, `redis` and `glide`, either of
which may be `null`:

- **Both set:** GLIDE declares a different signature. Usually redis-rs's
  return type cannot decode the reply GLIDE receives (e.g. `zpopmin`, whose
  reply glide-core normalizes to a map); `hset_multiple` differs by design (see
  `migration.md`).
- **Only `redis` set:** GLIDE does not implement the method yet.
- **Only `glide` set:** GLIDE adds a command-table method redis-rs does not
  have.

The guard allows exactly the pinned entries, and fails if one no longer holds
(either side's method changed, appeared or disappeared, or the two signatures
now match), so update or remove the entry when that happens. For example,
remove a method's entry once GLIDE implements it.
Commands beyond the redis-rs surface belong in the per-family extension traits
(`src/commands/<family>.rs`), not in the table.

<!-- TODO #6906: Document publishing to crates.io. -->
