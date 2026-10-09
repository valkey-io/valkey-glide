// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! GLIDE's owned command builder.
//!
//! [`Cmd`] represents a command keyword and arguments.

use crate::ValkeyFuture;
use crate::commands::core::AsyncCommands;
use crate::value::FromValkeyValue;
use crate::write::ToValkeyArgs;

#[cfg(feature = "sync")]
use crate::{ValkeyResult, commands::core::Commands};

/// Create a new command with the given keyword.
///
/// This is the recommended way to start a command.
/// Mirrors redis-rs's `cmd`.
///
/// ```
/// glide::cmd("PING");
/// ```
pub fn cmd(name: &str) -> Cmd {
    Cmd {
        inner: redis::cmd(name),
    }
}

/// A command to send to the server.
///
/// Build a command, then send it with one of:
///
/// - `crate::AsyncCommands::glide_send_command_as`
/// - `crate::AsyncCommands::glide_send_command`
/// - `crate::Commands::glide_send_command_as`
/// - `crate::Commands::glide_send_command`
///
/// ```rust,no_run
/// use glide::AsyncCommands;
/// # async fn demo(client: glide::GlideClient) -> glide::ValkeyResult<()> {
/// let set = glide::cmd("SET").arg("my_key").arg(42).clone();
/// let _: () = client.glide_send_command_as(set).await?;
///
/// let get = glide::cmd("GET").arg("my_key").clone();
/// let value: i64 = client.glide_send_command_as(get).await?;
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
        Self::default()
    }

    /// Append an argument and return `&mut self` for chaining.
    #[inline]
    pub fn arg<A: ToValkeyArgs>(&mut self, arg: A) -> &mut Self {
        arg.write_valkey_args(self);
        self
    }

    /// Execute this command on an async GLIDE client.
    ///
    /// ```rust,no_run
    /// use glide::cmd;
    /// # async fn demo(client: &glide::GlideClient) -> glide::ValkeyResult<()> {
    /// let v: i64 = cmd("GET").arg("k").query_async(client).await?;
    /// # let _ = v; Ok(()) }
    /// ```
    ///
    /// Mirrors `redis-rs`'s `query_async`.
    #[inline]
    pub fn query_async<'a, RV: FromValkeyValue>(
        &self,
        con: &'a impl AsyncCommands,
    ) -> ValkeyFuture<'a, RV> {
        con.glide_send_command_as(self.clone())
    }

    /// Execute this command on an async GLIDE client, discarding the reply.
    ///
    /// Mirrors `redis-rs`'s `exec_async`.
    #[inline]
    pub fn exec_async<'a>(&self, con: &'a impl AsyncCommands) -> ValkeyFuture<'a, ()> {
        self.query_async(con)
    }

    /// Execute this command on a blocking GLIDE client.
    ///
    /// ```rust,no_run
    /// use glide::cmd;
    /// use glide::sync::SyncGlideClient;
    /// # fn demo(client: &SyncGlideClient) -> glide::ValkeyResult<()> {
    /// let v: i64 = cmd("GET").arg("k").query(client)?;
    /// # let _ = v; Ok(()) }
    /// ```
    ///
    /// Mirrors `redis-rs`'s `query`.
    #[cfg(feature = "sync")]
    #[inline]
    pub fn query<RV: FromValkeyValue>(&self, con: &impl Commands) -> ValkeyResult<RV> {
        con.glide_send_command_as(self.clone())
    }

    /// Execute this command on a blocking GLIDE client, discarding the reply.
    ///
    /// Mirrors `redis-rs`'s `exec`.
    #[cfg(feature = "sync")]
    #[inline]
    pub fn exec(&self, con: &impl Commands) -> ValkeyResult<()> {
        self.query(con)
    }

    /// Borrow the underlying `redis::Cmd`.
    pub(crate) fn as_redis(&self) -> &redis::Cmd {
        &self.inner
    }

    /// Mutably borrow the underlying `redis::Cmd`.
    pub(crate) fn as_redis_mut(&mut self) -> &mut redis::Cmd {
        &mut self.inner
    }

    /// Consume into the underlying `redis::Cmd`.
    pub(crate) fn into_redis(self) -> redis::Cmd {
        self.inner
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn matches_redis_cmd_single_arg() {
        let v = cmd("SET").arg("key").arg(42i64).clone();
        let r = redis::cmd("SET").arg("key").arg(42i64).clone();
        assert_eq!(v.as_redis().get_packed_command(), r.get_packed_command());
    }

    #[test]
    fn matches_redis_cmd_multi_arg() {
        let v = cmd("MGET").arg(&["a", "b", "c"][..]).clone();
        let r = redis::cmd("MGET").arg(&["a", "b", "c"][..]).clone();
        assert_eq!(v.as_redis().get_packed_command(), r.get_packed_command());
    }
}
