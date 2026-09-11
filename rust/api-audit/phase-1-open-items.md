# Deferred items (for later phases)

Phases 1–2 of #7024 landed the public reply/arg types, the `Result` rename,
`SetExpiry`, and a glide-owned `IntoConnectionInfo` — without touching
`glide_send_owned`, the command signatures, or the parity guard. The items below
are intentionally deferred; each has an inline `TODO #7024` marker at the code
site (`grep -rn "TODO #7024" rust/`). This file is the longer-form rationale for
the ones that need more than a one-line comment.

## Command layer → Valkey types (the big one)

**Return side — DONE (Phase 3a).** `glide_send_owned` / `glide_send_owned_sync`
now return `ValkeyFuture<ValkeyValue>` / `ValkeyResult<ValkeyValue>`; the client
seam converts the reply via `ValkeyValue::from_redis` and classifies the error
via `GlideError::from_redis_error`. The `core.rs` command table, `glide_send*`,
and `Script` are rolled to `RV: FromValkeyValue` with `ValkeyResult`/`ValkeyFuture`
returns. `ValkeyFuture` was added beside `ValkeyResult` in `lib.rs`;
`ValkeyValue::from_redis` (+ nested `from_redis`) lost their `dead_code` allows.

**Arg side — still deferred (coupled to `Cmd`).** Every command still builds via
`Cmd::$name(args)` / `cmd.arg(a)`, which require redis's `ToRedisArgs`, so command
args (and `Script`/`custom_command` args, and the `scan*` `write_redis_args`
paths) stay bound on `ToRedisArgs`. Rolling them to `ToValkeyArgs` needs the
glide-owned `Cmd` (below): a `ToValkeyArgs: ToRedisArgs` supertrait would both
re-leak `redis::ToRedisArgs` into the public surface and block downstream user
impls, so the arg-bound roll must land *with* `Cmd`, not before.

Remaining, still actionable once the above lands:

- **`value.rs` `to_*` / `from_value` decoders** take `redis::Value` and are
  public only because per-family command traits still hand back `redis::Value`
  via `execute_command`. Move them onto `ValkeyValue` (public) and make the
  `redis::Value` forms `pub(crate)`.
- **`scan` composite decode** still bounds `RV: FromRedisValue` and bridges the
  page through `ValkeyValue::into_redis()` → `from_owned_redis_value`, because the
  provisional blanket `FromValkeyValue` cannot decode `(u64, Vec<RV>)` from an
  `RV: FromValkeyValue` bound (needs redis's `Vec`/tuple decode). Rolls with
  native decode. Inline `TODO #7024` in `commands/scan.rs`.
- **`CommandExecutor::execute_command` / `CustomCommand`** still return
  `redis::Value` and take `ToRedisArgs`; roll with the `Cmd`/arg change (this is
  "the Cmd seam").
- **`Script` NOSCRIPT detection** — `GlideError::from_redis_error` collapses the
  fork's `ErrorKind::NoScriptError` into `Request(msg)`, dropping the code, so the
  `EVALSHA`→`EVAL` fallback matches the stringified message
  (`GlideError::is_no_script_error`). Replace with a preserved server error code.
  Inline `TODO #7024` in `error.rs`.
- **Parity guard** needs a redis→valkey name mapping
  (`ToRedisArgs`→`ToValkeyArgs`, `FromRedisValue`→`FromValkeyValue`, and the
  param-type renames below) once the arg-side signatures change.
- **Command-param types** `Direction`, `Expiry`, `SetOptions`, `LposOptions`
  (deferred from Phase 2) are macro-table params in `core.rs`, forwarded verbatim
  to `Cmd::$name`. Converting them to glide-owned types needs the macro dispatch
  to convert glide args first. (`Expiry` is also used in hand-written `hgetex`.)
- **`Cmd`** — glide-owned command builder; replace at the executor seam
  (`execute_command` / `glide_send_owned`) together with the arg-bound roll.
- **`Pipeline`** — glide-owned pipeline; tied to redis's typed pipeline decoding
  (`query_glide`, still `RedisResult`) and glide-core `send_pipeline`/
  `send_transaction` (`execute_pipeline`). Not a small owned type; lands with the
  decode rework.

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
