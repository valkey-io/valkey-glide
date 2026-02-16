use std::fmt::Display;
use std::str;
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

#[derive(Debug)]
enum AddressResolverError {
    FailedToAttachError(jni::errors::Error),
    FailedToCreateHostString(jni::errors::Error),
    FailedToCallMethod(jni::errors::Error),
    InvalidResult(jni::errors::Error),
    InvalidString(str::Utf8Error),
}

impl Display for AddressResolverError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            AddressResolverError::FailedToAttachError(err) => {
                write!(f, "Failed to attach to JVM thread: {err}")
            }
            AddressResolverError::FailedToCreateHostString(err) => {
                write!(f, "Failed to create Java string for host: {err}")
            }
            AddressResolverError::FailedToCallMethod(err) => {
                write!(f, "Failed to call method on Java resolver: {err}")
            }
            AddressResolverError::InvalidResult(err) => {
                write!(f, "Invalid result from Java resolver: {err}")
            }
            AddressResolverError::InvalidString(err) => {
                write!(f, "Failed to convert Java string to Rust string: {err}")
            }
        }
    }
}

fn map_call_method_err(err: jni::errors::Error, env: &JNIEnv) -> AddressResolverError {
    if let Ok(true) = env.exception_check() {
        let _ = env.exception_describe(); // Log the exception details
        let _ = env.exception_clear();
    }
    AddressResolverError::FailedToCallMethod(err)
}
impl JavaAddressResolver {
    fn try_resolve(&self, host: &str, port: u16) -> Result<(String, u16), AddressResolverError> {
        // Prepare to call
        let mut env = self
            .jvm
            .attach_current_thread_as_daemon()
            .map_err(AddressResolverError::FailedToAttachError)?;
        let host_jstring = env
            .new_string(host)
            .map_err(AddressResolverError::FailedToCreateHostString)?;

        // Call the resolver
        let result = env
            .call_method(
                self.resolver_global.as_obj(),
                "resolve",
                "(Ljava/lang/String;I)Lglide/api/models/configuration/ResolvedAddress;",
                &[
                    jni::objects::JValue::Object(&host_jstring),
                    jni::objects::JValue::Int(port as i32),
                ],
            )
            .map_err(|err| map_call_method_err(err, &env))?;
        let resolved_address = result.l().map_err(AddressResolverError::InvalidResult)?;
        if resolved_address.is_null() {
            return Ok((host.to_string(), port));
        }

        // Call succeeded with non-null value. Let's extract the values now.
        let resolved_host_obj = env
            .call_method(&resolved_address, "getHost", "()Ljava/lang/String;", &[])
            .map_err(|err| map_call_method_err(err, &env))?;
        let resolved_host_jobj = resolved_host_obj
            .l()
            .map_err(AddressResolverError::InvalidResult)?;
        let resolved_host_jstr: JString = resolved_host_jobj.into();
        let resolved_host_str = env
            .get_string(&resolved_host_jstr)
            .map_err(AddressResolverError::InvalidResult)?;
        let resolved_host_string = resolved_host_str
            .to_str()
            .map_err(AddressResolverError::InvalidString)?
            .to_string();
        let resolved_port_val = env
            .call_method(&resolved_address, "getPort", "()I", &[])
            .map_err(|err| map_call_method_err(err, &env))?;
        let resolved_port = resolved_port_val
            .i()
            .map_err(AddressResolverError::InvalidResult)?;
        Ok((resolved_host_string, resolved_port as u16))
    }
}

impl redis::AddressResolver for JavaAddressResolver {
    fn resolve(&self, host: &str, port: u16) -> (String, u16) {
        self.try_resolve(host, port).unwrap_or_else(|err| {
            error!("Failed to resolve address on the JVM. {err}");
            (host.to_string(), port)
        })
    }
}
