// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Global registry for address resolvers.
//!
//! This module provides a thread-safe global registry that allows language bindings
//! to register address resolvers before creating clients. This is particularly useful
//! for bindings that use the socket listener pattern (e.g., Python async), where the
//! address resolver callback cannot be passed directly through the protobuf connection
//! request.
//!
//! Usage:
//! 1. Register a resolver with a unique key using `register`.
//! 2. Create the client (the socket listener will pick up the resolver).
//! 3. Remove the resolver using `remove` when the client is closed.

use once_cell::sync::Lazy;
use redis::AddressResolver;
use std::collections::HashMap;
use std::sync::{Arc, RwLock};

static REGISTRY: Lazy<RwLock<HashMap<String, Arc<dyn AddressResolver>>>> =
    Lazy::new(|| RwLock::new(HashMap::new()));

/// Register an address resolver with the given key.
/// Returns the previous resolver if one was already registered with the same key.
pub fn register(
    key: String,
    resolver: Arc<dyn AddressResolver>,
) -> Option<Arc<dyn AddressResolver>> {
    let mut registry = REGISTRY
        .write()
        .expect("Failed to acquire registry write lock");
    registry.insert(key, resolver)
}

/// Remove and return the address resolver registered with the given key.
pub fn remove(key: &str) -> Option<Arc<dyn AddressResolver>> {
    let mut registry = REGISTRY
        .write()
        .expect("Failed to acquire registry write lock");
    registry.remove(key)
}

/// Get a clone of the address resolver registered with the given key.
pub fn get(key: &str) -> Option<Arc<dyn AddressResolver>> {
    let registry = REGISTRY
        .read()
        .expect("Failed to acquire registry read lock");
    registry.get(key).cloned()
}
