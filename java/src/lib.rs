/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */
use glide_core::start_socket_listener;

use jni::errors::Error as JNIError;
use jni::objects::{JClass, JObject, JObjectArray, JString};
use jni::sys::jlong;
use jni::JNIEnv;
use log::error;
use redis::Value;
use std::string::FromUtf8Error;
use std::sync::mpsc;

#[cfg(ffi_test)]
mod ffi_test;
#[cfg(ffi_test)]
pub use ffi_test::*;

enum FFIError {
    Jni(JNIError),
    Uds(String),
    Utf8(FromUtf8Error),
}

impl From<jni::errors::Error> for FFIError {
    fn from(value: jni::errors::Error) -> Self {
        FFIError::Jni(value)
    }
}

impl From<FromUtf8Error> for FFIError {
    fn from(value: FromUtf8Error) -> Self {
        FFIError::Utf8(value)
    }
}

impl std::fmt::Display for FFIError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            FFIError::Jni(err) => write!(f, "{}", err),
            FFIError::Uds(err) => write!(f, "{}", err),
            FFIError::Utf8(err) => write!(f, "{}", err),
        }
    }
}

// TODO: Consider caching method IDs here in a static variable (might need RwLock to mutate)
fn redis_value_to_java<'local>(
    env: &mut JNIEnv<'local>,
    val: Value,
    encoding_utf8: bool,
) -> Result<JObject<'local>, FFIError> {
    match val {
        Value::Nil => Ok(JObject::null()),
        Value::SimpleString(str) => Ok(JObject::from(env.new_string(str)?)),
        Value::Okay => Ok(JObject::from(env.new_string("OK")?)),
        Value::Int(num) => Ok(env.new_object("java/lang/Long", "(J)V", &[num.into()])?),
        Value::BulkString(data) => {
            if encoding_utf8 {
                let utf8_str = String::from_utf8(data)?;
                Ok(JObject::from(env.new_string(utf8_str)?))
            } else {
                Ok(JObject::from(env.byte_array_from_slice(data.as_ref())?))
            }
        }
        Value::Array(array) => {
            let items: JObjectArray =
                env.new_object_array(array.len() as i32, "java/lang/Object", JObject::null())?;

            for (i, item) in array.into_iter().enumerate() {
                let java_value = redis_value_to_java(env, item, encoding_utf8)?;
                env.set_object_array_element(&items, i as i32, java_value)?;
            }

            Ok(items.into())
        }
        Value::Map(map) => {
            let linked_hash_map = env.new_object("java/util/LinkedHashMap", "()V", &[])?;

            for (key, value) in map {
                let java_key = redis_value_to_java(env, key, encoding_utf8)?;
                let java_value = redis_value_to_java(env, value, encoding_utf8)?;
                env.call_method(
                    &linked_hash_map,
                    "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    &[(&java_key).into(), (&java_value).into()],
                )?;
            }

            Ok(linked_hash_map)
        }
        Value::Double(float) => Ok(env.new_object("java/lang/Double", "(D)V", &[float.into()])?),
        Value::Boolean(bool) => Ok(env.new_object("java/lang/Boolean", "(Z)V", &[bool.into()])?),
        Value::VerbatimString { format: _, text } => Ok(JObject::from(env.new_string(text)?)),
        Value::BigNumber(_num) => todo!(),
        Value::Set(array) => {
            let set = env.new_object("java/util/HashSet", "()V", &[])?;

            for elem in array {
                let java_value = redis_value_to_java(env, elem, encoding_utf8)?;
                env.call_method(
                    &set,
                    "add",
                    "(Ljava/lang/Object;)Z",
                    &[(&java_value).into()],
                )?;
            }

            Ok(set)
        }
        Value::Attribute {
            data: _,
            attributes: _,
        } => todo!(),
        Value::Push { kind: _, data: _ } => todo!(),
    }
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_RedisValueResolver_valueFromPointer<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn f<'a>(env: &mut JNIEnv<'a>, pointer: jlong) -> Result<JObject<'a>, FFIError> {
                let value = unsafe { Box::from_raw(pointer as *mut Value) };
                redis_value_to_java(env, *value, true)
            }
            let result = f(&mut env, pointer);
            handle_errors(&mut env, result)
        },
        "valueFromPointer",
        JObject::null(),
    )
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_RedisValueResolver_valueFromPointerBinary<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn f<'a>(env: &mut JNIEnv<'a>, pointer: jlong) -> Result<JObject<'a>, FFIError> {
                let value = unsafe { Box::from_raw(pointer as *mut Value) };
                redis_value_to_java(env, *value, false)
            }
            let result = f(&mut env, pointer);
            handle_errors(&mut env, result)
        },
        "valueFromPointerBinary",
        JObject::null(),
    )
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_SocketListenerResolver_startSocketListener<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn f<'a>(env: &mut JNIEnv<'a>) -> Result<JObject<'a>, FFIError> {
                let (tx, rx) = mpsc::channel::<Result<String, String>>();

                start_socket_listener(move |socket_path: Result<String, String>| {
                    // Signals that thread has started
                    let _ = tx.send(socket_path);
                });

                // Wait until the thread has started
                let socket_path = rx.recv();

                match socket_path {
                    Ok(Ok(path)) => env
                        .new_string(path)
                        .map(|p| p.into())
                        .map_err(|err| FFIError::Uds(err.to_string())),
                    Ok(Err(error_message)) => Err(FFIError::Uds(error_message)),
                    Err(error) => Err(FFIError::Uds(error.to_string())),
                }
            }
            let result = f(&mut env);
            handle_errors(&mut env, result)
        },
        "startSocketListener",
        JObject::null(),
    )
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_storeScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    code: JString,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn f<'a>(env: &mut JNIEnv<'a>, code: JString) -> Result<JObject<'a>, FFIError> {
                let code_str: String = env.get_string(&code)?.into();
                let hash = glide_core::scripts_container::add_script(&code_str);
                Ok(JObject::from(env.new_string(hash)?))
            }
            let result = f(&mut env, code);
            handle_errors(&mut env, result)
        },
        "storeScript",
        JObject::null(),
    )
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_dropScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    hash: JString,
) {
    handle_panics(
        move || {
            fn f(env: &mut JNIEnv<'_>, hash: JString) -> Result<(), FFIError> {
                let hash_str: String = env.get_string(&hash)?.into();
                glide_core::scripts_container::remove_script(&hash_str);
                Ok(())
            }
            let result = f(&mut env, hash);
            handle_errors(&mut env, result)
        },
        "dropScript",
        (),
    )
}

