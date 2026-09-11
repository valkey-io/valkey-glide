// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! JNI bridge for isolated execution.
//!
//! This is a thin adapter that converts JNI types and delegates all logic
//! to `glide_core::scope` for cross-language reuse.

use crate::jni_client::{JVM, complete_callback, get_runtime};
use jni::JNIEnv;
use jni::objects::{JByteArray, JClass};
use jni::sys::{jint, jlong};

/// Acquire a scope from the client's internal connection pool.
/// Returns scope_id >= 0, -1 if exhausted, -2 if invalid.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideScopeResolver_glideScopeTryAcquire(
    env: JNIEnv,
    _class: JClass,
    client_id: jlong,
    connection_request_bytes: JByteArray,
    routing_slot: jint,
) -> jlong {
    let bytes = match env.convert_byte_array(&connection_request_bytes) {
        Ok(b) => b,
        Err(_) => return -2,
    };

    let runtime = get_runtime();
    glide_core::scope::try_acquire_scope(
        client_id as u64,
        bytes,
        runtime.handle(),
        routing_slot as u16,
    )
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

    let sid = scope_id as u64;

    let client = match glide_core::scope::resolve_scope_parent(sid) {
        Some(c) => c,
        None => return -1,
    };

    let runtime = get_runtime();
    let jvm = JVM.get().unwrap().clone();

    runtime.spawn(async move {
        let mut args = args;
        let result =
            glide_core::scope::send_scope_command(sid, &cmd_name, &mut args, &client).await;

        complete_callback(jvm, callback_id, result, false);
    });

    0
}
