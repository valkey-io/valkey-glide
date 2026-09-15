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
  (`query_glide`, still `RedisResult`) and glide-core `send_pipeline`/
  `send_transaction` (`execute_pipeline`). Not a small owned type; lands with the
  decode rework.

## `ValkeyServerError` representation

Currently a flat `{ code: String, detail: Option<String> }`, populated via redis's
public accessors. redis's own `ServerError` (`ExtensionError`/`KnownError` +
`ServerErrorKind`) is **not re-exported** by the fork, so it's unnameable from this
crate — we can't mirror the variant split today. Revisit whether the flat struct is
the intended final shape (it captures the full observable wire content: code +
detail) or whether the fork should re-export the richer types.

## `ToValkeyArgs` blanket impl

`FromValkeyValue` is now explicit per-type (Phase 3b), so it's open to downstream
user impls. `ToValkeyArgs` is **still** a blanket `impl<T: ToRedisArgs>`, which
blocks users from hand-implementing it for their own arg types. It resolves with
the arg-side / `Cmd` roll: replace the blanket with explicit per-standard-type
impls (leaving the trait open), driven by a glide-owned `Cmd` whose constructors
take `ToValkeyArgs`.

## Move the request-encoding tests in-crate

`tests/mock_commands/` verifies request *encoding* (the exact wire tokens each
command produces) via an in-process `Mock` executor that inspects the built
command's bytes. Because `tests/` is a separate crate, it can only see the public
API, so `Cmd::args()` had to be exposed as `#[doc(hidden)] pub` purely for these
tests. These tests peek at an internal (the built command's bytes), so they are
really unit tests — moving them in-crate (`#[cfg(test)]` under `src/`, imports
`glide::` → `crate::`) lets them use `pub(crate)` internals and lets `Cmd::args()`
drop to `pub(crate)` (or be deleted, reading args via `as_redis().args_iter()`).
Mechanical (~15 files); the live `it_*.rs` suites + the parity guard already cover
the public API from outside. Inline `TODO #7024` at `Cmd::args` and
`tests/mock_commands/main.rs`.

## Phase 4 (separate, breaking)

The `redis` re-exports in `lib.rs` (`pub use redis;`, `pub use redis::Value`, and
the flat `pub use redis::{…}` lines) stay until the command surface is fully off
`redis::*`. Phase 4 removes them and provides Valkey-branded migration aliases.
