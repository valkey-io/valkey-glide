// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Lua script helper (`Script`): SHA-caching `EVALSHA` with `EVAL` fallback.
//!
//!
//! ```rust,no_run
//! use glide::Script;
//! # async fn demo(mut client: glide::GlideClient) -> glide::ValkeyResult<()> {
//! let script = Script::new("return tonumber(ARGV[1]) + tonumber(ARGV[2])");
//! let sum: i64 = script.arg(1).arg(2).invoke_async(&mut client).await?;
//! assert_eq!(sum, 3);
//! # Ok(()) }
//! ```
//!
//! Async methods take an async GLIDE client ([`crate::GlideClient`] or
//! [`crate::GlideClusterClient`]); blocking methods take a sync GLIDE client
//! ([`crate::sync::SyncGlideClient`] or [`crate::sync::SyncGlideClusterClient`]).

use crate::ValkeyFuture;
use crate::ValkeyResult;
use crate::cmd::cmd;
use crate::commands::core::AsyncCommands;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::write::ToValkeyArgs;
use glide_core::scripts_container::add_script;
use glide_core::scripts_container::remove_script;

/// Runs a cached script by hash on an async client.
/// Implemented by [`GlideClient`] and [`GlideClusterClient`].
#[doc(hidden)]
#[sealed::sealed(pub(crate))]
pub trait ScriptInvoke {
    fn glide_invoke_script<'a>(
        &'a self,
        hash: &'a str,
        keys: &'a [Vec<u8>],
        args: &'a [Vec<u8>],
    ) -> ValkeyFuture<'a, ValkeyValue>;
}

/// Runs a cached script by hash on a sync client.
/// Blocking counterpart of [`ScriptInvoke`].
/// Implemented by the [`SyncGlideClient`] and [`SyncGlideClusterClient`].
#[cfg(feature = "sync")]
#[doc(hidden)]
#[sealed::sealed(pub(crate))]
pub trait ScriptInvokeSync {
    fn glide_invoke_script(
        &self,
        hash: &str,
        keys: &[Vec<u8>],
        args: &[Vec<u8>],
    ) -> ValkeyResult<ValkeyValue>;
}

/// A cached Lua script with its SHA-1 hash.
///
/// Create once (computes the SHA-1), then [`Self::arg`]/[`Self::key`] to build
/// an invocation. See the [module docs](self) for an example.
#[derive(Debug)]
pub struct Script {
    code: String,
    hash: String,
}

impl Script {
    /// Create a new script object with a precomputed SHA-1 hash.
    pub fn new(code: &str) -> Script {
        let hash = add_script(code.as_bytes());
        Script {
            code: code.to_string(),
            hash,
        }
    }

    /// The SHA-1 hash of the script, as used by `EVALSHA`.
    pub fn get_hash(&self) -> &str {
        &self.hash
    }

    /// Create an invocation and add a regular argument (`ARGV[…]`).
    #[must_use]
    pub fn arg<'a, T: ToValkeyArgs>(&'a self, arg: T) -> ScriptInvocation<'a> {
        let mut invocation = self.prepare_invoke();
        invocation.arg(arg);
        invocation
    }

    /// Create an invocation and add a key argument (`KEYS[…]`).
    #[must_use]
    pub fn key<'a, T: ToValkeyArgs>(&'a self, key: T) -> ScriptInvocation<'a> {
        let mut invocation = self.prepare_invoke();
        invocation.key(key);
        invocation
    }

    /// Create an empty invocation (no keys, no args).
    #[must_use]
    pub fn prepare_invoke(&self) -> ScriptInvocation<'_> {
        ScriptInvocation {
            script: self,
            args: Vec::new(),
            keys: Vec::new(),
        }
    }

    /// Invoke the script without keys or args.
    pub async fn invoke_async<C: ScriptInvoke, T: FromValkeyValue>(
        &self,
        con: &C,
    ) -> ValkeyResult<T> {
        self.prepare_invoke().invoke_async(con).await
    }

    /// Invoke the script without keys or args on a **blocking** connection
    /// ([`crate::sync::SyncGlideClient`] / [`crate::sync::SyncGlideClusterClient`]).
    #[cfg(feature = "sync")]
    pub fn invoke<C: ScriptInvokeSync, T: FromValkeyValue>(&self, con: &C) -> ValkeyResult<T> {
        self.prepare_invoke().invoke(con)
    }

    /// Load the script into the server's script cache (`SCRIPT LOAD`) without
    /// running it; returns the SHA-1 hash.
    pub async fn load_async<C: AsyncCommands>(&self, con: &C) -> ValkeyResult<String> {
        let mut load = cmd("SCRIPT");
        load.arg("LOAD").arg(self.code.as_bytes());
        String::from_owned_valkey_value(con.glide_send_command(load).await?)
    }

    /// Load the script into the server's script cache (`SCRIPT LOAD`) on a
    /// **blocking** connection; returns the SHA-1 hash.
    #[cfg(feature = "sync")]
    pub fn load<C: crate::commands::core::Commands>(&self, con: &C) -> ValkeyResult<String> {
        let mut load = cmd("SCRIPT");
        load.arg("LOAD").arg(self.code.as_bytes());
        String::from_owned_valkey_value(con.glide_send_command(load)?)
    }
}

