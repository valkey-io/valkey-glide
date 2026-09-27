// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Types for the command-table parity tests.
//!
//! The JSON in the examples below is illustrative only — these types are not
//! serialized; it just shows the shape a parsed entry takes.

/// A command-table method (e.g. `get`).
#[derive(Debug, PartialEq, Eq, PartialOrd, Ord)]
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

/// A generic parameter (e.g. `K: ToRedisArgs`, or unbounded `RV`).
#[derive(Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct Generic {
    /// The parameter name (e.g. `K`, `RV`).
    pub name: String,
    /// The trait bound (e.g. `ToRedisArgs`), or `None` if unbounded.
    pub bound: Option<String>,
}

/// A function argument (e.g. `key: K`).
#[derive(Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct Argument {
    /// The argument name (e.g. `key`).
    pub name: String,
    /// The argument's type, as written (e.g. `K`).
    pub type_name: String,
}
