// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::fmt::Display;
use std::sync::Arc;

use jni::objects::{GlobalRef, JMethodID, JObject, JObjectArray, JString};
use jni::{JNIEnv, JavaVM};
use log::error;

/// JNI bridge to a Java `IamCredentialsProvider` instance.
///
/// Holds a `GlobalRef` to the Java object so that it is not garbage-collected
/// while the Rust `IAMTokenManager` is alive.  The callback is invoked from a
/// `tokio::task::spawn_blocking` thread managed by the async token-refresh task.
/// `jvm.attach_current_thread()` handles the necessary JNI thread attachment.
pub struct JavaIamTokenCallback {
    jvm: Arc<JavaVM>,
    callback_global: GlobalRef,
    get_credentials_method_id: JMethodID,
}

impl JavaIamTokenCallback {
    /// Create a new `JavaIamTokenCallback`.
    ///
    /// # Returns
    /// `None` if the global reference or method-ID lookup fails.
    pub fn new(env: &mut JNIEnv, jvm: Arc<JavaVM>, callback: &JObject) -> Option<Self> {
        let callback_global = match env.new_global_ref(callback) {
            Ok(g) => g,
            Err(e) => {
                error!("Failed to create global reference for IAM credentials callback: {e}");
                return None;
            }
        };

        let class = match env.get_object_class(callback_global.as_obj()) {
            Ok(c) => c,
            Err(e) => {
                error!("Failed to get class of IAM credentials callback object: {e}");
                return None;
            }
        };

        // The Java interface method: String[] getCredentials() throws Exception
        let get_credentials_method_id =
            match env.get_method_id(class, "getCredentials", "()[Ljava/lang/String;") {
                Ok(mid) => mid,
                Err(e) => {
                    error!(
                        "Failed to find 'getCredentials' method on IAM credentials callback: {e}"
                    );
                    return None;
                }
            };

        Some(Self {
            jvm,
            callback_global,
            get_credentials_method_id,
        })
    }

    /// Call `getCredentials()` on the Java object.
    ///
    /// Returns `(access_key_id, secret_access_key, session_token)`.
    fn try_get_credentials(&self) -> Result<(String, String, Option<String>), IamCallbackError> {
        let mut env = self
            .jvm
            .attach_current_thread()
            .map_err(IamCallbackError::AttachFailed)?;

        // SAFETY: method_id is pre-computed from the same object class.
        let result = unsafe {
            env.call_method_unchecked(
                self.callback_global.as_obj(),
                self.get_credentials_method_id,
                jni::signature::ReturnType::Object,
                &[],
            )
        };

        // Check for and clear any pending Java exception before inspecting the result.
        let result = result.map_err(|err| {
            // Try to capture the Java exception message before clearing it.
            let exception_msg = if env.exception_check().unwrap_or(false) {
                env.exception_occurred()
                    .ok()
                    .and_then(|throwable| {
                        let _ = env.exception_clear();
                        env.call_method(throwable, "getMessage", "()Ljava/lang/String;", &[])
                            .ok()
                            .and_then(|v| v.l().ok())
                            .filter(|o| !o.is_null())
                            .and_then(|jstr| {
                                env.get_string(&JString::from(jstr)).ok().map(|s| s.into())
                            })
                    })
                    .unwrap_or_else(|| format!("(no message): {err}"))
            } else {
                format!("(no Java exception): {err}")
            };
            IamCallbackError::CallFailed(exception_msg)
        })?;

        let obj = result.l().map_err(IamCallbackError::InvalidReturn)?;
        if obj.is_null() {
            return Err(IamCallbackError::InvalidCredentials(
                "getCredentials() returned null".to_string(),
            ));
        }
        let array_obj: JObjectArray = obj.into();

        // Expect at least 2 elements: [accessKeyId, secretAccessKey, sessionToken?]
        let len = env
            .get_array_length(&array_obj)
            .map_err(IamCallbackError::InvalidReturn)?;
        if len < 2 {
            return Err(IamCallbackError::InvalidCredentials(format!(
                "getCredentials() returned array of length {len}; expected at least 2"
            )));
        }

        let access_key_id = get_string_element(&mut env, &array_obj, 0)?;
        let secret_access_key = get_string_element(&mut env, &array_obj, 1)?;
        let session_token = if len >= 3 {
            let raw = env
                .get_object_array_element(&array_obj, 2)
                .map_err(IamCallbackError::InvalidReturn)?;
            if raw.is_null() {
                None
            } else {
                let jstr: JString = raw.into();
                let s = env
                    .get_string(&jstr)
                    .map_err(IamCallbackError::InvalidReturn)?
                    .to_str()
                    .map_err(IamCallbackError::InvalidUtf8)?
                    .to_string();
                Some(s)
            }
        } else {
            None
        };

        Ok((access_key_id, secret_access_key, session_token))
    }
}

/// Helper: extract element `idx` from a `String[]` as a Rust `String`.
fn get_string_element(
    env: &mut JNIEnv,
    array: &JObjectArray,
    idx: jni::sys::jsize,
) -> Result<String, IamCallbackError> {
    let obj = env
        .get_object_array_element(array, idx)
        .map_err(IamCallbackError::InvalidReturn)?;
    if obj.is_null() {
        return Err(IamCallbackError::InvalidCredentials(format!(
            "getCredentials() returned null at index {idx}"
        )));
    }
    let jstr: JString = obj.into();
    env.get_string(&jstr)
        .map_err(IamCallbackError::InvalidReturn)?
        .to_str()
        .map_err(IamCallbackError::InvalidUtf8)
        .map(|s| s.to_string())
}

// ─── Error type ──────────────────────────────────────────────────────────────

#[derive(Debug)]
enum IamCallbackError {
    AttachFailed(jni::errors::Error),
    CallFailed(String),
    InvalidReturn(jni::errors::Error),
    InvalidUtf8(std::str::Utf8Error),
    InvalidCredentials(String),
}

impl Display for IamCallbackError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            IamCallbackError::AttachFailed(e) => {
                write!(f, "Failed to attach to JVM thread: {e}")
            }
            IamCallbackError::CallFailed(msg) => {
                write!(f, "getCredentials() threw a Java exception: {msg}")
            }
            IamCallbackError::InvalidReturn(e) => {
                write!(f, "Invalid return value from getCredentials(): {e}")
            }
            IamCallbackError::InvalidUtf8(e) => {
                write!(f, "Non-UTF-8 string returned by getCredentials(): {e}")
            }
            IamCallbackError::InvalidCredentials(msg) => {
                write!(f, "Invalid credentials from getCredentials(): {msg}")
            }
        }
    }
}

impl From<IamCallbackError> for glide_core::iam::GlideIAMError {
    fn from(e: IamCallbackError) -> Self {
        glide_core::iam::GlideIAMError::CredentialsError(e.to_string())
    }
}

// ─── Public types ────────────────────────────────────────────────────────────

/// Shared callback type used by `IAMTokenManager`.
pub type IamCredentialsFn = Arc<
    dyn Fn() -> Result<(String, String, Option<String>), glide_core::iam::GlideIAMError>
        + Send
        + Sync,
>;

// ─── Public factory ──────────────────────────────────────────────────────────

/// Wrap a `JavaIamTokenCallback` in an `Arc<dyn Fn>` suitable for passing to
/// `IAMTokenManager::new`.
pub fn make_iam_provider_callback(callback: JavaIamTokenCallback) -> IamCredentialsFn {
    Arc::new(move || {
        callback
            .try_get_credentials()
            .map_err(glide_core::iam::GlideIAMError::from)
    })
}
