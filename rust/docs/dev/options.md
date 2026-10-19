# Options

An option type is a public enum or struct that callers pass to a command
method, such as [`Direction`](../../src/commands/options.rs) (`LEFT` or
`RIGHT`). Its encoding is written into the command's arguments, and the type is
part of the crate's public API.

## Guidelines

- **Location:** TODO #7385: Add a
  guideline once the option type layout is decided. Until then, define a new
  option type next to similar existing ones.
- **Encoding:** The option must implement `ToValkeyArgs`. Callers pass the
  value straight to `Cmd::with_arg`, and an `Option<T>` writes nothing when it
  is `None`.
- **Single argument:** If every value writes exactly one argument (e.g.
  `Direction`), the option must implement `ToSingleValkeyArg` and be added to
  the [`assert_single` test](../../src/write.rs). If any value writes zero or
  several arguments (e.g. `ExpireOption::NONE` writes nothing,
  `SetExpiry::EX(60)` writes two), the option must not implement
  `ToSingleValkeyArg`, even if redis-rs does.
- **Tests:** The option must have an encoding test in its module's `tests`
  that covers every variant, using `assert_args` and `assert_args_empty` from
  the [test utilities](../../src/test_utils.rs).
- **Docs:** The option must have a doc comment on the type and on each variant
  naming its wire keyword. If it mirrors a redis-rs or Python type, the doc
  comment must say so.
- **Exports:** The option must be re-exported from the
  [crate root](../../src/lib.rs), under "Shared command options" or "Command
  group-specific options".
