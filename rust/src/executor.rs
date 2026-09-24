// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! The command dispatch trait.
//!
//! [`CommandExecutor`] is the single trait every command family builds on. Both
//! [`crate::GlideClient`] and [`crate::GlideClusterClient`] implement it. All the
//! typed command methods live in extension traits with blanket impls over
//! `CommandExecutor`, so a single implementation of this trait unlocks the entire
//! command surface.

use crate::ValkeyResult;
use crate::cmd::Cmd;
use crate::routes::Route;
use crate::value::ValkeyValue;
use crate::write::ToValkeyArgs;
use async_trait::async_trait;

/// The low-level command execution interface.
///
/// Implementors forward a fully-built [`Cmd`] to `glide-core` and return the
/// decoded [`ValkeyValue`] reply.
#[async_trait]
pub trait CommandExecutor: Send + Sync {
    /// Execute `cmd`, optionally routed to a specific node/set of nodes (cluster).
    /// Standalone implementations ignore `route`.
    async fn execute_command(&self, cmd: Cmd, route: Option<Route>) -> ValkeyResult<ValkeyValue>;
}

/// Convenience helpers layered on top of [`CommandExecutor`], available on every
/// client. These are the "escape hatches" that guarantee 100% functional command
/// coverage regardless of which typed wrappers exist.
#[async_trait]
pub trait CustomCommand: CommandExecutor {
    /// Execute an arbitrary command given its already-encoded arguments, e.g.
    /// `client.custom_command(&["SET", "key", "value"]).await`.
    ///
    /// The first argument is the command keyword; the rest are its arguments.
    async fn custom_command<A>(&self, args: &[A]) -> ValkeyResult<ValkeyValue>
    where
        A: ToValkeyArgs + Sync,
    {
        let mut cmd = Cmd::new();
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, None).await
    }

    /// Like [`CustomCommand::custom_command`] but routed (cluster). Ignored for
    /// standalone clients.
    async fn custom_command_with_route<A>(
        &self,
        args: &[A],
        route: Route,
    ) -> ValkeyResult<ValkeyValue>
    where
        A: ToValkeyArgs + Sync,
    {
        let mut cmd = Cmd::new();
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, Some(route)).await
    }
}

impl<T: CommandExecutor + ?Sized> CustomCommand for T {}
