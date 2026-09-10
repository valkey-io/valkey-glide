// Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0

//! Global registry for credential providers.
//!
//! This module provides a thread-safe global registry that allows language bindings
//! to register custom AWS credential providers before creating clients. This is
//! particularly useful for bindings that use the socket listener pattern (e.g., Python
//! async, Node.js), where the credential provider callback cannot be passed directly
//! through the protobuf connection request.
//!
//! Usage:
//! 1. Register a provider with a unique key using `register`.
//! 2. Create the client (the socket listener will pick up the provider by the key
//!    stored in `ConnectionRequest.credential_provider_key`).
//! 3. Remove the provider using `remove` when the client is closed.

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
pub fn get(key: &str) -> Option<CredentialsProvider> {
    let registry = REGISTRY
        .read()
        .expect("Failed to acquire credential provider registry read lock");
    registry.get(key).cloned()
}
