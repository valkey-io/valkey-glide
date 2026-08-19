# DESIGN — `glide-rust`

## Dependency strategy
The crate declares **git ("remote") dependencies** on both `glide-core` and its
*vendored* `redis` (the redis-rs fork, v0.25.2 — predating the upstream
license change), pinned to the same commit of the canonical
`valkey-io/valkey-glide` repository:

```toml
glide-core = { git = "https://github.com/valkey-io/valkey-glide", rev = "..." }
redis      = { git = "https://github.com/valkey-io/valkey-glide", package = "redis", rev = "...", features = [
    "aio", "tokio-comp", "cluster", "cluster-async",
] }
```

Because both point at the **same git source + rev**, Cargo unifies our
`redis::Cmd` / `redis::Value` with the exact types `glide-core` expects — no type
mismatch, no re-wrapping — and consumers don't need a local monorepo checkout.
(Previously these were local `path` deps; the git form removes the
sibling-checkout requirement but still does not enable a crates.io publish.)

## Dispatch seam — `CommandExecutor`
```rust
#[async_trait]
pub trait CommandExecutor: Send + Sync {
    async fn execute_command(&self, cmd: Cmd, routing: Option<RoutingInfo>) -> Result<Value>;
}
```
`glide_core::client::Client` is `Clone` (internally `Arc<RwLock<..>>`), and
`send_command` needs `&mut self`. So `execute_command` clones the inner client
(cheap Arc clone) and calls `send_command` on the clone. This matches exactly
what every other wrapper does.

`GlideClient` (standalone) and `GlideClusterClient` (cluster) both hold a
`glide_core::client::Client` and implement `CommandExecutor`. The cluster client
additionally accepts a `Route` on command variants (via dedicated
`*_with_route` helpers and `custom_command` routing).

## Command surface

**GLIDE's command API** is source-compatible with the fork: `glide::AsyncCommands` (async)
and `glide::Commands` (blocking) are defined by a **hand-maintained command
table** (`src/commands/core.rs`, one `implement_glide_commands!` macro
invocation — the same declarative pattern the fork itself uses) mirroring the vendored
fork's `implement_commands!` table, enforced by a signature-parity guard
(`tests/it_parity_guard.rs`, implemented in `tests/parity/`).
Method names, generic parameter order, and
wire encoding match the fork exactly (methods delegate to its own
`Cmd::<name>()` constructors).

Parity is a **command-surface** contract, not a connection-plumbing one.
Deliberate deviations, all performance-motivated:
- methods take `&self` (the clients are cheaply cloneable handles) and hand
  the built command to glide-core **by value** via the `glide_send_owned`
  required method — the native zero-extra-copy path;
- the clients do **not** implement the `redis` crate's connection-object
  traits (`ConnectionLike`): that interop hands commands over by reference,
  which forced a full payload copy per command to bridge into glide-core's
  owned dispatch. Raw commands go through the typed `glide_send` escape
  hatch instead;
- the `scan*` methods return GLIDE-owned iterators (`src/commands/scan.rs`,
  same `next_item()` / `Iterator` call shape as redis-rs), each page
  dispatched by value.

```rust
pub trait AsyncCommands: Send + Sync + Sized {
    fn glide_send_owned<'a>(&'a self, cmd: Cmd) -> RedisFuture<'a, Value>;
    // typed escape hatch (replaces `cmd().query_async()`):
    fn glide_send<'a, RV: FromRedisValue>(&'a self, cmd: Cmd) -> RedisFuture<'a, RV> { /* provided */ }
    // + 151 table-defined methods with fork-exact signatures:
    fn get<'a, K: ToRedisArgs + Send + Sync + 'a, RV: FromRedisValue>(
        &'a self, key: K) -> RedisFuture<'a, RV> { /* Cmd::get -> glide_send_owned */ }
    // ...
}
```

Commands **beyond** that table live in GLIDE **extension traits**
(`src/commands/`): streams, geo, Search (`FT.*`), JSON, Pub/Sub, scripting/
functions, server & connection management, plus per-family extras (hash
field-TTL, `LCS`, `SINTERCARD`, `ZRANGESTORE`, `BITFIELD`, `SORT`,
`DUMP`/`RESTORE`, …). These keep rich concrete return types and never collide
with unified-trait names, so both can be imported together.

- **Arguments**: generic over `redis::ToRedisArgs` — accepts `&str`, `String`,
  `&[u8]`, `Vec<u8>`, integers, floats, slices, etc.
- **Returns**: unified traits are generic over `redis::FromRedisValue`
  (`let v: Option<String> = c.get(k).await?`); extension traits return
  concrete typed results.

## Value conversion
`value` module provides helpers: `Value -> Option<Bytes>`, `-> String`, `-> i64`,
`-> f64`, `-> bool`, `-> Vec<T>`, `-> HashMap<..>`. Built on `FromRedisValue`
where possible, with Glide-specific handling for `Value::Nil`, `Value::Okay`,
and RESP3 maps/doubles/booleans (glide-core already converts many types).

## Routing (cluster)
`routes::Route` enum → `redis::cluster_routing::RoutingInfo`:
`AllNodes`, `AllPrimaries`, `RandomNode`, `SlotKey{key,type}`,
`SlotId{id,type}`, `ByAddress{host,port}`.

## Errors
`GlideError` enum mirrors the Python exception hierarchy:
`Connection`, `Timeout`, `ExecAbort`, `Request`, `Closing`, `Configuration`,
`CircuitBreaker`. Converts from `redis::RedisError` (by `ErrorKind`) and
`glide_core::client::ConnectionError`.

## Sync layer
`sync::SyncGlideClient` / `sync::SyncGlideClusterClient` own an async client and a
shared multi-thread `tokio::runtime::Runtime` (lazily created, process-wide), and
expose the same methods with `block_on`. Mirrors Python `glide-sync`.

## Pipelines / Transactions
`redis::Pipeline` is used directly: build with `glide::pipe()` (add
`.atomic()` for `MULTI`/`EXEC`), execute typed via `PipelineExt::query_glide`
(async and, mirrored in `sync::PipelineExt`, blocking), or via
`execute_pipeline(&Pipeline, raise_on_error, &PipelineOptions)` when GLIDE
execution controls (per-call timeout, pipeline retry policy, cluster routing)
are needed. `query_glide` hands the built `&Pipeline` to glide-core by
reference (zero payload copies) and reuses the `redis` crate's typed decoding
(`.ignore()` markers, transaction unwrapping) through a crate-private adapter.
The client dispatches to glide-core's `send_transaction` (atomic) or
`send_pipeline`.
