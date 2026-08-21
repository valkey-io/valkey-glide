// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::fmt::Display;
use std::sync::Arc;

use jni::objects::{GlobalRef, JMethodID, JObject, JString};
use jni::{JNIEnv, JavaVM};
use log::error;

/// JNI bridge to a Java `GlideCredentialProvider` instance.
///
/// Holds a `GlobalRef` to the Java object so that it is not garbage-collected
/// while the Rust `IAMTokenManager` is alive.  The callback is invoked from a
/// `tokio::task::spawn_blocking` thread managed by the async token-refresh task.
/// `jvm.attach_current_thread_as_daemon()` handles the necessary JNI thread attachment.
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
                if env.exception_check().unwrap_or(false) {
                    let _ = env.exception_clear();
                }
                error!("Failed to get class of IAM credentials callback object: {e}");
                return None;
            }
        };

        // The Java interface method: AwsCredentials getCredentials() throws Exception
        let get_credentials_method_id = match env.get_method_id(
            class,
            "getCredentials",
            "()Lglide/api/models/configuration/AwsCredentials;",
        ) {
            Ok(mid) => mid,
            Err(e) => {
                if env.exception_check().unwrap_or(false) {
                    let _ = env.exception_clear();
                }
                error!("Failed to find 'getCredentials' method on IAM credentials callback: {e}");
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
            .attach_current_thread_as_daemon()
            .map_err(IamCallbackError::AttachFailed)?;

        // Use a local frame so that all JNI local references created inside
        // are freed when the frame is popped.  This prevents local-ref
        // accumulation on reused tokio blocking threads across many refreshes.
        // Capacity 8 covers: result object, creds object, 3 String fields,
        // and a few intermediate refs.
        //
        // `with_local_frame` requires E: From<jni::errors::Error>; we satisfy
        // that by wrapping in `Result<Result<...>, jni::errors::Error>` and
        // flattening afterwards.
        let inner_result: Result<Result<_, IamCallbackError>, jni::errors::Error> =
            env.with_local_frame(8, |env| Ok(self.try_get_credentials_inner(env)));
        inner_result
            .map_err(|e| IamCallbackError::CallFailed(format!("local frame error: {e}")))
            .and_then(|r| r)
    }

    fn try_get_credentials_inner(
        &self,
        env: &mut JNIEnv,
    ) -> Result<(String, String, Option<String>), IamCallbackError> {
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
                        // Clear the original exception — required before making further JNI calls.
                        let _ = env.exception_clear();
                        let msg = env
                            .call_method(throwable, "getMessage", "()Ljava/lang/String;", &[])
                            .ok()
                            .and_then(|v| v.l().ok())
                            .filter(|o| !o.is_null())
                            .and_then(|jstr| {
                                env.get_string(&JString::from(jstr)).ok().map(|s| s.into())
                            });
                        // Clear any secondary exception that getMessage() or string
                        // conversion may have thrown, so the JNI thread is left clean.
                        if env.exception_check().unwrap_or(false) {
                            let _ = env.exception_clear();
                        }
                        msg
                    })
                    .unwrap_or_else(|| format!("(no message): {err}"))
            } else {
                format!("(no Java exception): {err}")
            };
            IamCallbackError::CallFailed(exception_msg)
        })?;

        // Unwrap the returned AwsCredentials object.
        let creds_obj = result.l().map_err(IamCallbackError::InvalidReturn)?;
        if creds_obj.is_null() {
            return Err(IamCallbackError::InvalidCredentials(
                "getCredentials() returned null".to_string(),
            ));
        }

        // Extract accessKeyId via AwsCredentials.getAccessKeyId()
        let access_key_id = get_string_field(env, &creds_obj, "getAccessKeyId")?;
        if access_key_id.is_empty() {
            return Err(IamCallbackError::InvalidCredentials(
                "getCredentials() returned a blank accessKeyId".to_string(),
            ));
        }

        // Extract secretAccessKey via AwsCredentials.getSecretAccessKey()
        let secret_access_key = get_string_field(env, &creds_obj, "getSecretAccessKey")?;
        if secret_access_key.is_empty() {
            return Err(IamCallbackError::InvalidCredentials(
                "getCredentials() returned a blank secretAccessKey".to_string(),
            ));
        }

        // Extract optional sessionToken via AwsCredentials.getSessionToken()
        let session_token = get_nullable_string_field(env, &creds_obj, "getSessionToken")?;

        Ok((access_key_id, secret_access_key, session_token))
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

/// Call a no-arg getter on `obj` that returns a non-null `String`.
fn get_string_field(
    env: &mut JNIEnv,
    obj: &JObject,
    method_name: &str,
) -> Result<String, IamCallbackError> {
    let result = env
        .call_method(obj, method_name, "()Ljava/lang/String;", &[])
        .map_err(|e| {
            if env.exception_check().unwrap_or(false) {
                let _ = env.exception_clear();
            }
            IamCallbackError::InvalidReturn(e)
        })?;
    let jobj = result.l().map_err(IamCallbackError::InvalidReturn)?;
    if jobj.is_null() {
        return Err(IamCallbackError::InvalidCredentials(format!(
            "{}() returned null",
            method_name
        )));
    }
    let jstr: JString = jobj.into();
    env.get_string(&jstr)
        .map_err(IamCallbackError::InvalidReturn)?
        .to_str()
        .map_err(IamCallbackError::InvalidUtf8)
        .map(|s| s.to_string())
}

/// Call a no-arg getter on `obj` that returns a nullable `String`.
fn get_nullable_string_field(
    env: &mut JNIEnv,
    obj: &JObject,
    method_name: &str,
) -> Result<Option<String>, IamCallbackError> {
    let result = env
        .call_method(obj, method_name, "()Ljava/lang/String;", &[])
        .map_err(|e| {
            if env.exception_check().unwrap_or(false) {
                let _ = env.exception_clear();
            }
            IamCallbackError::InvalidReturn(e)
        })?;
    let jobj = result.l().map_err(IamCallbackError::InvalidReturn)?;
    if jobj.is_null() {
        return Ok(None);
    }
    let jstr: JString = jobj.into();
    env.get_string(&jstr)
        .map_err(IamCallbackError::InvalidReturn)?
        .to_str()
        .map_err(IamCallbackError::InvalidUtf8)
        .map(|s| Some(s.to_string()))
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
