// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Shared data types for the command-table parity check.

use serde::Deserialize;
use serde::Serialize;
use std::collections::BTreeSet;

/// A command table for a Valkey/Redis Rust client.
///
/// ```json
/// {
///   "source": "redis-rs fork v0.25.2",
///   "methods": [
///     { "name": "get",
///       "generics": [{ "name": "K", "bound": "ToRedisArgs" }],
///       "args": [{ "name": "key", "type_name": "K" }],
///       "return_type": null }
///   ]
/// }
/// ```
#[derive(Serialize, Deserialize)]
pub struct CommandTable {
    /// Source for the command table (e.g. 'redis-rs-1.7.0')
    pub source: String,

    /// The command table methods (e.g. `get`).
    pub methods: BTreeSet<Method>,
}

/// A method for a Valkey/Redis Rust client.
///
/// For `get<K: ToRedisArgs>(key: K)`:
/// ```json
/// { "name": "get",
///   "generics": [{ "name": "K", "bound": "ToRedisArgs" }],
///   "args": [{ "name": "key", "type_name": "K" }],
///   "return_type": null }
/// ```
#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct Method {
    /// The method name (e.g. `get`).
    pub name: String,

    /// Generic parameters, in declaration order (e.g. `K: ToRedisArgs`).
    pub generics: Vec<Generic>,

    /// Arguments, in order (e.g. `key: K`).
    pub args: Vec<Argument>,

    /// Declared return type, or `None` when unspecified.
    pub return_type: Option<String>,
}

/// A generic parameter for a Valkey/Redis Rust client.
///
/// A bounded `K: ToRedisArgs` and an unbounded `RV`:
/// ```json
/// { "name": "K", "bound": "ToRedisArgs" }
/// { "name": "RV", "bound": null }
/// ```
#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct Generic {
    /// The parameter name (e.g. `K`, `RV`).
    pub name: String,

    /// The trait bound (e.g. `ToRedisArgs`), or `None` if unbounded.
    pub bound: Option<String>,
}

/// A function argument for a Valkey/Redis Rust client.
///
/// For `key: K`:
/// ```json
/// { "name": "key", "type_name": "K" }
/// ```
#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct Argument {
    /// The argument name (e.g. `key`).
    pub name: String,

    /// The argument's type, as written (e.g. `K`).
    pub type_name: String,
}
