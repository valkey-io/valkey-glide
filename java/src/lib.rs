/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use glide_core::start_socket_listener as start_socket_listener_core;
use glide_core::MAX_REQUEST_ARGS_LENGTH;

use bytes::Bytes;
use jni::objects::{JClass, JObject, JObjectArray, JByteArray, JString};
use jni::sys::{jlong, jsize};
use jni::JNIEnv;
use redis::Value;
use std::sync::mpsc;

mod errors;

use errors::{handle_errors, handle_panics, FFIError};

#[cfg(ffi_test)]
mod ffi_test;
#[cfg(ffi_test)]
pub use ffi_test::*;

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
                Ok(JObject::from(env.byte_array_from_slice(&data)?))
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
            fn value_from_pointer<'a>(
                env: &mut JNIEnv<'a>,
                pointer: jlong,
            ) -> Result<JObject<'a>, FFIError> {
                let value = unsafe { Box::from_raw(pointer as *mut Value) };
                redis_value_to_java(env, *value, true)
            }
            let result = value_from_pointer(&mut env, pointer);
            handle_errors(&mut env, result)
        },
        "valueFromPointer",
    )
    .unwrap_or(JObject::null())
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
            fn value_from_pointer_binary<'a>(
                env: &mut JNIEnv<'a>,
                pointer: jlong,
            ) -> Result<JObject<'a>, FFIError> {
                let value = unsafe { Box::from_raw(pointer as *mut Value) };
                redis_value_to_java(env, *value, false)
            }
            let result = value_from_pointer_binary(&mut env, pointer);
            handle_errors(&mut env, result)
        },
        "valueFromPointerBinary",
    )
    .unwrap_or(JObject::null())
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_RedisValueResolver_createLeakedBytesVec<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    args: JObjectArray<'local>,
) -> jlong {
    let num_elements = env.get_array_length(&args).unwrap();
    let mut bytes_vec = Vec::with_capacity(num_elements as usize);

    for index in 0..num_elements {
        unsafe {
            let value = env.get_object_array_element(&args, index as jsize).unwrap();
            let bytes = Bytes::from(env.convert_byte_array(JByteArray::from(value)).unwrap());
            bytes_vec.push(bytes)
        };
    };
    Box::leak(Box::new(bytes_vec)) as *mut Vec<Bytes> as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_RedisValueResolver_getMaxRequestArgsLength<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    MAX_REQUEST_ARGS_LENGTH as jlong
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
            fn start_socket_listener<'a>(env: &mut JNIEnv<'a>) -> Result<JObject<'a>, FFIError> {
                let (tx, rx) = mpsc::channel::<Result<String, String>>();

                start_socket_listener_core(move |socket_path: Result<String, String>| {
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
            let result = start_socket_listener(&mut env);
            handle_errors(&mut env, result)
        },
        "startSocketListener",
    )
    .unwrap_or(JObject::null())
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_storeScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    code: JString,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn store_script<'a>(
                env: &mut JNIEnv<'a>,
                code: JString,
            ) -> Result<JObject<'a>, FFIError> {
                let code_str: String = env.get_string(&code)?.into();
                let hash = glide_core::scripts_container::add_script(&code_str);
                Ok(JObject::from(env.new_string(hash)?))
            }
            let result = store_script(&mut env, code);
            handle_errors(&mut env, result)
        },
        "storeScript",
    )
    .unwrap_or(JObject::null())
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_dropScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    hash: JString,
) {
    handle_panics(
        move || {
            fn drop_script(env: &mut JNIEnv<'_>, hash: JString) -> Result<(), FFIError> {
                let hash_str: String = env.get_string(&hash)?.into();
                glide_core::scripts_container::remove_script(&hash_str);
                Ok(())
            }
            let result = drop_script(&mut env, hash);
            handle_errors(&mut env, result)
        },
        "dropScript",
    )
    .unwrap_or(())
}
