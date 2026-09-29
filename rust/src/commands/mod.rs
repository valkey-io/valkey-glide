// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Command families. Each submodule is an extension trait with a blanket impl
//! over [`crate::executor::CommandExecutor`], so every client gets every command.

pub mod options;

pub mod core;
pub mod scan;

pub mod bitmap;
pub mod connection_management;
pub mod ft;
pub mod generic;
pub mod geo;
pub mod hash;
pub mod json;
pub mod pubsub;
pub mod scripting;
pub mod server_management;
pub mod set;
pub mod sorted_set;
pub mod stream;
pub mod string;

/// Re-exports every command trait so `use glide::commands::prelude::*;` brings all
/// command methods into scope on the clients.
pub mod prelude {
    pub use super::bitmap::BitmapCommands;
    pub use super::connection_management::ConnectionManagementCommands;
    pub use super::ft::FtCommands;
    pub use super::generic::GenericCommands;
    pub use super::geo::GeoCommands;
    pub use super::hash::HashCommands;
    pub use super::json::JsonCommands;
    pub use super::pubsub::PubSubCommands;
    pub use super::scripting::ScriptingCommands;
    pub use super::server_management::ServerManagementCommands;
    pub use super::set::SetCommands;
    pub use super::sorted_set::SortedSetCommands;
    pub use super::stream::StreamCommands;
    pub use super::string::StringCommands;
}
