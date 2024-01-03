use glide_core::start_socket_listener;

use jni::objects::{JClass, JObject, JThrowable};
use jni::sys::jlong;
use jni::JNIEnv;
use log::error;
use redis::Value;
use std::sync::mpsc;

fn redis_value_to_java(mut env: JNIEnv, val: Value) -> JObject {
    match val {
        Value::Nil => JObject::null(),
        Value::SimpleString(str) => JObject::from(env.new_string(str).unwrap()),
        Value::Okay => JObject::from(env.new_string("OK").unwrap()),
        // TODO use primitive integer
        Value::Int(num) => env
            .new_object("java/lang/Integer", "(I)V", &[num.into()])
            .unwrap(),
        Value::BulkString(data) => match std::str::from_utf8(data.as_ref()) {
            Ok(val) => JObject::from(env.new_string(val).unwrap()),
            Err(_err) => {
                let _ = env.throw("Error decoding Unicode data");
                JObject::null()
            }
        },
        Value::Array(_array) => {
            let _ = env.throw("Not implemented");
            JObject::null()
        }
        Value::Map(_map) => todo!(),
        Value::Double(_float) => todo!(),
        Value::Boolean(_bool) => todo!(),
        Value::VerbatimString { format: _, text: _ } => todo!(),
        Value::BigNumber(_num) => todo!(),
        Value::Set(_array) => todo!(),
        Value::Attribute {
            data: _,
            attributes: _,
        } => todo!(),
        Value::Push { kind: _, data: _ } => todo!(),
    }
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_RedisValueResolver_valueFromPointer<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong,
) -> JObject<'local> {
    let value = unsafe { Box::from_raw(pointer as *mut Value) };
    redis_value_to_java(env, *value)
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
