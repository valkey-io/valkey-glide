// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! JNI bridge for isolated execution.
//!
//! This is a thin adapter that converts JNI types and delegates all logic
//! to `glide_core::scope` for cross-language reuse.

use crate::jni_client::{JVM, complete_callback, get_runtime};
use jni::JNIEnv;
use jni::objects::{JByteArray, JClass};
use jni::sys::{jint, jlong};

/// Acquire a scope from the client's scope pool, completing the Java future for
/// `callback_id` with the scope id as a `Long`, or exceptionally with the message
/// and error type from `ScopeAcquireError::request_error_type`.
///
/// Returns 0 once the acquire is queued; -2 if the byte array cannot be read, in
/// which case the future is not touched.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideScopeResolver_glideScopeAcquire(
    env: JNIEnv,
    _class: JClass,
    client_id: jlong,
    connection_request_bytes: JByteArray,
    routing_slot: jint,
    timeout_ms: jlong,
    callback_id: jlong,
) -> jint {
    let bytes = match env.convert_byte_array(&connection_request_bytes) {
        Ok(b) => b,
        Err(_) => return -2,
    };

    let runtime = get_runtime();
    let handle = runtime.handle().clone();
    let jvm = JVM.get().unwrap().clone();

    runtime.spawn(async move {
        let result = glide_core::scope::acquire_scope(
            client_id as u64,
            bytes,
            &handle,
            routing_slot as u16,
            std::time::Duration::from_millis(timeout_ms.max(0) as u64),
        )
        .await
        .map(|scope_id| redis::Value::Int(scope_id as i64))
        .map_err(redis::RedisError::from);

        complete_callback(jvm, callback_id, result, false);
    });

    0
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
