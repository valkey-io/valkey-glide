# Changelog

All notable changes to this crate are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## 0.2.0 (unreleased)

**Breaking — unified command API.** One command surface instead of two:

- `glide::AsyncCommands` / `glide::Commands` are now THE command API:
  GLIDE's own traits, source-compatible with redis-rs (names, generic order,
  and wire encoding match the vendored fork; existing redis-rs call sites
  compile unchanged), sent **by value** on the native zero-extra-copy path.
  Deliberate deviations: `&self` receivers; `RedisResult` errors.
- **Parity is a command-surface contract, not a connection-plumbing one.**
  The clients are *not* `redis` connection objects: the `ConnectionLike`
  interop layer (async and blocking) was removed because it hands commands
  over by reference, forcing a full payload copy per command (and, on the
  blocking side, a packed-byte round-trip costing two more). Replacements,
  all zero-extra-copy:
  - typed pipelines/transactions: `PipelineExt::query_glide(&client)`
    (async) / `sync::PipelineExt::query_glide(&client)` (blocking), replacing
    `Pipeline::query_async` / `Pipeline::query`;
  - raw commands: the typed `glide_send(cmd)` escape hatch (plus the untyped
    `glide_send_owned`), replacing `cmd(...).query_async(&mut c)`;
  - scan iterators: the `scan*` methods now return GLIDE-owned iterators
    (`ScanIter` with `next_item()`; blocking `SyncScanIter` implementing
    `Iterator`) — call sites keep their shape;
  - generic code bounded on the fork's `ConnectionLike`-based traits should
    re-bound on `glide::AsyncCommands` / `glide::Commands`.
- The duplicated native core command traits (string/hash/list/set/sorted-set/
  generic/bitmap/HyperLogLog) were **removed** where redis-rs covers the
  command (including the pub/sub extension's duplicate `publish`, which made
  `use glide::*` ambiguous). GLIDE-only commands remain as extension traits
  with concrete return types (streams, geo, `FT.*`, `JSON.*`, Pub/Sub,
  scripting/functions, server & connection management, hash field-TTL, `LCS`,
  `SINTERCARD`, `ZRANGESTORE`, `BITFIELD`, `SORT`, `DUMP`/`RESTORE`, `COPY`, …).
- `Batch`/`BatchOptions` never shipped in a release and are gone: use
  `glide::pipe()` + `query_glide`, or `execute_pipeline(&Pipeline,
  raise_on_error, &PipelineOptions)` for GLIDE execution controls (timeout,
  retry policy, cluster routing).
- redis-rs migration ergonomics: `from_url`/`from_connection_info`/`from_urls`,
  mutual TLS (`client_identity`), `Script` with `EVALSHA` caching, whole-crate
  `glide::redis` re-export.
- No performance regression: typed calls, pipelines, and scans all ride the
  owned-send / by-reference paths (measured at native copy count; see
  DESIGN.md).

### Added — Python-parity feature gaps (also unreleased)

- **Dynamic password management**: `GlideClient::update_connection_password` /
  `GlideClusterClient::update_connection_password` (and blocking mirrors on the
  sync clients) to rotate the client's authentication password at runtime, with
  optional immediate re-`AUTH`.
- **AWS IAM authentication**: `ServerCredentials::iam(username, IamAuthConfig)`
  plus the new `IamAuthConfig` and `ServiceType` (`ElastiCache` / `MemoryDB`)
  types, lowered into the core `AuthenticationInfo`. `ServerCredentials.password`
  is now `Option<String>` (IAM-only credentials carry no static password), and
  `ServerCredentials`'s `Debug` now redacts the password.
- **OpenTelemetry**: a new `glide::telemetry` module exposing
  `OpenTelemetryConfig` (builder), `TelemetryExporter` (`grpc`/`http`/`file`),
  and `init` / `is_initialized` / `shutdown`, wrapping `glide-core`'s
  OpenTelemetry support (traces + metrics).
- **Runtime Pub/Sub**: `subscribe`, `psubscribe`, `ssubscribe`, `unsubscribe`,
  `punsubscribe`, `sunsubscribe` on `PubSubCommands`, plus an `enable_pubsub()`
  configuration opt-in that provisions the push channel without connect-time
  subscriptions. Received messages are delivered through `get_pubsub_message`.
  Note: runtime subscriptions are session-scoped and are not automatically
  restored on reconnect (connect-time subscriptions are).
- **Sorted-set store variants**: `zrangestore_by_score` and `zrangestore_by_lex`
  (with `rev` and optional `LIMIT`).
- **Routed function calls**: `fcall_route` and `fcall_ro_route`.
