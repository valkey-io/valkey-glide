// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! JNI bridge for isolated execution (Feature 2 scopes).
//!
//! This is a thin adapter that converts JNI types and delegates all logic
//! to `glide_core::scope` for cross-language reuse.

use crate::jni_client::{complete_callback, get_runtime, JVM};
use jni::objects::{JByteArray, JClass};
use jni::sys::{jint, jlong};
use jni::JNIEnv;

/// Acquire a scope from the client's internal connection pool.
/// Returns scope_id >= 0, -1 if exhausted, -2 if invalid.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideScopeResolver_glideScopeTryAcquire(
    env: JNIEnv,
    _class: JClass,
    client_id: jlong,
    connection_request_bytes: JByteArray,
) -> jlong {
    let bytes = match env.convert_byte_array(&connection_request_bytes) {
        Ok(b) => b,
        Err(_) => return -2,
    };

    let runtime = get_runtime();
    glide_core::scope::try_acquire_scope(client_id as u64, bytes, runtime.handle())
}

/// Release a scope back to the pool. Fire-and-forget.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideScopeResolver_glideScopeRelease(
    _env: JNIEnv,
    _class: JClass,
    scope_id: jlong,
    client_id: jlong,
) -> jint {
    let runtime = get_runtime();
    glide_core::scope::release_scope(scope_id as u64, client_id as u64, runtime.handle())
}

/// Execute a command on a scoped connection.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideScopeResolver_glideScopeExecute(
    env: JNIEnv,
    _class: JClass,
    scope_id: jlong,
    command_bytes: JByteArray,
    callback_id: jlong,
) -> jint {
    let bytes = match env.convert_byte_array(&command_bytes) {
        Ok(b) => b,
        Err(_) => return -2,
    };

    let (cmd_name, args) = match glide_core::scope::deserialize_command(&bytes) {
        Some(p) => p,
        None => return -2,
    };

    // Verify scope exists before spawning
    let registry = glide_core::pool::get_scope_registry();
    if registry.get(&(scope_id as u64)).is_none() {
        return -1;
    }

    let runtime = get_runtime();
    let jvm = JVM.get().unwrap().clone();
    let sid = scope_id as u64;

    runtime.spawn(async move {
        // Get the parent client for timeout/decompression/IAM
        let client_registry = glide_core::scope::get_client_registry();
        let client = {
            // Find parent client_id via the scope pool that owns this scope
            let pools = glide_core::pool::get_client_scope_pools();
            let parent_id = pools
                .iter()
                .find(|e| {
                    e.value()
                        .try_lock()
                        .map(|p| p.in_use.contains_key(&sid))
                        .unwrap_or(false)
                })
                .map(|e| *e.key());

            parent_id.and_then(|pid| client_registry.get(&pid).map(|e| e.value().clone()))
        };

        let result = glide_core::scope::execute_scope_command(
            sid,
            &cmd_name,
            &args,
            client.as_ref(),
        )
        .await;

        complete_callback(jvm, callback_id, result, false);
    });

    0
}