impl Clone for Script {
    fn clone(&self) -> Self {
        // Bump glide-core's ref-count for the script.
        add_script(self.code.as_bytes());
        Script {
            code: self.code.clone(),
            hash: self.hash.clone(),
        }
    }
}

impl Drop for Script {
    fn drop(&mut self) {
        remove_script(&self.hash);
    }
}

/// A pending script invocation: keys + args bound to a [`Script`].
#[derive(Debug, Clone)]
pub struct ScriptInvocation<'a> {
    script: &'a Script,
    args: Vec<Vec<u8>>,
    keys: Vec<Vec<u8>>,
}

impl ScriptInvocation<'_> {
    /// Add a regular argument (`ARGV[…]`). Builder form.
    pub fn arg<T: ToValkeyArgs>(&mut self, arg: T) -> &mut Self {
        arg.write_valkey_args(&mut self.args);
        self
    }

    /// Add a key argument (`KEYS[…]`). Builder form.
    pub fn key<T: ToValkeyArgs>(&mut self, key: T) -> &mut Self {
        key.write_valkey_args(&mut self.keys);
        self
    }

    /// Executes the script using [`EVALSHA`], with automatic fallback to
    /// [`EVAL`] if the script is not cached on the server.
    ///
    /// [`EVALSHA`]: https://valkey.io/commands/evalsha/
    /// [`EVAL`]: https://valkey.io/commands/eval/
    pub async fn invoke_async<C: ScriptInvoke, T: FromValkeyValue>(
        &self,
        con: &C,
    ) -> ValkeyResult<T> {
        let value = con
            .glide_invoke_script(&self.script.hash, &self.keys, &self.args)
            .await?;
        T::from_owned_valkey_value(value)
    }

    /// Executes the script on a **blocking** connection using [`EVALSHA`],
    /// with automatic fallback to [`EVAL`] if the script is not cached on the server.
    ///
    /// [`EVALSHA`]: https://valkey.io/commands/evalsha/
    /// [`EVAL`]: https://valkey.io/commands/eval/
    #[cfg(feature = "sync")]
    pub fn invoke<C: ScriptInvokeSync, T: FromValkeyValue>(&self, con: &C) -> ValkeyResult<T> {
        let value = con.glide_invoke_script(&self.script.hash, &self.keys, &self.args)?;
        T::from_owned_valkey_value(value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sha1_matches_server_semantics() {
        // SHA-1 of "return 1" (verified via `printf 'return 1' | sha1sum` and
        // matching the server's SCRIPT LOAD result).
        let script = Script::new("return 1");
        assert_eq!(
            script.get_hash(),
            "e0e1f9fabfc9d4800c877a703b823ac0578ff8db"
        );
    }

    #[test]
    fn invocation_collects_keys_and_args() {
        let script = Script::new("return KEYS[1]");
        let mut invocation = script.prepare_invoke();
        invocation.key("k1").key("k2").arg("a1").arg(2);
        assert_eq!(invocation.keys, vec![b"k1".to_vec(), b"k2".to_vec()]);
        assert_eq!(invocation.args, vec![b"a1".to_vec(), b"2".to_vec()]);
    }
}
