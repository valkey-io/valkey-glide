// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! The command dispatch seam.
//!
//! [`CommandExecutor`] is the single trait every command family builds on. Both
//! [`crate::GlideClient`] and [`crate::GlideClusterClient`] implement it. All the
//! typed command methods live in extension traits with blanket impls over
//! `CommandExecutor`, so a single implementation of this trait unlocks the entire
//! command surface.

use crate::error::Result;
use crate::routes::Route;
use async_trait::async_trait;
use redis::cluster_routing::RoutingInfo;
use redis::{Cmd, Value};

/// The low-level command execution interface.
///
/// Implementors forward a fully-built [`Cmd`] to `glide-core` and return the raw
/// [`Value`] reply (already normalized by core's value-conversion layer).
#[async_trait]
pub trait CommandExecutor: Send + Sync {
    /// Execute `cmd`, optionally routed to a specific node/set of nodes (cluster).
    /// Standalone implementations ignore `routing`.
    async fn execute_command(&self, cmd: Cmd, routing: Option<RoutingInfo>) -> Result<Value>;
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
    async fn custom_command<A>(&self, args: &[A]) -> Result<Value>
    where
        A: redis::ToRedisArgs + Sync,
    {
        let mut cmd = Cmd::new();
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, None).await
    }

    /// Like [`CustomCommand::custom_command`] but routed (cluster). Ignored for
    /// standalone clients.
    async fn custom_command_with_route<A>(&self, args: &[A], route: Route) -> Result<Value>
    where
        A: redis::ToRedisArgs + Sync,
    {
        let mut cmd = Cmd::new();
        for a in args {
            cmd.arg(a);
        }
        let routing = route.to_routing_info(Some(&cmd));
        self.execute_command(cmd, Some(routing)).await
    }
}

impl<T: CommandExecutor + ?Sized> CustomCommand for T {}
