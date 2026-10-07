// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::fmt::Display;
use std::sync::Arc;
use std::time::Duration;

use jni::objects::{GlobalRef, JClass, JMethodID, JObject, JString, JValue};
use jni::sys::jlong;
use jni::{JNIEnv, JavaVM};
use log::error;

/// The Java wait must finish before glide-core's 10-second credentials-provider deadline.
const CREDENTIAL_FUTURE_TIMEOUT: Duration = Duration::from_secs(9);

/// JNI bridge to a Java `GlideCredentialProvider` instance.
///
/// Holds a `GlobalRef` to the Java object so that it is not garbage-collected
/// while the Rust `IAMTokenManager` is alive. The callback is invoked from a
/// `tokio::task::spawn_blocking` thread managed by the async token-refresh task.
/// `jvm.attach_current_thread_as_daemon()` handles the necessary JNI thread attachment.
/// The interface method `getCredentials()` returns a `CompletableFuture<AwsCredentials>`;
/// the future is awaited for at most nine seconds. Cancellation after a timeout is best effort and
/// may not interrupt the provider's underlying work, but the JNI wait thread is always bounded.
pub struct JavaIamTokenCallback {
    jvm: Arc<JavaVM>,
    callback_global: GlobalRef,
    get_credentials_method_id: JMethodID,
    future_timeout: Duration,
}

impl JavaIamTokenCallback {
    /// Create a new `JavaIamTokenCallback`.
    ///
    /// # Returns
    /// `None` if the global reference or method-ID lookup fails.
    pub fn new(env: &mut JNIEnv, jvm: Arc<JavaVM>, callback: &JObject) -> Option<Self> {
        Self::new_with_timeout(env, jvm, callback, CREDENTIAL_FUTURE_TIMEOUT)
    }

    fn new_with_timeout(
        env: &mut JNIEnv,
        jvm: Arc<JavaVM>,
        callback: &JObject,
        future_timeout: Duration,
    ) -> Option<Self> {
        let callback_global = match env.new_global_ref(callback) {
            Ok(g) => g,
            Err(e) => {
                clear_pending_exception(env);
                error!("Failed to create global reference for IAM credentials callback: {e}");
                return None;
            }
        };

        let class = match env.get_object_class(callback_global.as_obj()) {
            Ok(c) => c,
            Err(e) => {
                clear_pending_exception(env);
                error!("Failed to get class of IAM credentials callback object: {e}");
                return None;
            }
        };

        // The Java interface method: CompletableFuture<AwsCredentials> getCredentials()
        let get_credentials_method_id = match env.get_method_id(
            class,
            "getCredentials",
            "()Ljava/util/concurrent/CompletableFuture;",
        ) {
            Ok(mid) => mid,
            Err(e) => {
                clear_pending_exception(env);
                error!("Failed to find 'getCredentials' method on IAM credentials callback: {e}");
                return None;
            }
        };

        Some(Self {
            jvm,
            callback_global,
            get_credentials_method_id,
            future_timeout,
        })
    }

    /// Call `getCredentials()` on the Java object.
    ///
    /// Returns `(access_key_id, secret_access_key, session_token, expires_at)`.
    fn try_get_credentials(
        &self,
    ) -> Result<
        (
            String,
            String,
            Option<String>,
            Option<std::time::SystemTime>,
        ),
        IamCallbackError,
    > {
        let mut env = self
            .jvm
            .attach_current_thread_as_daemon()
            .map_err(IamCallbackError::AttachFailed)?;

        // Free every local reference when the callback returns. Capacity 32 covers the future,
        // credentials, TimeUnit, credential fields, and a bounded throwable cause chain.
        let inner_result: Result<Result<_, IamCallbackError>, jni::errors::Error> =
            env.with_local_frame(32, |env| Ok(self.try_get_credentials_inner(env)));
        let result = inner_result
            .map_err(|e| IamCallbackError::CallFailed(format!("local frame error: {e}")))
            .and_then(|r| r);
        clear_pending_exception(&mut env);
        result
    }

