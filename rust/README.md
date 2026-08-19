# Valkey GLIDE for Rust (`glide`)

[![CI](https://github.com/omerrubi-amzn/glide-rust/actions/workflows/ci.yml/badge.svg)](https://github.com/omerrubi-amzn/glide-rust/actions/workflows/ci.yml)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](./LICENSE)
[![Rust](https://img.shields.io/badge/rust-1.85%2B-orange.svg)](https://www.rust-lang.org)

A first-class, native **Rust** client for [Valkey](https://valkey.io) and Redis OSS,
built directly on the shared **`glide-core`** engine that powers the official
GLIDE clients for Python, Java, Node, and Go.

Because `glide-core` is itself written in Rust, this wrapper links to it
**directly** — no FFI, no socket bridge — making it the thinnest and fastest
GLIDE binding.

## Highlights

- **Async first** — `GlideClient` (standalone) and `GlideClusterClient` (cluster)
  built on Tokio.
- **Blocking API** — a `sync` layer mirrors the async surface for non-async code
  (enabled by the default `sync` feature).
- **Broad command coverage** — typed methods across strings, generic/key, hash,
  list, set, sorted-set, HyperLogLog, bitmap, geo, stream, scripting,
  connection- and server-management, plus batches/transactions.
- **`custom_command` escape hatch** — run *any* command (with optional cluster
  routing) even where a typed wrapper is not provided, guaranteeing 100%
  functional coverage.
- **Batching** — `pipe()` pipelines and `MULTI`/`EXEC` transactions, executed
  typed via `query_glide` (zero extra payload copies) or with GLIDE execution
  controls (`PipelineOptions`: per-call timeout and pipeline retry strategy)
  via `execute_pipeline`.
- **Dynamic authentication** — rotate the connection password at runtime with
  `update_connection_password`, or use **AWS IAM** auth (ElastiCache / MemoryDB)
  via `ServerCredentials::iam`.
- **Runtime Pub/Sub** — `subscribe`/`psubscribe`/`ssubscribe` (and the matching
  unsubscribes) in addition to connect-time subscriptions; messages arrive via
  `get_pubsub_message`.
- **OpenTelemetry** — export traces and metrics via the `glide::telemetry`
  module (gRPC / HTTP / file exporters).
- **Feature parity with the Python GLIDE wrapper** as the baseline for both the
  API surface and the test suite.

## Documentation

GLIDE concepts — cluster routing, [batching](https://glide.valkey.io/concepts/client-features/batch-commands/),
the [PubSub model](https://glide.valkey.io/concepts/client-features/pubsub-model/),
[multi-slot command handling](https://glide.valkey.io/concepts/client-features/multi-slot-command-handling/),
and [OpenTelemetry](https://glide.valkey.io/concepts/client-features/open-telemetry/) —
are shared with the official clients and documented at
[glide.valkey.io](https://glide.valkey.io); this client is built on the same core,
so those concepts apply here unchanged. For Rust-specific architecture see
[DESIGN.md](./DESIGN.md).

## Supported Engine Versions

The compatibility target matches GLIDE — see the
[Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions).
CI runs the full integration suite against **Valkey 8.1, 8.0, and 7.2** and
**Redis OSS 7.2**, on Linux (x86_64 and aarch64). Other platforms (e.g. macOS)
should work wherever `glide-core` builds, but are not exercised in CI.

## Installation

### Prerequisites

- **Rust 1.85+** (the crate and `glide-core` use edition 2024).
- **Network access on the first build** — the crate links `glide-core` and its
  vendored `redis-rs` as pinned **git** dependencies, which Cargo fetches
  automatically (no monorepo checkout needed; see [Status & publishing](#status--publishing)).
- A running **Valkey** (or Redis OSS) server to connect to — e.g.
  `valkey-server` locally, `docker run -p 6379:6379 valkey/valkey`, or an
  ElastiCache/MemoryDB endpoint.

### 1. Add the dependency

The crate is not yet on crates.io (see [Status & publishing](#status--publishing)),
so depend on it via git. The package is named `glide-rust` and the library is
imported as `glide`:

```toml
# Cargo.toml
[dependencies]
glide-rust = { git = "https://github.com/omerrubi-amzn/glide-rust", branch = "main" }
# Async runtime (the async client is built on Tokio):
tokio = { version = "1", features = ["rt-multi-thread", "macros"] }
```

Pin to a specific commit for reproducible builds with `rev = "<sha>"` instead of
`branch = "main"`.

### 2. Provide the required build-time environment (important)

The vendored `redis-rs` fork reads two variables **at compile time** (via
`env!`), so *any* crate that builds it — including yours — must define them, or
the build fails with `environment variable GLIDE_VERSION not defined`. Add a
`.cargo/config.toml` at your project (or workspace) root:

```toml
# .cargo/config.toml
[env]
GLIDE_NAME = "GlideRust"
GLIDE_VERSION = "0.2.0"   # set to the glide-rust version you pinned
# Optional: avoids an aws-lc-rs CPU-jitter-entropy connection-latency regression.
AWS_LC_SYS_NO_JITTER_ENTROPY = "1"
```

These identify the client library/version reported to the server on the
connection handshake (any non-empty strings work; keep `GLIDE_VERSION` in sync
with the `glide-rust` version you depend on so server-side client listings stay
meaningful).

### 3. Build

```bash
cargo build
```

The first build fetches and compiles `glide-core` and its dependency tree, so it
takes a few minutes; subsequent builds are incremental.

### Contributor setup

Cloning this repository to develop the client? See **[DEVELOPER.md](./DEVELOPER.md)**
for the full workflow (build, run the unit + live integration tests — which spawn
a `valkey-server`, set `VALKEY_SERVER_PATH` to point at your binary — lint,
coverage, and benchmarks).

## Quick start (async)

```rust,no_run
use glide::{AsyncCommands, GlideClient, GlideClientConfiguration};

#[tokio::main]
async fn main() -> glide::RedisResult<()> {
    let config = GlideClientConfiguration::with_address("localhost", 6379);
    let client = GlideClient::connect(config).await.expect("connect");

    client.set::<_, _, ()>("hello", "world").await?;
    let value: Option<String> = client.get("hello").await?;
    assert_eq!(value.as_deref(), Some("world"));
    Ok(())
}
```

## Quick start (sync)

```rust,no_run
use glide::sync::SyncGlideClient;
use glide::{Commands, GlideClientConfiguration};

fn main() -> glide::RedisResult<()> {
    let client = SyncGlideClient::connect(
        GlideClientConfiguration::with_address("localhost", 6379),
    ).expect("connect");
    client.set::<_, _, ()>("hello", "world")?;
    let value: Option<String> = client.get("hello")?;
    assert_eq!(value.as_deref(), Some("world"));
    Ok(())
}
```

## Cluster & routing

```rust,no_run
use glide::{GlideClusterClient, GlideClusterClientConfiguration, Route, CustomCommand};

# async fn demo() -> glide::Result<()> {
let client = GlideClusterClient::connect(
    GlideClusterClientConfiguration::with_address("localhost", 7000),
).await?;

// Broadcast PING to all primaries.
client.custom_command_with_route(&["PING"], Route::AllPrimaries).await?;
# Ok(()) }
```

See `DESIGN.md` for architecture, and `DEVELOPER.md` for how to
build, test, and benchmark.

## Migrating from redis-rs

GLIDE's command API is **source-compatible with the redis-rs fork
(v0.25.2, predating the upstream license change)**: method names, signatures,
and wire encoding match, so existing typed call sites compile unchanged with
`RedisResult` errors. Everything you need is re-exported from `glide`.

Every command is executed by glide-core (multiplexing, cluster routing,
reconnection, IAM auth), handed over **by value** on GLIDE's zero-extra-copy
path. Parity is deliberately a **command-surface** contract, not a
connection-plumbing one: the clients are *not* `redis` connection objects
(`ConnectionLike`), because that interop layer forced a full payload copy per
command. The migrations that follow from this are mechanical:

| redis-rs call site            | GLIDE call site                          |
|-------------------------------|------------------------------------------|
| `pipe()….query_async(&mut c)` | `pipe()….query_glide(&c)` (`PipelineExt`) |
| sync `pipe()….query(&mut c)`  | `pipe()….query_glide(&c)` (`sync::PipelineExt`) |
| `cmd("X")….query_async(&mut c)` | `c.glide_send(cmd)` (typed, by value)  |
| `con.scan_match(pat)` iterators | same call — GLIDE-owned iterator, same `next_item()` / `Iterator` shape |

```rust,no_run
use glide::{AsyncCommands, GlideClient, GlideClientConfiguration, PipelineExt, Script, pipe};

# async fn demo() -> glide::RedisResult<()> {
// Standard connection-URL semantics, including rediss:// and database selection:
let config = GlideClientConfiguration::from_url("redis://user:pass@localhost:6379/2")
    .expect("valid URL");
# let client = GlideClient::connect(config).await.unwrap();

// Typed commands, unchanged from redis-rs call sites:
client.set::<_, _, ()>("key", 42).await?;
let value: i64 = client.get("key").await?;

// Pipelines and transactions (zero extra payload copies):
let (a, b): (i64, i64) = pipe()
    .atomic()
    .incr("counter", 1)
    .incr("counter", 1)
    .query_glide(&client)
    .await?;

// Lua scripts with EVALSHA caching:
let script = Script::new("return tonumber(ARGV[1]) + 1");
let n: i64 = script.arg(41).invoke_async(&client).await?;
# Ok(()) }
```

Notes:
- `glide::AsyncCommands` / `glide::Commands` are GLIDE's command API.
  Extension traits (streams, geo, Search `FT.*`, `JSON.*`, hash field-TTL, …)
  cover the rest of the command surface; names never collide, so import both
  freely.
- Cluster: `GlideClusterClientConfiguration::from_urls([...])` accepts
  seed-node URLs; commands are routed automatically.
- Mutual TLS: `config.client_identity(cert_pem, key_pem)`.
- Raw commands: build a `redis::Cmd` and send it typed with
  `client.glide_send(cmd)` (or untyped with `glide_send_owned` /
  `custom_command`) — this replaces `cmd().query_async()`, without the
  connection-object copy.
- Accepted gaps: no Sentinel / unix sockets / async-std (unsupported by
  glide-core); Pub/Sub stays client-integrated by design; generic code
  bounded on the fork's `ConnectionLike`-based traits should re-bound on
  `glide::AsyncCommands` (performance-motivated deviation).

## Testing

The suite has three layers (all run in CI and are currently green):

- **Unit tests (server-free, ~260)** — pure logic with no server: config →
  `ConnectionRequest` lowering, route → `RoutingInfo` mapping, option/argument
  encoding, value conversion, and error mapping; plus a **command-family mock
  suite** that drives every typed command through an in-process executor to
  assert exact **request encoding** and **response decoding**.
- **Integration tests (live server, ~900 executions across 31 files)** — real
  round-trips against a spawned `valkey-server`, one `tests/it_<family>.rs` per
  command family with edge/error cases (wrong-type, missing key, bounds, expiry
  conditions), **parametrized over RESP2 and RESP3**, plus suites for batches,
  scan, pub/sub, auth, TLS, and a **native multi-shard cluster** harness. Each
  test boots its own ephemeral server and tears it down on drop; suites needing
  unavailable infra (cluster/TLS/auth) **skip gracefully** rather than fail.
- **Doctests** — the examples in this README and the API docs are compiled.

```bash
cargo test --lib     # fast unit + mock tests only (no server needed)
cargo test           # everything, incl. live integration tests + doctests
```

Integration tests auto-discover a `valkey-server`/`redis-server` on `PATH`; point
them at a specific binary with `VALKEY_SERVER_PATH=/path/to/valkey-server`. See
`DEVELOPER.md` for coverage and benchmarks.

## Status & publishing

This crate links `glide-core` and its vendored `redis-rs` via **git ("remote")
dependencies** pinned to a commit of the canonical `valkey-io/valkey-glide`
repository (see `DEVELOPER.md`). Consequences to be aware of:

- **Builds fetch the dependency automatically** — no local monorepo checkout is
  required; you just need network access to GitHub on the first build.
- **Not yet publishable to crates.io as-is** — `cargo publish` rejects **both**
  git and path dependencies, so the crate cannot be published while it links
  `glide-core` (and the vendored redis-rs fork) from git. The only route to
  crates.io is to **publish `glide-core` and the redis-rs fork to crates.io and
  switch these to versioned dependencies** (`glide-core = "x.y"`). A git
  dependency lets downstreams consume this crate straight from its repo, but does
  not itself enable a crates.io publish. This is an inherent consequence of the
  "link core directly, no FFI" design and is a deliberate release-time decision.
- `docs.rs` builds would likewise need the dependency strategy resolved first.

## Getting Help

- **GitHub Issues**: Check the [existing issues](https://github.com/omerrubi-amzn/glide-rust/issues)
  first; if your question or problem isn't covered, open a new issue.
- **Valkey Slack**: For questions about Valkey or GLIDE in general,
  [join the Valkey Slack](https://join.slack.com/t/valkey-oss-developer/shared_invite/zt-2nxs51chx-EB9hu9Qdch3GMfRcztTSkQ).

## Contributing

Contributions are welcome — bug reports, feature requests, and pull requests.
See the [Contributing Guidelines](./CONTRIBUTING.md) to get started and
[DEVELOPER.md](./DEVELOPER.md) for the development workflow.

## Security

To report a security vulnerability, please see the [Security Policy](./SECURITY.md).

## License

Licensed under the Apache License, Version 2.0. See the `LICENSE` file at the
repository root for the full text.
