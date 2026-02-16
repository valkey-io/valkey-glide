use std::sync::Arc;

use jni::objects::{GlobalRef, JObject, JString};
use jni::{JNIEnv, JavaVM};
use log::error;

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
        let mut env = match self.jvm.attach_current_thread_as_daemon() {
            Ok(env) => env,
            Err(err) => {
                error!("Failed to attach to JVM. Falling back to original host and port. {err:?}");
                return (host.to_string(), port);
            }
        };
        let host_jstring = match env.new_string(host) {
            Ok(host_jstring) => host_jstring,
            Err(err) => {
                error!("Failed to create Java string for host: {err:?}");
                return (host.to_string(), port);
            }
        };
        // Call the resolver's resolve method: ResolvedAddress resolve(String host, int port)
        let result = match env.call_method(
            self.resolver_global.as_obj(),
            "resolve",
            "(Ljava/lang/String;I)Lglide/api/models/configuration/ResolvedAddress;",
            &[
                jni::objects::JValue::Object(&host_jstring),
                jni::objects::JValue::Int(port as i32),
            ],
        ) {
            Ok(result) => result,
            Err(err) => {
                error!(
                    "Failed to call resolve method on the JVM. Falling back to original host and port. {err:?}"
                );
                return (host.to_string(), port);
            }
        };
        let Ok(resolved_address) = result.l() else {
            error!(
                "JavaAddressResolver did not return an object. Falling back to original host and port."
            );
            return (host.to_string(), port);
        };
        if resolved_address.is_null() {
            return (host.to_string(), port);
        }

        // Call succeeded with non-null value. Let's extract the values now.
        let resolved_host_obj = match env.call_method(
            &resolved_address,
            "getHost",
            "()Ljava/lang/String;",
            &[],
        ) {
            Ok(resolved_host_obj) => resolved_host_obj,
            Err(err) => {
                error!(
                    "Failed to call getHost on ResolvedAddress. Falling back to original host and port. {err:?}"
                );
                return (host.to_string(), port);
            }
        };
        let resolved_host_jobj = match resolved_host_obj.l() {
            Ok(resolved_host_jobj) => resolved_host_jobj,
            Err(err) => {
                error!(
                    "getHost did not return an object. Falling back to original host and port. {err:?}"
                );
                return (host.to_string(), port);
            }
        };
        if resolved_host_jobj.is_null() {
            error!("getHost returned null. Falling back to original host and port.");
            return (host.to_string(), port);
        }
        let resolved_host_jstr: JString = resolved_host_jobj.into();
        let resolved_host_str = match env.get_string(&resolved_host_jstr) {
            Ok(resolved_host_str) => resolved_host_str,
            Err(err) => {
                error!(
                    "Failed to convert resolved host to Rust string. Falling back to original host and port. {err:?}"
                );
                return (host.to_string(), port);
            }
        };
        let resolved_host_string = resolved_host_str.to_str().unwrap_or(host).to_string();

        let resolved_port_val = match env.call_method(&resolved_address, "getPort", "()I", &[]) {
            Ok(resolved_port_val) => resolved_port_val,
            Err(err) => {
                error!(
                    "Failed to call getPort on ResolvedAddress. Falling back to original host and port. {err:?}"
                );
                return (host.to_string(), port);
            }
        };
        let resolved_port = match resolved_port_val.i() {
            Ok(resolved_port) => resolved_port,
            Err(err) => {
                error!(
                    "getPort did not return an integer. Falling back to original host and port. {err:?}"
                );
                return (host.to_string(), port);
            }
        };
        (resolved_host_string, resolved_port as u16)
    }
}
