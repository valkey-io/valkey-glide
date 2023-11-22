use babushka::start_socket_listener;

use jni::objects::{JClass, JObject, JThrowable};
use jni::JNIEnv;
use jni::sys::jlong;
use std::sync::mpsc;
use log::error;
use logger_core::Level;
use redis::Value;

fn redis_value_to_java(mut env: JNIEnv, val: Value) -> JObject {
    match val {
        Value::Nil => JObject::null(),
        Value::Status(str) => JObject::from(env.new_string(str).unwrap()),
        Value::Okay => JObject::from(env.new_string("OK").unwrap()),
        // TODO use primitive integer
        Value::Int(num) => env.new_object("java/lang/Integer", "(I)V", &[num.into()]).unwrap(),
        Value::Data(data) => match std::str::from_utf8(data.as_ref()) {
            Ok(val) => JObject::from(env.new_string(val).unwrap()),
            Err(_err) => {
                let _ = env.throw("Error decoding Unicode data");
                JObject::null()
            },
        },
        Value::Bulk(_bulk) => {
            let _ = env.throw("Not implemented");
            JObject::null()
            /*
            let elements: &PyList = PyList::new(
                py,
                bulk.into_iter()
                    .map(|item| redis_value_to_py(py, item).unwrap()),
            );
            Ok(elements.into_py(py))
            */
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_javababushka_BabushkaCoreNativeDefinitions_valueFromPointer<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong
) -> JObject<'local> {
    let value = unsafe { Box::from_raw(pointer as *mut Value) };
    redis_value_to_java(env, *value)
}

#[no_mangle]
pub extern "system" fn Java_javababushka_BabushkaCoreNativeDefinitions_startSocketListenerExternal<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>
) -> JObject<'local> {
    let (tx, rx) = mpsc::channel::<Result<String, String>>();

    //logger_core::init(Some(Level::Trace), None);

    start_socket_listener(move |socket_path : Result<String, String>| {
        // Signals that thread has started
        let _ = tx.send(socket_path);
    });

    // Wait until the thread has started
    let socket_path = rx.recv();

    match socket_path {
        Ok(Ok(path)) => {
            env.new_string(path).unwrap().into()
        },
        Ok(Err(error_message)) => {
            throw_java_exception(env, error_message);
            JObject::null()
        },
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
        &[
            (&env.new_string(message.clone()).unwrap()).into(),
        ]);

    match res {
        Ok(res) => {
            let _ = env.throw(JThrowable::from(res));
        },
        Err(err) => {
            error!("Failed to create exception with string {}: {}", message, err.to_string());
        }
    };
}
