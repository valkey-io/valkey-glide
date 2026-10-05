# Migrating from redis-rs

The GLIDE Rust client's command surface is source-compatible with redis-rs
1.7.0: the `AsyncCommands` / `Commands` methods and their typed counterparts
(`AsyncTypedCommands` / `TypedCommands`) keep the same names, generics,
argument lists, and (for the typed traits) return types, so most call sites
migrate with only import changes. Some redis-rs commands are not implemented
yet; see [#7060](https://github.com/valkey-io/valkey-glide/issues/7060).

## Renamed types

GLIDE-specific equivalents replace the redis-rs types:

| redis-rs           | GLIDE               |
|--------------------|---------------------|
| `RedisResult`      | `ValkeyResult`      |
| `RedisError`       | `GlideError`        |
| `Value`            | `ValkeyValue`       |
| `ToRedisArgs`      | `ToValkeyArgs`      |
| `ToSingleRedisArg` | `ToSingleValkeyArg` |
| `FromRedisValue`   | `FromValkeyValue`   |

To migrate a typed call site, you only rename the type.

## Connections, pipelines and raw commands

Every command is executed by glide-core (multiplexing, cluster routing,
reconnection, IAM auth), handed over **by value** on GLIDE's zero-extra-copy
path. Parity is deliberately a **command-surface** contract, not a
connection-plumbing one: the clients are *not* `redis` connection objects
(`ConnectionLike`), because that interop layer forced a full payload copy per
command. The migrations that follow from this are mechanical:

<!-- TODO #7058: `glide_send` and `glide_send_owned` no longer exist; the
methods are `glide_send_command_as` and `glide_send_command`. -->

| redis-rs call site              | GLIDE call site                           |
|---------------------------------|-------------------------------------------|
| `pipe()….query_async(&mut c)`   | `pipe()….query_async(&c)` (`PipelineExt`) |
| sync `pipe()….query(&mut c)`    | `pipe()….query(&c)` (`sync::PipelineExt`) |
| `cmd("X")….query_async(&mut c)` | `c.glide_send(cmd)` (typed, by value)     |
| `con.scan_match(pat)` iterators | same call; GLIDE-owned iterator           |

GLIDE's scan iterators yield a `ValkeyResult<RV>` per item, from `next_item()`
(async) or `Iterator` (blocking).

```rust,no_run
use glide::{
    AsyncCommands, GlideClient, GlideClientConfiguration, PipelineExt, Script, pipe,
};

# async fn demo() -> glide::ValkeyResult<()> {
// Standard connection-URL semantics, including rediss:// and database selection:
let config = GlideClientConfiguration::from_url("redis://user:pass@localhost:6379/2")
    .expect("valid URL");
# let client = GlideClient::connect(config).await.unwrap();

// Typed commands, unchanged from redis-rs call sites:
client.set::<_, _, ()>("key", 42).await?;
let value: i64 = client.get("key").await?;

// Pipelines and transactions:
let (a, b): (i64, i64) = pipe()
    .atomic()
    .incr("counter", 1)
    .incr("counter", 1)
    .query_async(&client)
    .await?;

// Lua scripts with EVALSHA caching:
let script = Script::new("return tonumber(ARGV[1]) + 1");
let n: i64 = script.arg(41).invoke_async(&client).await?;
# Ok(()) }
```

Notes:

- `glide::AsyncCommands` / `glide::Commands` are GLIDE's command API, with
  caller-chosen return types. `glide::AsyncTypedCommands` /
  `glide::TypedCommands` are their typed counterparts with concrete return
  types (e.g. `get` returns `Option<String>`), mirroring redis-rs's
  `AsyncTypedCommands` / `TypedCommands`. As in redis-rs, a trait and its typed
  counterpart share method names, so import only one of them.
  Extension traits (streams, geo search, `JSON.*`, …)
  cover the rest of the command surface; their names never collide with the
  command traits.
- Cluster: `GlideClusterClientConfiguration::from_urls([...])` accepts
  seed-node URLs; commands are routed automatically.
- Mutual TLS: `config.client_identity(cert_pem, key_pem)`.
  <!-- TODO #7058: see the `glide_send` note above. -->
- Raw commands: build a `glide::Cmd` with `glide::cmd("X")`. Send it typed with
  `client.glide_send(cmd)`, or untyped with `glide_send_owned` /
  `custom_command`. This replaces `cmd().query_async()` without the
  connection-object copy.
- Accepted gaps: no Sentinel / unix sockets / async-std (unsupported by
  glide-core); Pub/Sub stays client-integrated by design; generic code
  bounded on redis-rs's `ConnectionLike`-based traits should re-bound on
  `glide::AsyncCommands` (performance-motivated deviation).

## Differences

Behaviours that differ from redis-rs and may need small changes when migrating.

### No `IntoConnectionInfo` equivalent

redis-rs accepts anything implementing its open `IntoConnectionInfo` trait (a
URL string, `(host, port)`, a `url::Url`, a prebuilt `ConnectionInfo`, or your
own type). GLIDE instead offers concrete constructors on both configurations:
`from_url(url)` (accepts a `&str`, `String`, or `url::Url` — anything
`AsRef<str>`) and `with_address(host, port)`; the cluster configuration also has
`from_urls(urls)` for multiple seed URLs. Custom `IntoConnectionInfo` impls and
prebuilt `ConnectionInfo` structs are not accepted — for anything a
URL/host-port can't express (explicit database, credentials, protocol, TLS, …),
build the configuration with `new(...)` and the `with_*` setters, which is
GLIDE's connection-description type.

### `ValueComparison` supports only `IFEQ`

Valkey has no `DIGEST` command, so redis-rs's `IFDEQ` and `IFDNE` digest
comparisons (and `digest`) are not available. `IFNE` requires Valkey 9.2 and is
not supported yet.
<!-- TODO #7237: Update once `IFNE` is supported. -->

### `StreamAddOptions` supports only `NOMKSTREAM` and trimming

Valkey's `XADD` has no deletion policies or idempotent production, so
redis-rs's `StreamAddOptions::set_deletion_policy`, `idmp` and `idmpauto` are
not available; `nomkstream` and `trim` are. GLIDE's stream reply types
(`StreamId`, `StreamRangeReply`, `StreamReadReply`, …) are at the crate root
rather than in a `streams` module, and `StreamId::map` holds `ValkeyValue`s.

### A few typed returns differ

`zpopmin` and `zpopmax` return `Vec<(String, f64)>` instead of `Vec<String>`.
glide-core reshapes their reply into a member-to-score map, which `Vec<String>`
cannot decode, so redis-rs's typed return would fail on every non-empty pop.

`blmpop` returns `Option<(String, Vec<String>)>` instead of
`Option<[String; 2]>`, matching `lmpop`. Its reply is a key and a list of
elements, which `[String; 2]` cannot decode.

### `hset_multiple` differs

`hset_multiple` sends multi-field `HSET` and returns the number of fields added,
instead of sending `HMSET` and returning `()`. Migrated code that annotates
`hset_multiple`'s result as `()` must change the type; with the generic
`AsyncCommands`/`Commands` traits, `()` still works.

### No `get_int` or `mget_ints`

The typed traits omit these redis-rs helpers. Use the generic
`AsyncCommands`/`Commands` `get` and `mget` with an integer return type instead,
e.g. `let n: Option<isize> = c.get(key).await?;`.

### Geo types are renamed

redis-rs's `geo::Unit` and `geo::Coord` are `GeoUnit` and `GeoCoord` at the
crate root, with the same variants, fields, and constructors.

### Integer decoding is strict

redis-rs decodes numeric responses with `as`, which can silently produce a wrong
value: a non-integral double is truncated (`1.5` decoded as `u8` becomes `1`),
an out-of-range or NaN double is clamped (`-1.0` decoded as `u64` becomes `0`),
and an out-of-range integer wraps (`-2` decoded as `usize` becomes
`18446744073709551614`). GLIDE instead returns an error naming the target type
unless the reply is an integer, a whole-number double, or a numeric string within
that type's range. Decode into a type that fits the reply (e.g. `f64` for
`INCRBYFLOAT`). Float decoding matches redis-rs.

### `ValueType` covers only the core Valkey types

redis-rs's variants for module types (vector sets, the Redis Stack modules, and
the `JSON` and `BloomFilterValKey` types of valkey-json and valkey-bloom) are
absent; any other type name decodes as `ValueType::Unknown`, e.g.
`ValueType::Unknown("ReJSON-RL".to_string())`.
