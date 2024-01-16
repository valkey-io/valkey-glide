use jni::{objects::JClass, sys::jlong, JNIEnv};
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

#[no_mangle]
pub extern "system" fn Java_glide_ffi_FfiTest_createLeakedMap<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    let mut map: Vec<(Value, Value)> = Vec::new();
    map.push((Value::Int(1i64), Value::Int(2i64)));
    map.push((Value::Int(3i64), Value::SimpleString("hi".to_string())));
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
    value: jni::objects::JLongArray<'local>,
) -> jlong {
    use jni::objects::ReleaseMode;
    let value = unsafe {
        env.get_array_elements(&value, ReleaseMode::NoCopyBack)
            .unwrap()
    };
    let set = value
        .iter()
        .map(|val| Value::Int(*val))
        .collect::<Vec<Value>>();
    let redis_value = Value::Set(set);
    Box::leak(Box::new(redis_value)) as *mut Value as jlong
}
