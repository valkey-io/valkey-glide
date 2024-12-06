use crate::errors;
use jni::{objects::JObject, JNIEnv};

const LINKED_HASHMAP: &str = "java/util/LinkedHashMap";
const LINKED_HASHMAP_PUT_SIG: &str = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";

/// Create new Java `LinkedHashMap`
pub fn new_linked_hashmap<'a>(env: &mut JNIEnv<'a>) -> Option<JObject<'a>> {
    let hash_map = env.new_object(LINKED_HASHMAP, "()V", &[]);
    let Ok(hash_map) = hash_map else {
        errors::throw_java_exception(
            env,
            errors::ExceptionType::RuntimeException,
            "Failed to allocated LinkedHashMap",
        );
        return None;
    };
    Some(hash_map)
}

/// Put `key` / `value` pair into the `map`, where both `key` and `value` are of type `&str`
/// This method is provided for convenience
pub fn put_strings<'a>(env: &mut JNIEnv<'a>, map: &mut JObject<'a>, key: &str, value: &str) {
    let Some(key) = string_to_jobject(env, key) else {
        return;
    };
    let Some(value) = string_to_jobject(env, value) else {
        return;
    };
    put_objects(env, map, key, value)
}

/// Put `key` / `value` pair into the `map`, where both `key` and `value` are of type `JObject`
pub fn put_objects<'a>(
    env: &mut JNIEnv<'a>,
    map: &mut JObject<'a>,
    key: JObject<'a>,
    value: JObject<'a>,
) {
    if env
        .call_method(
            &map,
            "put",
            LINKED_HASHMAP_PUT_SIG,
            &[(&key).into(), (&value).into()],
        )
        .is_err()
    {
        errors::throw_java_exception(
            env,
            errors::ExceptionType::RuntimeException,
            "Failed to call LinkedHashMap::put method",
        );
    }
}

/// Construct new Java string from Rust's `str`
fn string_to_jobject<'a>(env: &mut JNIEnv<'a>, string: &str) -> Option<JObject<'a>> {
    match env.new_string(string) {
        Ok(obj) => Some(JObject::from(obj)),
        Err(_) => {
            errors::throw_java_exception(
                env,
                errors::ExceptionType::RuntimeException,
                "Failed to create Java string",
            );
            None
        }
    }
}