    fn try_get_credentials_inner(
        &self,
        env: &mut JNIEnv,
    ) -> Result<
        (
            String,
            String,
            Option<String>,
            Option<std::time::SystemTime>,
        ),
        IamCallbackError,
    > {
        // SAFETY: method_id is pre-computed from the same object class.
        let result = unsafe {
            env.call_method_unchecked(
                self.callback_global.as_obj(),
                self.get_credentials_method_id,
                jni::signature::ReturnType::Object,
                &[],
            )
        }
        .map_err(|err| java_call_error(env, err))?;

        let future_obj = result.l().map_err(IamCallbackError::InvalidReturn)?;
        if future_obj.is_null() {
            return Err(IamCallbackError::InvalidCredentials(
                "getCredentials() returned null CompletableFuture".to_string(),
            ));
        }

        // Resolve TimeUnit.MILLISECONDS inside this local frame and wait for less than core's
        // ten-second provider deadline.
        let time_unit_class = env
            .find_class("java/util/concurrent/TimeUnit")
            .map_err(|e| {
                clear_pending_exception(env);
                IamCallbackError::InvalidReturn(e)
            })?;
        let milliseconds = env
            .get_static_field(
                time_unit_class,
                "MILLISECONDS",
                "Ljava/util/concurrent/TimeUnit;",
            )
            .map_err(|e| {
                clear_pending_exception(env);
                IamCallbackError::InvalidReturn(e)
            })?
            .l()
            .map_err(IamCallbackError::InvalidReturn)?;
        let timeout_millis = jlong::try_from(self.future_timeout.as_millis()).map_err(|_| {
            IamCallbackError::InvalidCredentials(
                "credentials-provider timeout is too large for Java".to_string(),
            )
        })?;

        let creds_result = env.call_method(
            &future_obj,
            "get",
            "(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;",
            &[JValue::Long(timeout_millis), JValue::Object(&milliseconds)],
        );
        let creds_result = match creds_result {
            Ok(result) => result,
            Err(err) => {
                let (is_timeout, message) = take_java_exception(env, &err);
                if is_timeout {
                    // CompletableFuture cancellation does not guarantee interruption of the
                    // provider's underlying work, but it releases this JNI wait thread promptly.
                    let _ = env.call_method(&future_obj, "cancel", "(Z)Z", &[JValue::Bool(1)]);
                    clear_pending_exception(env);
                    return Err(IamCallbackError::Timeout(self.future_timeout));
                }
                return Err(IamCallbackError::CallFailed(message));
            }
        };

        let creds_obj = creds_result.l().map_err(IamCallbackError::InvalidReturn)?;
        if creds_obj.is_null() {
            return Err(IamCallbackError::InvalidCredentials(
                "CompletableFuture.get() returned null AwsCredentials".to_string(),
            ));
        }

        // Extract accessKeyId via AwsCredentials.getAccessKeyId()
        let access_key_id = get_string_field(env, &creds_obj, "getAccessKeyId")?;
        if access_key_id.trim().is_empty() {
            return Err(IamCallbackError::InvalidCredentials(
                "getCredentials() returned a blank accessKeyId".to_string(),
            ));
        }

        // Extract secretAccessKey via AwsCredentials.getSecretAccessKey()
        let secret_access_key = get_string_field(env, &creds_obj, "getSecretAccessKey")?;
        if secret_access_key.trim().is_empty() {
            return Err(IamCallbackError::InvalidCredentials(
                "getCredentials() returned a blank secretAccessKey".to_string(),
            ));
        }

        // Extract optional sessionToken via AwsCredentials.getSessionToken()
        let session_token = get_nullable_string_field(env, &creds_obj, "getSessionToken")?;

        // Extract optional expiresAt via AwsCredentials.getExpiresAt()
        // Returns null if not set; otherwise a java.time.Instant.
        // We extract the epoch milliseconds via Instant.toEpochMilli().
        let expires_at = get_nullable_instant_field(env, &creds_obj, "getExpiresAt")?;

        Ok((access_key_id, secret_access_key, session_token, expires_at))
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

fn clear_pending_exception(env: &mut JNIEnv) {
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}

fn java_call_error(env: &mut JNIEnv, err: jni::errors::Error) -> IamCallbackError {
    let (_, message) = take_java_exception(env, &err);
    IamCallbackError::CallFailed(message)
}

/// Take and clear a pending Java exception before making any further JNI calls.
/// Returns whether it was a TimeoutException and a bounded rendering of its cause chain.
fn take_java_exception(env: &mut JNIEnv, err: &jni::errors::Error) -> (bool, String) {
    if !env.exception_check().unwrap_or(false) {
        return (false, format!("(no Java exception): {err}"));
    }

    let throwable = match env.exception_occurred() {
        Ok(throwable) => throwable,
        Err(_) => {
            clear_pending_exception(env);
            return (false, format!("(unable to read Java exception): {err}"));
        }
    };
    let _ = env.exception_clear();

    let is_timeout = env
        .is_instance_of(&throwable, "java/util/concurrent/TimeoutException")
        .unwrap_or(false);
    clear_pending_exception(env);

    let mut messages = Vec::new();
    let mut current = match env.new_local_ref(&throwable) {
        Ok(current) => current,
        Err(_) => {
            clear_pending_exception(env);
            return (
                is_timeout,
                format!("(unable to inspect Java exception): {err}"),
            );
        }
    };
    // Cause chains should be short. Bound traversal to protect against malformed cyclic chains and
    // to stay within the local-frame capacity.
    for _ in 0..12 {
        match env.call_method(&current, "getMessage", "()Ljava/lang/String;", &[]) {
            Ok(value) => {
                if let Ok(message_obj) = value.l()
                    && !message_obj.is_null()
                    && let Ok(message) = env.get_string(&JString::from(message_obj))
                {
                    messages.push(String::from(message));
                }
            }
            Err(_) => {
                clear_pending_exception(env);
                break;
            }
        }

        match env.call_method(&current, "getCause", "()Ljava/lang/Throwable;", &[]) {
            Ok(value) => match value.l() {
                Ok(cause) if !cause.is_null() => current = cause,
                _ => break,
            },
            Err(_) => {
                clear_pending_exception(env);
                break;
            }
        }
    }
    clear_pending_exception(env);

    let message = if messages.is_empty() {
        format!("(no message): {err}")
    } else {
        messages.join(": ")
    };
    (is_timeout, message)
}

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

/// Call a no-arg getter on `obj` that returns a nullable `java.time.Instant`.
/// Returns `Some(SystemTime)` if non-null, `None` if null.
fn get_nullable_instant_field(
    env: &mut JNIEnv,
    obj: &JObject,
    method_name: &str,
) -> Result<Option<std::time::SystemTime>, IamCallbackError> {
    let result = env
        .call_method(obj, method_name, "()Ljava/time/Instant;", &[])
        .map_err(|e| {
            if env.exception_check().unwrap_or(false) {
                let _ = env.exception_clear();
            }
            IamCallbackError::InvalidReturn(e)
        })?;
    let instant_obj = result.l().map_err(IamCallbackError::InvalidReturn)?;
    if instant_obj.is_null() {
        return Ok(None);
    }
    // Call Instant.toEpochMilli() -> long. Values that Java cannot represent as epoch milliseconds
    // and values that SystemTime cannot represent are controlled credentials errors.
    let millis_result = env
        .call_method(&instant_obj, "toEpochMilli", "()J", &[])
        .map_err(|e| {
            let (_, message) = take_java_exception(env, &e);
            IamCallbackError::InvalidCredentials(format!(
                "expiresAt cannot be represented as epoch milliseconds: {message}"
            ))
        })?;
    let epoch_millis = millis_result.j().map_err(IamCallbackError::InvalidReturn)?;
    if epoch_millis <= 0 {
        return Ok(None);
    }
    let expires_at = std::time::SystemTime::UNIX_EPOCH
        .checked_add(Duration::from_millis(epoch_millis as u64))
        .ok_or_else(|| {
            IamCallbackError::InvalidCredentials(
                "expiresAt is outside the supported SystemTime range".to_string(),
            )
        })?;
    Ok(Some(expires_at))
}

#[derive(Debug)]
enum IamCallbackError {
    AttachFailed(jni::errors::Error),
    CallFailed(String),
    InvalidReturn(jni::errors::Error),
    InvalidUtf8(std::str::Utf8Error),
    InvalidCredentials(String),
    Timeout(Duration),
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
            IamCallbackError::Timeout(timeout) => write!(
                f,
                "Timed out waiting {} ms for getCredentials() CompletableFuture",
                timeout.as_millis()
            ),
        }
    }
}

