/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */
use jni::{
    objects::{JClass, JLongArray},
    sys::jlong,
    JNIEnv,
};
use redis::Value;

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedNil<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    let redis_value = Value::Nil;
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

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

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedOkay<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    let redis_value = Value::Okay;
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedInt<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jlong,
) -> jlong {
    let redis_value = Value::Int(value);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

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

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedLongArray<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JLongArray<'local>,
) -> jlong {
    let array = java_long_array_to_value(&mut env, &value);
    let redis_value = Value::Array(array);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedMap<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    keys: JLongArray<'local>,
    values: JLongArray<'local>,
) -> jlong {
    let keys_vec = java_long_array_to_value(&mut env, &keys);
    let values_vec = java_long_array_to_value(&mut env, &values);
    let map: Vec<(Value, Value)> = keys_vec.into_iter().zip(values_vec).collect();
    let redis_value = Value::Map(map);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedDouble<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jni::sys::jdouble,
) -> jlong {
    let redis_value = Value::Double(value.into());
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedBoolean<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: jni::sys::jboolean,
) -> jlong {
    let redis_value = Value::Boolean(value != 0);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

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

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedLongSet<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JLongArray<'local>,
) -> jlong {
    let set = java_long_array_to_value(&mut env, &value);
    let redis_value = Value::Set(set);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}

fn java_long_array_to_value<'local>(
    env: &mut JNIEnv<'local>,
    array: &JLongArray<'local>,
) -> Vec<Value> {
    use jni::objects::ReleaseMode;
    let elements = unsafe {
        env.get_array_elements(array, ReleaseMode::NoCopyBack)
            .unwrap()
    };
    elements
        .iter()
        .map(|value| Value::Int(*value))
        .collect::<Vec<Value>>()
}
