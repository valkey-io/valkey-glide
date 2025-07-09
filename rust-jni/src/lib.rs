//! JNI bridge for realistic performance benchmarking.
//!
//! This module provides a JNI interface for testing GET/SET/PING commands
//! with actual glide-core integration against the existing UDS implementation.
//! Focus is on realistic performance comparison and fair benchmarking.

use jni::JNIEnv;
use jni::objects::{JClass, JString, JByteArray};
use jni::sys::{jlong, jint, jbyteArray};

mod metadata;
mod error;
mod client;

use error::{Result, Error, ExceptionGuard};

/// Simplified JNI exports for realistic POC.
#[allow(non_snake_case)]
pub mod jni_exports {
    use super::*;

    /// Create a client connection to Redis with actual glide-core integration.
    #[no_mangle]
    pub unsafe extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_connect<'local>(
        mut env: JNIEnv<'local>,
        _class: JClass<'local>,
        connection_string: JString<'local>,
    ) -> jlong {
        let _guard = ExceptionGuard::new(&mut env);

        match client::create_client(&mut env, connection_string.into()) {
            Ok(client_ptr) => client_ptr,
            Err(err) => {
                let _ = err.throw(&mut env);
                0
            }
        }
    }

    /// Disconnect and release client resources.
    #[no_mangle]
    pub unsafe extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_disconnect<'local>(
        mut env: JNIEnv<'local>,
        _class: JClass<'local>,
        client_ptr: jlong,
    ) {
        let _guard = ExceptionGuard::new(&mut env);

        if let Err(err) = client::close_client(&mut env, client_ptr) {
            let _ = err.throw(&mut env);
        }
    }

    /// Execute a command through actual glide-core client - core function for realistic benchmarking.
    ///
    /// Takes command type (GET=1, SET=2, PING=3) and payload bytes.
    /// Returns response as byte array from actual Redis operations.
    #[no_mangle]
    pub unsafe extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_executeCommand<'local>(
        mut env: JNIEnv<'local>,
        _class: JClass<'local>,
        client_ptr: jlong,
        command_type: jint,
        payload: JByteArray<'local>,
    ) -> jbyteArray {
        let _guard = ExceptionGuard::new(&mut env);

        match execute_command_impl(&mut env, client_ptr, command_type, payload) {
            Ok(response) => response.into_raw(),
            Err(err) => {
                let _ = err.throw(&mut env);
                std::ptr::null_mut()
            }
        }
    }
}

/// Internal implementation of command execution.
fn execute_command_impl<'local>(
    env: &mut JNIEnv<'local>,
    client_ptr: jlong,
    command_type: jint,
    payload: JByteArray<'local>,
) -> Result<JByteArray<'local>> {
    // Convert Java byte array to Rust slice
    let payload_bytes = env.convert_byte_array(payload)?;

    // Execute the command via the client
    client::execute_command(env, client_ptr, command_type as u32, &payload_bytes)
}
