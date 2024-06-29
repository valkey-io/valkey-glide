/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use glide_core::start_socket_listener;

use jni::objects::{JClass, JObject, JObjectArray, JString, JThrowable};
use jni::sys::jlong;
use jni::JNIEnv;
use log::error;
use redis::Value;
use std::sync::mpsc;

#[cfg(ffi_test)]
mod ffi_test;
#[cfg(ffi_test)]
pub use ffi_test::*;

// TODO: Consider caching method IDs here in a static variable (might need RwLock to mutate)
fn redis_value_to_java<'local>(
    env: &mut JNIEnv<'local>,
    val: Value,
    encoding_utf8: bool,
) -> JObject<'local> {
    match val {
        Value::Nil => JObject::null(),
        Value::SimpleString(str) => JObject::from(env.new_string(str).unwrap()),
        Value::Okay => JObject::from(env.new_string("OK").unwrap()),
        Value::Int(num) => env
            .new_object("java/lang/Long", "(J)V", &[num.into()])
            .unwrap(),
        Value::BulkString(data) => {
            if encoding_utf8 {
                let Ok(utf8_str) = String::from_utf8(data) else {
                    let _ = env.throw("Failed to construct UTF-8 string");
                    return JObject::null();
                };
                match env.new_string(utf8_str) {
                    Ok(string) => JObject::from(string),
                    Err(e) => {
                        let _ = env.throw(format!(
                            "Failed to construct Java UTF-8 string from Rust UTF-8 string. {:?}",
                            e
                        ));
                        JObject::null()
                    }
                }
            } else {
                let Ok(bytearr) = env.byte_array_from_slice(&data) else {
                    let _ = env.throw("Failed to allocate byte array");
                    return JObject::null();
                };
                bytearr.into()
            }
        }
        Value::Array(array) => array_to_java_array(env, array, encoding_utf8),
        Value::Map(map) => {
            let linked_hash_map = env
                .new_object("java/util/LinkedHashMap", "()V", &[])
                .unwrap();

            for (key, value) in map {
                let java_key = redis_value_to_java(env, key, encoding_utf8);
                let java_value = redis_value_to_java(env, value, encoding_utf8);
                env.call_method(
                    &linked_hash_map,
                    "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    &[(&java_key).into(), (&java_value).into()],
                )
                .unwrap();
            }

            linked_hash_map
        }
        Value::Double(float) => env
            .new_object("java/lang/Double", "(D)V", &[float.into()])
            .unwrap(),
        Value::Boolean(bool) => env
            .new_object("java/lang/Boolean", "(Z)V", &[bool.into()])
            .unwrap(),
        Value::VerbatimString { format: _, text } => JObject::from(env.new_string(text).unwrap()),
        Value::BigNumber(_num) => todo!(),
        Value::Set(array) => {
            let set = env.new_object("java/util/HashSet", "()V", &[]).unwrap();

            for elem in array {
                let java_value = redis_value_to_java(env, elem, encoding_utf8);
                env.call_method(
                    &set,
                    "add",
                    "(Ljava/lang/Object;)Z",
                    &[(&java_value).into()],
                )
                .unwrap();
            }

            set
        }
        Value::Attribute {
            data: _,
            attributes: _,
        } => todo!(),
        // Create a java `Map<String, Object>` with two keys:
        //   - "kind" which corresponds to the push type, stored as a `String`
        //   - "values" which corresponds to the array of values received, stored as `Object[]`
        // Only string messages are supported now by Redis and `redis-rs`.
        Value::Push { kind, data } => {
            // TODO rework unwraps after https://github.com/aws/glide-for-redis/pull/1601
            let hash_map = env.new_object("java/util/HashMap", "()V", &[]).unwrap();

            let kind_str = env.new_string("kind").unwrap();
            let kind_value_str = env.new_string(format!("{kind:?}")).unwrap();

            let _ = env
                .call_method(
                    &hash_map,
                    "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    &[(&kind_str).into(), (&kind_value_str).into()],
                )
                .unwrap();

            let values_str = env.new_string("values").unwrap();
            let values = array_to_java_array(env, data, encoding_utf8);

            let _ = env
                .call_method(
                    &hash_map,
                    "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    &[(&values_str).into(), (&values).into()],
                )
                .unwrap();

            hash_map
        }
    }
}

/// Convert an array of values into java array of corresponding values.
///
/// Recursively calls to [`redis_value_to_java`] for every element.
///
/// Returns an arbitrary java `Object[]`.
fn array_to_java_array<'local>(
    env: &mut JNIEnv<'local>,
    values: Vec<Value>,
    encoding_utf8: bool,
) -> JObject<'local> {
    let items: JObjectArray = env
        .new_object_array(values.len() as i32, "java/lang/Object", JObject::null())
        .unwrap();

    for (i, item) in values.into_iter().enumerate() {
        let java_value = redis_value_to_java(env, item, encoding_utf8);
        env.set_object_array_element(&items, i as i32, java_value)
            .unwrap();
    }

    items.into()
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_RedisValueResolver_valueFromPointer<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong,
) -> JObject<'local> {
    let value = unsafe { Box::from_raw(pointer as *mut Value) };
    redis_value_to_java(&mut env, *value, true)
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_RedisValueResolver_valueFromPointerBinary<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong,
) -> JObject<'local> {
    let value = unsafe { Box::from_raw(pointer as *mut Value) };
    redis_value_to_java(&mut env, *value, false)
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_SocketListenerResolver_startSocketListener<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JObject<'local> {
    let (tx, rx) = mpsc::channel::<Result<String, String>>();

    start_socket_listener(move |socket_path: Result<String, String>| {
        // Signals that thread has started
        let _ = tx.send(socket_path);
    });

    // Wait until the thread has started
    let socket_path = rx.recv();

    match socket_path {
        Ok(Ok(path)) => env.new_string(path).unwrap().into(),
        Ok(Err(error_message)) => {
            throw_java_exception(env, error_message);
            JObject::null()
        }
        Err(error) => {
            throw_java_exception(env, error.to_string());
            JObject::null()
        }
    }
}

fn throw_java_exception(mut env: JNIEnv, message: String) {
    let res = env.new_object(
        "java/lang/Exception",
        "(Ljava/lang/String;)V",
        &[(&env.new_string(message.clone()).unwrap()).into()],
    );

    match res {
        Ok(res) => {
            let _ = env.throw(JThrowable::from(res));
        }
        Err(err) => {
            error!(
                "Failed to create exception with string {}: {}",
                message,
                err.to_string()
            );
        }
    };
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_storeScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    code: JString,
) -> JObject<'local> {
    let code_str: String = env.get_string(&code).unwrap().into();
    let hash = glide_core::scripts_container::add_script(&code_str);
    JObject::from(env.new_string(hash).unwrap())
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_dropScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    hash: JString,
) {
    let hash_str: String = env.get_string(&hash).unwrap().into();
    glide_core::scripts_container::remove_script(&hash_str);
}
