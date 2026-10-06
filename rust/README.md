# Valkey GLIDE for Rust (`glide`)

[![crates.io](https://img.shields.io/crates/v/valkey-glide.svg)](https://crates.io/crates/valkey-glide)
[![docs.rs](https://img.shields.io/docsrs/valkey-glide)](https://docs.rs/valkey-glide)
[![CI](https://github.com/valkey-io/valkey-glide/actions/workflows/rust.yml/badge.svg)](https://github.com/valkey-io/valkey-glide/actions/workflows/rust.yml)
[![Rust](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fvalkey-io%2Fvalkey-glide%2Fmain%2Frust%2FCargo.toml&query=%24.package%5B%27rust-version%27%5D&label=rust&suffix=%2B&color=orange)](https://www.rust-lang.org)

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
  typed via `query_async` (zero extra payload copies) or with GLIDE execution
  controls (`PipelineOptions`: per-call timeout and pipeline retry strategy)
  via `exec`.
- **Dynamic authentication** — rotate the connection password at runtime with
  `update_connection_password`, or use **AWS IAM** auth (ElastiCache / MemoryDB)
  via `ServerCredentials::iam`.
- **Runtime Pub/Sub** — `subscribe`/`psubscribe`/`ssubscribe` (and the matching
  unsubscribes) in addition to connect-time subscriptions; messages arrive via
  `get_pubsub_message`.
- **OpenTelemetry** — export traces and metrics via the `glide::telemetry`
  module (gRPC / HTTP / file exporters).
- **Feature parity with the Python GLIDE wrapper** as the baseline for the API
  surface.

## Documentation

GLIDE concepts — cluster routing, [batching](https://glide.valkey.io/concepts/client-features/batch-commands/),
the [PubSub model](https://glide.valkey.io/concepts/client-features/pubsub-model/),
[multi-slot command handling](https://glide.valkey.io/concepts/client-features/multi-slot-command-handling/),
and [OpenTelemetry](https://glide.valkey.io/concepts/client-features/open-telemetry/) —
are shared with the official clients and documented at
[glide.valkey.io](https://glide.valkey.io); this client is built on the same core,
so those concepts apply here unchanged. For Rust-specific architecture see
[DESIGN.md](https://github.com/valkey-io/valkey-glide/blob/main/rust/DESIGN.md).

## Supported Engine Versions

The compatibility target matches GLIDE — see the
[Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions).
It is tested on Linux (x86_64 and aarch64). Other platforms (e.g. macOS)
should work wherever `glide-core` builds, but are not tested yet.

## Installation

### Prerequisites

- **Rust** 1.94.1 or later — install via [rustup](https://rustup.rs)
- A running **Valkey** (or Redis OSS) server to connect to — e.g.
  `valkey-server` locally, `docker run -p 6379:6379 valkey/valkey`, or an
  ElastiCache/MemoryDB endpoint.

### Add the dependency

The package is named `valkey-glide` and the library is imported as `glide`.
The async client is built on Tokio, so you will also need a Tokio runtime:

```bash
cargo add valkey-glide
cargo add tokio --features rt-multi-thread,macros
```

The first build compiles `glide-core` and its dependency tree, so it takes a
few minutes; subsequent builds are incremental.

## Quick start (async)

```rust,no_run
use glide::{AsyncCommands, GlideClient, GlideClientConfiguration};

#[tokio::main]
async fn main() -> glide::ValkeyResult<()> {
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

fn main() -> glide::ValkeyResult<()> {
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

# async fn demo() -> glide::ValkeyResult<()> {
let client = GlideClusterClient::connect(
    GlideClusterClientConfiguration::with_address("localhost", 7000),
).await?;

// Broadcast PING to all primaries.
client.custom_command_with_route(&["PING"], Route::AllPrimaries).await?;
# Ok(()) }
```

## Migrating from redis-rs

GLIDE's command API **mirrors redis-rs 1.7.0**, so most call sites migrate with
only import and type-name changes. See
**[migration.md](https://github.com/valkey-io/valkey-glide/blob/main/rust/migration.md)**
for more details.

## Getting Help

- **GitHub Issues**: Check the [existing issues](https://github.com/valkey-io/valkey-glide/issues)
  first; if your question or problem isn't covered, open a new issue.
- **Valkey Slack**: For questions about Valkey or GLIDE in general,
  [join the Valkey Slack](https://join.slack.com/t/valkey-oss-developer/shared_invite/zt-2nxs51chx-EB9hu9Qdch3GMfRcztTSkQ).

## Contributing

Contributions are welcome — bug reports, feature requests, and pull requests.
See the [Contributing Guidelines](https://github.com/valkey-io/valkey-glide/blob/main/CONTRIBUTING.md)
to get started and [DEVELOPER.md](https://github.com/valkey-io/valkey-glide/blob/main/rust/DEVELOPER.md)
for the development workflow.

## License

Licensed under the Apache License, Version 2.0. See the
[`LICENSE`](https://github.com/valkey-io/valkey-glide/blob/main/LICENSE)
for the full text.
