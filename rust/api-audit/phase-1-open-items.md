# Phase 1 — deferred items (for Phase 3)

Phase 1 (core Valkey types) of #7024 landed the public reply/arg types without
touching `glide_send_owned`, the command signatures, or the parity guard. The
items below are intentionally deferred; each has an inline `TODO #7024` marker at
the code site (`grep -rn "TODO #7024" rust/`). This file is the longer-form
rationale for the ones that need more than a one-line comment.

## Command layer → Valkey types (the big one)

`glide_send_owned` / `glide_send_owned_sync` still return `RedisFuture<Value>` /
`RedisResult<Value>`, and every command stays bound on redis's
`ToRedisArgs`/`FromRedisValue` with `redis::Value` returns. Phase 3 rewires them
to `ValkeyFuture<ValkeyValue>` / `ValkeyResult<ValkeyValue>` and rolls the
`ToValkeyArgs`/`FromValkeyValue` bounds + Valkey return types across the table.
This is what makes the following items actionable:

- **`ValkeyValue::from_redis` (+ nested `from_redis`)** are `#[allow(dead_code)]`
  until a command actually produces a `ValkeyValue`.
- **`value.rs` `to_*` / `from_value` decoders** take `redis::Value` and are
  public only because the command surface still hands back `redis::Value`. Move
  them onto `ValkeyValue` (public) and make the `redis::Value` forms `pub(crate)`.
- **`ValkeyFuture`** was removed from Phase 1 (nothing returned it); re-add it
  beside `ValkeyResult` in `lib.rs` when `glide_send_owned` uses it.
- **Parity guard** needs a redis→valkey generic-bound name mapping
  (`ToRedisArgs`→`ToValkeyArgs`, `FromRedisValue`→`FromValkeyValue`) once the
  command bounds are renamed.

## `ValkeyValue::into_redis` should disappear

`FromValkeyValue` currently decodes by round-tripping a `ValkeyValue` back through
`redis::Value` (`into_redis` → `from_owned_redis_value`). That's why `into_redis`
exists and why it has to reconstruct a `Value::ServerError` (nested error elements
in a decoded reply must still decode to an error). redis-rs never re-encodes a
reply, and neither should we: decode `ValkeyValue` **natively** in
`FromValkeyValue` (walking the tree, erroring on `ServerError` nodes directly),
then delete `into_redis` and the `ServerError` reconstruction entirely.

## `ValkeyServerError` representation

Currently a flat `{ code: String, detail: Option<String> }`, populated via redis's
public accessors. redis's own `ServerError` (`ExtensionError`/`KnownError` +
`ServerErrorKind`) is **not re-exported** by the fork, so it's unnameable from this
crate — we can't mirror the variant split today. Revisit whether the flat struct is
the intended final shape (it captures the full observable wire content: code +
detail) or whether the fork should re-export the richer types.

## `ToValkeyArgs` / `FromValkeyValue` blanket impls

Implemented as blanket impls delegating to redis (`impl<T: ToRedisArgs>`,
`impl<T: FromRedisValue>`). Trade-off: a blanket impl **blocks downstream users
from hand-implementing** these traits for their own types — which conflicts with
the migration goal of letting users port their custom `ToRedisArgs`/`FromRedisValue`
types. When these become real command bounds in Phase 3, decide between keeping the
blanket impls vs. explicit per-standard-type impls (which leave the trait open for
user impls). If they go explicit, the two trait tests should broaden from one
representative type to a case per standard type.

## Phase 4 (separate, breaking)

The `redis` re-exports in `lib.rs` (`pub use redis;`, `pub use redis::Value`, and
the flat `pub use redis::{…}` lines) stay until the command surface is fully off
`redis::*`. Phase 4 removes them and provides Valkey-branded migration aliases.