impl From<IamCallbackError> for glide_core::iam::GlideIAMError {
    fn from(e: IamCallbackError) -> Self {
        glide_core::iam::GlideIAMError::CredentialsError(e.to_string())
    }
}

// ─── Public factory ──────────────────────────────────────────────────────────

/// Wrap a `JavaIamTokenCallback` in an `Arc<dyn Fn>` suitable for passing to
/// `IAMTokenManager::new`.
pub fn make_iam_provider_callback(
    callback: JavaIamTokenCallback,
) -> glide_core::iam::CredentialsProvider {
    Arc::new(move || {
        callback
            .try_get_credentials()
            .map_err(glide_core::iam::GlideIAMError::from)
    })
}

/// Internal JNI seam used by `IamTokenCallbackTest`. The Java declaration exists only in test
/// sources, so this does not add a public Java API. Production callbacks always use the nine-second
/// timeout above.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_IamTokenCallbackTest_invokeProvider<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    callback: JObject<'local>,
    timeout_millis: jlong,
) -> JString<'local> {
    if timeout_millis <= 0 {
        let _ = env.throw_new(
            "java/lang/IllegalArgumentException",
            "timeoutMillis must be positive",
        );
        return JString::default();
    }

    let callback = env.get_java_vm().ok().and_then(|jvm| {
        JavaIamTokenCallback::new_with_timeout(
            &mut env,
            Arc::new(jvm),
            &callback,
            Duration::from_millis(timeout_millis as u64),
        )
    });
    let Some(callback) = callback else {
        let _ = env.throw_new(
            "java/lang/RuntimeException",
            "Failed to initialize IAM credentials callback",
        );
        return JString::default();
    };

    match callback.try_get_credentials() {
        Ok((access_key_id, _, _, _)) => match env.new_string(access_key_id) {
            Ok(value) => value,
            Err(err) => {
                clear_pending_exception(&mut env);
                let _ = env.throw_new("java/lang/RuntimeException", err.to_string());
                JString::default()
            }
        },
        Err(err) => {
            clear_pending_exception(&mut env);
            let _ = env.throw_new("java/lang/RuntimeException", err.to_string());
            JString::default()
        }
    }
}