enum ExceptionType {
    Exception,
    RuntimeException,
}

impl std::fmt::Display for ExceptionType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ExceptionType::Exception => write!(f, "java/lang/Exception"),
            ExceptionType::RuntimeException => write!(f, "java/lang/RuntimeException"),
        }
    }
}

fn handle_errors<T>(env: &mut JNIEnv, result: Result<T, FFIError>) -> Option<T> {
    match result {
        Ok(value) => Some(value),
        Err(err) => {
            match err {
                FFIError::Utf8(utf8_error) => throw_java_exception(
                    env,
                    ExceptionType::RuntimeException,
                    utf8_error.to_string(),
                ),
                error => throw_java_exception(env, ExceptionType::Exception, error.to_string()),
            };
            None
        }
    }
}

fn handle_panics<T, F: std::panic::UnwindSafe + FnOnce() -> Option<T>>(
    func: F,
    ffi_func_name: &str,
    default_value: T,
) -> T {
    match std::panic::catch_unwind(func) {
        Ok(Some(value)) => value,
        Ok(None) => default_value,
        Err(_err) => {
            // Following https://github.com/jni-rs/jni-rs/issues/76#issuecomment-363523906
            // and throwing a runtime exception is not feasible here because of https://github.com/jni-rs/jni-rs/issues/432
            error!("Native function {} panicked.", ffi_func_name);
            default_value
        }
    }
}

fn throw_java_exception(env: &mut JNIEnv, exception_type: ExceptionType, message: String) {
    match env.exception_check() {
        Ok(true) => (),
        Ok(false) => {
            env.throw_new(exception_type.to_string(), &message)
                .unwrap_or_else(|err| {
                    error!(
                        "Failed to create exception with string {}: {}",
                        message,
                        err.to_string()
                    );
                });
        }
        Err(err) => {
            error!(
                "Failed to check if an exception is currently being thrown: {}",
                err.to_string()
            );
        }
    }
}
