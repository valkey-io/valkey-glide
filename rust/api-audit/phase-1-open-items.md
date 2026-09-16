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

**Native decode — DONE (Phase 3b).** `FromValkeyValue` now decodes `ValkeyValue`
directly with explicit per-type impls (numerics, `bool`, `String`, `Bytes`,
`Vec<T>` incl. the `Vec<u8>` byte specialization, `Option<T>`, tuples 1..=12,
`HashMap`, `HashSet`, `()`), mirroring redis-rs's coercions and tuple
flat/array-of-arrays heuristic — no round-trip through `redis::Value`.
`ValkeyValue::into_redis` (and the lossy `ServerError` reconstruction) is
**deleted**; the trait is now open to downstream user impls (resolves the
blanket-impl question below for the decode side). The `scan*` iterators are
rolled to `RV: FromValkeyValue` (bridge removed). The parity guard canonicalizes
GLIDE's renamed bounds back to the fork's names. Note: the fork's connection
layer runs `Value::extract_error()`, so a `ServerError` never reaches decode on
the command path — the trait handles it defensively anyway.

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
- **`CommandExecutor::execute_command` / `CustomCommand`** still return
  `redis::Value` and take `ToRedisArgs`; roll with the `Cmd`/arg change (this is
  "the Cmd seam").
- **`Script` NOSCRIPT detection** — `GlideError::from_redis_error` collapses the
  fork's `ErrorKind::NoScriptError` into `Request(msg)`, dropping the code, so the
  `EVALSHA`→`EVAL` fallback matches the stringified message
  (`GlideError::is_no_script_error`). Replace with a preserved server error code.
  Inline `TODO #7024` in `error.rs`.
- **Parity guard** already canonicalizes the renamed bounds
  (`FromValkeyValue`→`FromRedisValue`, `ToValkeyArgs`→`ToRedisArgs`); extend it
  with the param-type renames below when the arg-side signatures change.
- **Command-param types** `Direction`, `Expiry`, `SetOptions`, `LposOptions`
  (deferred from Phase 2) are macro-table params in `core.rs`, forwarded verbatim
  to `Cmd::$name`. Converting them to glide-owned types needs the macro dispatch
  to convert glide args first. (`Expiry` is also used in hand-written `hgetex`.)
- **`Cmd`** — glide-owned command builder; replace at the executor seam
  (`execute_command` / `glide_send_owned`) together with the arg-bound roll.
- **`Pipeline`** — glide-owned pipeline; tied to redis's typed pipeline decoding
  (`query_async`, still `RedisResult`) and glide-core `send_pipeline`/
  `send_transaction` (`exec`). Not a small owned type; lands with the
  decode rework.

## `ValkeyServerError` representation — RESOLVED (match redis-rs's public surface)

Aligned with redis-rs's `ServerError` *public* interface rather than its internal
shape. redis-rs exposes only the accessors `err_code() -> &str` / `details() ->
Option<&str>`, keeps its representation private (the `ExtensionError`/`KnownError`
+ `ServerErrorKind` split lives in a private module), and does **not** re-export
the type. So `ValkeyServerError` now: has **private fields** + `err_code()` /
`details()` accessors (same names/signatures), and is **not** re-exported at the
crate root (reachable only as `glide::value::ValkeyServerError`, the payload of
`ValkeyValue::ServerError`). No fork change; mirroring the internal variant split
would need one and exposes no additional wire information.

## `ToValkeyArgs` blanket impl

`FromValkeyValue` is now explicit per-type (Phase 3b), so it's open to downstream
user impls. `ToValkeyArgs` is **still** a blanket `impl<T: ToRedisArgs>`, which
blocks users from hand-implementing it for their own arg types. It resolves with
the arg-side / `Cmd` roll: replace the blanket with explicit per-standard-type
impls (leaving the trait open), driven by a glide-owned `Cmd` whose constructors
take `ToValkeyArgs`.

## Move the request-encoding tests in-crate — DONE

The request-encoding tests moved from `tests/mock_commands/` to `src/mock_tests/`
(`#[cfg(test)] mod mock_tests;`), so the in-process `Mock` executor now reads the
built command's bytes through the crate-internal `Cmd::as_redis().args_iter()`.
`Cmd::args()` (previously `#[doc(hidden)] pub` purely for the out-of-crate tests)
is **deleted**, so the public API no longer carries it.

## Phase 4 (separate, breaking)

The `redis` re-exports in `lib.rs` (`pub use redis;`, `pub use redis::Value`, and
the flat `pub use redis::{…}` lines) stay until the command surface is fully off
`redis::*`. Phase 4 removes them and provides Valkey-branded migration aliases.
