// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Global registry for credential providers.
//!
//! This module provides a thread-safe global registry that allows language bindings
//! to register custom AWS credential providers before creating clients. This is
//! used by bindings that use a registry-key approach rather than passing a function
//! pointer directly — currently Node.js (via NAPI) and the socket-listener path.
//! Go and Python pass the callback as a direct C function pointer to `create_client`
//! and do not use this registry.
//!
//! Usage:
//! 1. Register a provider with a unique key using `register`.
//! 2. Pass the key as `credential_provider_key` in the `ConnectionRequest` protobuf.
//! 3. During `create_client`, the key is extracted from the proto and the provider
//!    is removed from the registry and injected into the IAM configuration.
//! 4. Remove the provider using `remove` if client creation fails before step 3.

use crate::iam::CredentialsProvider;
use once_cell::sync::Lazy;
use std::collections::HashMap;
use std::sync::RwLock;

static REGISTRY: Lazy<RwLock<HashMap<String, CredentialsProvider>>> =
    Lazy::new(|| RwLock::new(HashMap::new()));

/// Register a credentials provider with the given key.
/// Returns the previous provider if one was already registered with the same key.
pub fn register(key: String, provider: CredentialsProvider) -> Option<CredentialsProvider> {
    let mut registry = REGISTRY
        .write()
        .expect("Failed to acquire credential provider registry write lock");
    registry.insert(key, provider)
}

/// Remove and return the credentials provider registered with the given key.
pub fn remove(key: &str) -> Option<CredentialsProvider> {
    let mut registry = REGISTRY
        .write()
        .expect("Failed to acquire credential provider registry write lock");
    registry.remove(key)
}

/// Get a clone of the credentials provider registered with the given key.
///
/// This is provided for completeness but is not used in the normal connection
/// lifecycle. Use [`remove`] to atomically claim the provider during client
/// creation. Using `get` followed by a separate operation is not atomic.
pub fn get(key: &str) -> Option<CredentialsProvider> {
    let registry = REGISTRY
        .read()
        .expect("Failed to acquire credential provider registry read lock");
    registry.get(key).cloned()
}
