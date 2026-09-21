# DESIGN — `glide-rust`

## Dependency strategy

The crate lives in the `valkey-io/valkey-glide` monorepo under `rust/` and
declares in-repo **path dependencies** on both `glide-core` and its *vendored*
`redis` (the redis-rs fork, v0.25.2 — predating the upstream license change),
which sit alongside it in the same tree (see the path dependencies in
[`Cargo.toml`](Cargo.toml)).

Because both resolve to the **same in-repo source**, Cargo unifies our
`redis::Cmd` / `redis::Value` with the exact types `glide-core` expects — no type
mismatch, no re-wrapping. Building the crate therefore requires a checkout of the
monorepo, and the path deps mean it is not yet publishable to crates.io (see the
README's *Status & publishing* section).

## Command dispatch — `CommandExecutor`

Every command goes into glide-core through the
[`CommandExecutor`](src/executor.rs) trait. It has one async method,
`execute_command(cmd, route)`, that returns `ValkeyResult<ValkeyValue>`.

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
- the `scan*` methods return GLIDE-owned iterators (`src/commands/scan.rs`)
  that yield `ValkeyResult<RV>` via `next_item()` / `Iterator` (unlike redis-rs,
  which yields the bare value and swallows mid-scan errors), each page
  dispatched by value.

Almost every method on [`AsyncCommands`](src/commands/core.rs) and
[`Commands`](src/commands/core.rs) stands for one Valkey command — `get`, `set`,
`incr`, and so on. The command table generates them all automatically.

Each client writes just one method of its own: `glide_send_owned` (or
`glide_send_owned_sync` on the blocking trait). It takes a finished command and
sends it to glide-core, and every generated method goes through it.

For a command the table does not cover, build the command yourself and run it
with `glide_send`, which returns the reply already decoded into the type you ask
for. `Cmd::query_async` does the same in redis-rs's calling style, so code moving
over from that crate keeps working.

Commands **beyond** that table live in GLIDE **extension traits**
(`src/commands/`): streams, geo, Search (`FT.*`), JSON, Pub/Sub, scripting/
functions, server & connection management, plus per-family extras (hash
field-TTL, `LCS`, `SINTERCARD`, `ZRANGESTORE`, `BITFIELD`, `SORT`,
`DUMP`/`RESTORE`, …). These keep rich concrete return types and never collide
with unified-trait names, so both can be imported together.

- **Arguments**: generic over `glide::ToValkeyArgs` — accepts `&str`, `String`,
  `&[u8]`, `Vec<u8>`, `Bytes`, integers, floats, slices, etc.
- **Returns**: the unified traits are generic over `glide::FromValkeyValue`
  (`let v: Option<String> = c.get(k).await?`). The extension traits return
  concrete typed results.

## Value conversion

The `value` module owns the reply and argument types. `ValkeyValue`
(`src/value.rs`) mirrors redis-rs's `Value` and covers all RESP2/RESP3 shapes.
Command dispatch converts the raw fork `redis::Value` to a `ValkeyValue` once,
through `ValkeyValue::from_redis`. `FromValkeyValue` then decodes a `ValkeyValue`
into concrete Rust types: numerics, `bool`, `String`, `Bytes`, `Option`, `Vec`,
tuples, maps, and sets. Its explicit per-type impls match redis-rs's
`FromRedisValue` semantics. `ToValkeyArgs` encodes Rust values into command
arguments. Downstream users can implement both traits for their own types.

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

GLIDE owns the pipeline type (`glide::Pipeline`, `src/pipeline.rs`): build with
`glide::pipe()` (add `.atomic()` for `MULTI`/`EXEC`), then either execute typed
via `PipelineExt::query_async` (async, mirrored in `sync::PipelineExt::query`,
blocking), or via `exec(&Pipeline, raise_on_error, &PipelineOptions)` when GLIDE
execution controls (per-call timeout, pipeline retry policy, cluster routing) are
needed. Dispatch goes through `pipeline::dispatch_pipeline`, which hands the built
pipeline to glide-core **by reference** (zero extra payload copies) —
`send_transaction` (atomic) or `send_pipeline` — and converts the reply to a
`ValkeyValue`. The two entry points then diverge: `exec` returns the raw
`Vec<ValkeyValue>` (aborted transaction → `[]`), while `query_async`/`query` honor
`.ignore()` markers, decode into `T`, and preserve an aborted transaction's `Nil`
(matching redis-rs). The clients are deliberately **not** `redis` connection
objects, so there is no `ConnectionLike` bridge.
