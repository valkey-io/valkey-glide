use glide_core::start_socket_listener;

use jni::objects::{JClass, JObject, JObjectArray, JThrowable};
use jni::sys::jlong;
use jni::JNIEnv;
use log::error;
use redis::Value;
use std::sync::mpsc;

fn redis_value_to_java<'local>(env: &mut JNIEnv<'local>, val: Value) -> JObject<'local> {
    match val {
        Value::Nil => JObject::null(),
        Value::SimpleString(str) => JObject::from(env.new_string(str).unwrap()),
        Value::Okay => JObject::from(env.new_string("OK").unwrap()),
        // TODO use primitive integer
        Value::Int(num) => env
            .new_object("java/lang/Long", "(J)V", &[num.into()])
            .unwrap(),
        Value::BulkString(data) => match std::str::from_utf8(data.as_ref()) {
            Ok(val) => JObject::from(env.new_string(val).unwrap()),
            Err(_err) => {
                let _ = env.throw("Error decoding Unicode data");
                JObject::null()
            }
        },
        Value::Array(array) => {
            // TODO: Consider caching the method ID here in a static variable (might need RwLock to mutate)
            let items: JObjectArray = env
                .new_object_array(array.len() as i32, "java/lang/Object", JObject::null())
                .unwrap();

            for (i, item) in array.into_iter().enumerate() {
                let java_value = redis_value_to_java(env, item);
                env.set_object_array_element(&items, i as i32, java_value)
                    .unwrap();
            }

            items.into()
        }
        Value::Map(map) => {
            let hashmap = env.new_object("java/util/HashMap", "()V", &[]).unwrap();

            for (key, value) in map {
                let java_key = redis_value_to_java(env, key);
                let java_value = redis_value_to_java(env, value);
                env.call_method(
                    &hashmap,
                    "insert",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    &[(&java_key).into(), (&java_value).into()],
                )
                .unwrap();
            }

            hashmap
        }
        Value::Double(float) => env
            .new_object("java/lang/Double", "(D)V", &[float.into_inner().into()])
            .unwrap(),
        Value::Boolean(bool) => env
            .new_object("java/lang/Boolean", "(Z)V", &[bool.into()])
            .unwrap(),
        Value::VerbatimString { format: _, text } => JObject::from(env.new_string(text).unwrap()),
        Value::BigNumber(_num) => todo!(),
        Value::Set(array) => {
            // TODO: Consider caching the method ID here in a static variable (might need RwLock to mutate)
            let items: JObjectArray = env
                .new_object_array(array.len() as i32, "java/lang/Object", JObject::null())
                .unwrap();

            for (i, item) in array.into_iter().enumerate() {
                let java_value = redis_value_to_java(env, item);
                env.set_object_array_element(&items, i as i32, java_value)
                    .unwrap();
            }

            env.new_object(
                "java/util/HashSet",
                "([Ljava/lang/Object;)V",
                &[(&items).into()],
            )
            .unwrap()
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
    let value = unsafe { Box::from_raw(pointer as *mut Value) };
    redis_value_to_java(&mut env, *value)
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

#[cfg(ffi_test)]
#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedNil<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    let redis_value = Value::Nil;
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[cfg(ffi_test)]
#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedSimpleString<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jni::objects::JString<'local>,
) -> jlong {
    let value: String = env.get_string(&value).unwrap().into();
    let redis_value = Value::SimpleString(value);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[cfg(ffi_test)]
#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedOkay<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    let redis_value = Value::Okay;
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[cfg(ffi_test)]
#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedInt<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jlong,
) -> jlong {
    let redis_value = Value::Int(value);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[cfg(ffi_test)]
#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedBulkString<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jni::objects::JByteArray<'local>,
) -> jlong {
    let value = env.convert_byte_array(&value).unwrap();
    let value = value.into_iter().collect::<Vec<u8>>();
    let redis_value = Value::BulkString(value);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[cfg(ffi_test)]
#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedLongArray<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jni::objects::JLongArray<'local>,
) -> jlong {
    use jni::objects::ReleaseMode;
    let value = unsafe {
        env.get_array_elements(&value, ReleaseMode::NoCopyBack)
            .unwrap()
    };
    let array = value
        .iter()
        .map(|val| Value::Int(*val))
        .collect::<Vec<Value>>();
    let redis_value = Value::Array(array);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[cfg(ffi_test)]
#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedDouble<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jni::sys::jdouble,
) -> jlong {
    use ordered_float::OrderedFloat;
    let redis_value = Value::Double(OrderedFloat(value));
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[cfg(ffi_test)]
#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedBoolean<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jni::sys::jboolean,
) -> jlong {
    let redis_value = Value::Boolean(value != 0);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[cfg(ffi_test)]
#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedVerbatimString<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jni::objects::JString<'local>,
) -> jlong {
    use redis::VerbatimFormat;
    let value: String = env.get_string(&value).unwrap().into();
    let redis_value = Value::VerbatimString {
        format: VerbatimFormat::Text,
        text: value,
    };
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}
