use std::sync::Arc;

use jni::objects::{GlobalRef, JObject};
use jni::{JNIEnv, JavaVM};

/// Java-specific implementation of the AddressResolver trait.
/// This struct holds a GlobalRef to the Java AddressResolver object, ensuring it
/// won't be garbage collected while the resolver is in use.
pub struct JavaAddressResolver {
    jvm: Arc<JavaVM>,
    resolver_global: GlobalRef,
}

impl JavaAddressResolver {
    /// Creates a new JavaAddressResolver by creating a global reference to the Java object.
    /// Returns None if the global reference cannot be created.
    pub fn new(env: &mut JNIEnv, jvm: Arc<JavaVM>, resolver: &JObject) -> Option<Self> {
        match env.new_global_ref(resolver) {
            Ok(resolver_global) => Some(Self {
                jvm,
                resolver_global,
            }),
            Err(e) => {
                log::error!("Failed to create global reference for address resolver: {e}");
                None
            }
        }
    }
}

impl std::fmt::Debug for JavaAddressResolver {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "JavaAddressResolver {{ resolver: <Java object> }}")
    }
}

impl redis::AddressResolver for JavaAddressResolver {
    fn resolve(&self, host: &str, port: u16) -> (String, u16) {
        // Try to attach to JVM and call the Java resolver
        if let Ok(mut env) = self.jvm.attach_current_thread_as_daemon() {
            // Call the resolver's resolve method: ResolvedAddress resolve(String host, int port)
            if let Ok(host_jstring) = env.new_string(host)
                && let Ok(result) = env.call_method(
                    self.resolver_global.as_obj(),
                    "resolve",
                    "(Ljava/lang/String;I)Lglide/api/models/configuration/ResolvedAddress;",
                    &[
                        jni::objects::JValue::Object(&host_jstring),
                        jni::objects::JValue::Int(port as i32),
                    ],
                )
                && let Ok(resolved_address) = result.l()
                && !resolved_address.is_null()
            {
                // Get the resolved host and port from the ResolvedAddress object
                if let Ok(resolved_host_obj) =
                    env.call_method(&resolved_address, "getHost", "()Ljava/lang/String;", &[])
                    && let Ok(resolved_host_jobj) = resolved_host_obj.l()
                    && !resolved_host_jobj.is_null()
                    && let Ok(resolved_port_val) =
                        env.call_method(&resolved_address, "getPort", "()I", &[])
                    && let Ok(resolved_port) = resolved_port_val.i()
                {
                    let resolved_host_jstr: jni::objects::JString = resolved_host_jobj.into();
                    if let Ok(resolved_host_str) = env.get_string(&resolved_host_jstr) {
                        let resolved_host_string =
                            resolved_host_str.to_str().unwrap_or(host).to_string();
                        return (resolved_host_string, resolved_port as u16);
                    }
                }
            }
        }
        // Fallback: return original address if resolution fails
        (host.to_string(), port)
    }
}
