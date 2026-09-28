// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Types for the parity tests.

use serde::Deserialize;
use serde::Serialize;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

/// A snapshot of redis-rs commands for parity tests.
#[derive(Debug, Serialize, Deserialize)]
pub struct RedisParity {
    /// The redis-rs release this snapshot describes (e.g. `0.25.2`).
    pub version: String,
    /// The command-table methods, indexed by method name.
    pub methods: BTreeMap<String, Method>,
    /// The scan-iterator method names (compared by name only).
    pub scan_method_names: BTreeSet<String>,
}

/// A command-table method (e.g. `get`).
#[derive(Debug, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
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
#[derive(Debug, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
pub struct Generic {
    /// The parameter name (e.g. `K`, `RV`).
    pub name: String,
    /// The trait bound (e.g. `ToRedisArgs`), or `None` if unbounded.
    pub bound: Option<String>,
}

/// A function argument (e.g. `key: K`).
#[derive(Debug, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
pub struct Argument {
    /// The argument name (e.g. `key`).
    pub name: String,
    /// The argument's type, as written (e.g. `K`).
    pub type_name: String,
}
