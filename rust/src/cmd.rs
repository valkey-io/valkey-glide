// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! GLIDE's owned command builder.
//!
//! [`Cmd`] represents a command keyword and arguments.

use crate::value::ToValkeyArgs;

/// Create a new command with the given keyword.
///
/// This is the recommended way to start a command.
/// Mirrors redis-rs's `cmd`.
///
/// ```
/// glide::cmd("PING");
/// ```
pub fn cmd(name: &str) -> Cmd {
    let mut command = Cmd::new();
    command.arg(name);
    command
}

/// A command to send to the server.
///
/// Build a command, then send it with one of:
///
/// - [`glide_send`](crate::AsyncCommands::glide_send)
/// - [`glide_send_owned`](crate::AsyncCommands::glide_send_owned)
/// - [`glide_send_sync`](crate::Commands::glide_send_sync)
/// - [`glide_send_owned_sync`](crate::Commands::glide_send_owned_sync)
///
/// ```no_run
/// use glide::AsyncCommands;
/// # async fn demo(client: glide::GlideClient) -> glide::ValkeyResult<()> {
/// let set = glide::cmd("SET").arg("my_key").arg(42).clone();
/// let _: () = client.glide_send(set).await?;
///
/// let get = glide::cmd("GET").arg("my_key").clone();
/// let value: i64 = client.glide_send(get).await?;
///
/// # assert_eq!(value, 42);
/// # Ok(()) }
/// ```
///
/// Mirrors redis-rs's `Cmd`.
#[derive(Clone, Default)]
pub struct Cmd {
    inner: redis::Cmd,
}

impl Cmd {
    /// Create an empty command.
    ///
    /// It is recommended to use [`cmd`] instead
    /// to explicitly specify the command keyword.
    pub fn new() -> Self {
        Cmd {
            inner: redis::Cmd::new(),
        }
    }

    /// Append an argument and return `&mut self` for chaining.
    #[inline]
    pub fn arg<A: ToValkeyArgs>(&mut self, arg: A) -> &mut Self {
        self.inner.arg(arg.to_valkey_args());
        self
    }

    /// The command's keyword and arguments in order.
    // TODO #7024: convert the request-encoding tests to in-crate unit tests, then
    // drop this to `pub(crate)`. See rust/api-audit/phase-1-open-items.md.
    #[doc(hidden)]
    pub fn args(&self) -> Vec<&[u8]> {
        self.inner
            .args_iter()
            .filter_map(|a| match a {
                redis::Arg::Simple(bytes) => Some(bytes),
                redis::Arg::Cursor => None,
            })
            .collect()
    }

    /// Borrow the underlying `redis::Cmd`.
    pub(crate) fn as_redis(&self) -> &redis::Cmd {
        &self.inner
    }

    /// Mutably borrow the underlying `redis::Cmd`.
    pub(crate) fn as_redis_mut(&mut self) -> &mut redis::Cmd {
        &mut self.inner
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn matches_redis_cmd_single_arg() {
        let mut v = Cmd::new();
        v.arg("SET").arg("key").arg(42i64);

        let mut r = redis::Cmd::new();
        r.arg("SET").arg("key").arg(42i64);

        assert_eq!(v.as_redis().get_packed_command(), r.get_packed_command());
    }

    #[test]
    fn matches_redis_cmd_multi_arg() {
        let mut v = Cmd::new();
        v.arg("MGET").arg(&["a", "b", "c"][..]);

        let mut r = redis::Cmd::new();
        r.arg("MGET").arg(&["a", "b", "c"][..]);

        assert_eq!(v.as_redis().get_packed_command(), r.get_packed_command());
    }
}
