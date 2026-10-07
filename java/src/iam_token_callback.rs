// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::fmt::Display;
use std::sync::Arc;
use std::time::{Duration, Instant};

use jni::objects::{GlobalRef, JClass, JMethodID, JObject, JString, JValue};
use jni::sys::jlong;
use jni::{JNIEnv, JavaVM};
use log::error;

/// The Java wait must finish before glide-core's 10-second credentials-provider deadline.
const CREDENTIAL_FUTURE_TIMEOUT: Duration = Duration::from_secs(9);

/// JNI bridge to a Java `GlideCredentialProvider` instance.
///
/// Holds a `GlobalRef` to a Java `CredentialsProviderInvoker`, which in turn owns the provider and
/// a single zero-backlog daemon worker. The callback is invoked from a
/// `tokio::task::spawn_blocking` thread managed by the async token-refresh task.
/// `jvm.attach_current_thread_as_daemon()` handles the necessary JNI thread attachment. The
/// synchronous `getCredentials()` method body and its returned future share one nine-second
/// deadline. A timed-out method body is interrupted, and a worker still occupied by a provider that
/// ignored interruption causes subsequent invocations to fail immediately rather than queue.
pub struct JavaIamTokenCallback {
    jvm: Arc<JavaVM>,
    invoker_global: GlobalRef,
    submit_method_id: JMethodID,
    future_timeout: Duration,
}

impl JavaIamTokenCallback {
    /// Create a new `JavaIamTokenCallback`.
    ///
    /// # Returns
    /// `None` if the invoker, global reference, or method-ID lookup cannot be created.
    pub fn new(env: &mut JNIEnv, jvm: Arc<JavaVM>, callback: &JObject) -> Option<Self> {
        Self::new_with_timeout(env, jvm, callback, CREDENTIAL_FUTURE_TIMEOUT)
    }

    fn new_with_timeout(
        env: &mut JNIEnv,
        jvm: Arc<JavaVM>,
        callback: &JObject,
        future_timeout: Duration,
    ) -> Option<Self> {
        let bridge_class = match env.find_class("glide/internal/GlideNativeBridge") {
            Ok(class) => class,
            Err(e) => {
                clear_pending_exception(env);
                error!(
                    "Failed to find GlideNativeBridge for IAM credentials provider invoker: {e}"
                );
                return None;
            }
        };
        let invoker = match env.call_static_method(
            bridge_class,
            "createCredentialsProviderInvoker",
            "(Lglide/api/models/configuration/GlideCredentialProvider;)Lglide/internal/CredentialsProviderInvoker;",
            &[JValue::Object(callback)],
        ) {
            Ok(value) => match value.l() {
                Ok(invoker) if !invoker.is_null() => invoker,
                Ok(_) => {
                    error!("IAM credentials provider invoker factory returned null");
                    return None;
                }
                Err(e) => {
                    clear_pending_exception(env);
                    error!("IAM credentials provider invoker factory returned an invalid value: {e}");
                    return None;
                }
            },
            Err(e) => {
                let exception = take_java_exception(env, &e);
                error!(
                    "Failed to create IAM credentials provider invoker: {}",
                    exception.message
                );
                return None;
            }
        };
        let invoker_global = match env.new_global_ref(invoker) {
            Ok(global) => global,
            Err(e) => {
                clear_pending_exception(env);
                error!(
                    "Failed to create global reference for IAM credentials provider invoker: {e}"
                );
                return None;
            }
        };
        let class = match env.get_object_class(invoker_global.as_obj()) {
            Ok(class) => class,
            Err(e) => {
                clear_pending_exception(env);
                error!("Failed to get IAM credentials provider invoker class: {e}");
                return None;
            }
        };
        let submit_method_id =
            match env.get_method_id(class, "submit", "()Ljava/util/concurrent/Future;") {
                Ok(method_id) => method_id,
                Err(e) => {
                    clear_pending_exception(env);
                    error!("Failed to find submit method on IAM credentials provider invoker: {e}");
                    return None;
                }
            };

        Some(Self {
            jvm,
            invoker_global,
            submit_method_id,
            future_timeout,
        })
    }

    /// Obtain credentials through the bounded Java provider invoker.
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

        // Free every local reference when the callback returns. Capacity 40 covers both futures,
        // credentials, TimeUnit, credential fields, and a bounded throwable cause chain.
        let inner_result: Result<Result<_, IamCallbackError>, jni::errors::Error> =
            env.with_local_frame(40, |env| Ok(self.try_get_credentials_inner(env)));
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
        let deadline = Instant::now()
            .checked_add(self.future_timeout)
            .ok_or_else(|| {
                IamCallbackError::InvalidCredentials(
                    "credentials-provider timeout is too large for a monotonic deadline"
                        .to_string(),
                )
            })?;

        // submit() only hands off to the invoker's SynchronousQueue. It never runs the provider body
        // on this JNI thread and rejects immediately when its sole worker is occupied.
        // SAFETY: submit_method_id was resolved from this invoker object's class.
        let invocation_result = unsafe {
            env.call_method_unchecked(
                self.invoker_global.as_obj(),
                self.submit_method_id,
                jni::signature::ReturnType::Object,
                &[],
            )
        };
        let invocation_result = match invocation_result {
            Ok(result) => result,
            Err(err) => {
                let exception = take_java_exception(env, &err);
                if exception.is_rejected {
                    return Err(IamCallbackError::InvocationRejected(exception.message));
                }
                return Err(IamCallbackError::CallFailed(exception.message));
            }
        };
        let invocation_future = invocation_result
            .l()
            .map_err(IamCallbackError::InvalidReturn)?;
        if invocation_future.is_null() {
            return Err(IamCallbackError::InvalidCredentials(
                "credentials provider invoker returned a null invocation Future".to_string(),
            ));
        }

        let Some(body_wait_millis) = remaining_timeout_millis(deadline) else {
            cancel_future(env, &invocation_future);
            return Err(IamCallbackError::MethodBodyTimeout(self.future_timeout));
        };
        let provider_future_result = env.call_method(
            &invocation_future,
            "get",
            "(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;",
            &[
                JValue::Long(body_wait_millis),
                JValue::Object(&milliseconds),
            ],
        );
        let provider_future_result = match provider_future_result {
            Ok(result) => result,
            Err(err) => {
                let exception = take_java_exception(env, &err);
                if exception.is_timeout {
                    cancel_future(env, &invocation_future);
                    return Err(IamCallbackError::MethodBodyTimeout(self.future_timeout));
                }
                return Err(IamCallbackError::CallFailed(exception.message));
            }
        };
        let provider_future = provider_future_result
            .l()
            .map_err(IamCallbackError::InvalidReturn)?;
        if provider_future.is_null() {
            return Err(IamCallbackError::InvalidCredentials(
                "getCredentials() returned null CompletableFuture".to_string(),
            ));
        }

        let Some(future_wait_millis) = remaining_timeout_millis(deadline) else {
            cancel_future(env, &provider_future);
            return Err(IamCallbackError::FutureTimeout(self.future_timeout));
        };
        let credentials_result = env.call_method(
            &provider_future,
            "get",
            "(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;",
            &[
                JValue::Long(future_wait_millis),
                JValue::Object(&milliseconds),
            ],
        );
        let credentials_result = match credentials_result {
            Ok(result) => result,
            Err(err) => {
                let exception = take_java_exception(env, &err);
                if exception.is_timeout {
                    cancel_future(env, &provider_future);
                    return Err(IamCallbackError::FutureTimeout(self.future_timeout));
                }
                return Err(IamCallbackError::CallFailed(exception.message));
            }
        };

        let creds_obj = credentials_result
            .l()
            .map_err(IamCallbackError::InvalidReturn)?;
        if creds_obj.is_null() {
            return Err(IamCallbackError::InvalidCredentials(
                "CompletableFuture.get() returned null AwsCredentials".to_string(),
            ));
        }

        let access_key_id = get_string_field(env, &creds_obj, "getAccessKeyId")?;
        if access_key_id.trim().is_empty() {
            return Err(IamCallbackError::InvalidCredentials(
                "getCredentials() returned a blank accessKeyId".to_string(),
            ));
        }

        let secret_access_key = get_string_field(env, &creds_obj, "getSecretAccessKey")?;
        if secret_access_key.trim().is_empty() {
            return Err(IamCallbackError::InvalidCredentials(
                "getCredentials() returned a blank secretAccessKey".to_string(),
            ));
        }

        let session_token = get_nullable_string_field(env, &creds_obj, "getSessionToken")?;
        let expires_at = get_nullable_instant_field(env, &creds_obj, "getExpiresAt")?;

        Ok((access_key_id, secret_access_key, session_token, expires_at))
    }
}

impl Drop for JavaIamTokenCallback {
    fn drop(&mut self) {
        let Ok(mut env) = self.jvm.attach_current_thread_as_daemon() else {
            error!(
                "Failed to attach JVM thread while shutting down IAM credentials provider invoker"
            );
            return;
        };
        if let Err(err) = env.call_method(self.invoker_global.as_obj(), "close", "()V", &[]) {
            let exception = take_java_exception(&mut env, &err);
            error!(
                "Failed to shut down IAM credentials provider invoker: {}",
                exception.message
            );
        }
        clear_pending_exception(&mut env);
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

fn clear_pending_exception(env: &mut JNIEnv) {
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}

fn remaining_timeout_millis(deadline: Instant) -> Option<jlong> {
    let remaining = deadline.checked_duration_since(Instant::now())?;
    if remaining.is_zero() {
        return None;
    }
    // Java's timed Future.get uses whole milliseconds. Round up so a positive sub-millisecond
    // remainder never becomes the special zero-millisecond immediate timeout.
    let millis = remaining.as_nanos().saturating_add(999_999) / 1_000_000;
    jlong::try_from(millis).ok().filter(|millis| *millis > 0)
}

fn cancel_future(env: &mut JNIEnv, future: &JObject) {
    let _ = env.call_method(future, "cancel", "(Z)Z", &[JValue::Bool(1)]);
    clear_pending_exception(env);
}

struct JavaException {
    is_timeout: bool,
    is_rejected: bool,
    message: String,
}

/// Take and clear a pending Java exception before making any further JNI calls.
/// Classifies timeout and executor rejection while preserving a bounded cause-chain rendering.
fn take_java_exception(env: &mut JNIEnv, err: &jni::errors::Error) -> JavaException {
    if !env.exception_check().unwrap_or(false) {
        return JavaException {
            is_timeout: false,
            is_rejected: false,
            message: format!("(no Java exception): {err}"),
        };
    }

    let throwable = match env.exception_occurred() {
        Ok(throwable) => throwable,
        Err(_) => {
            clear_pending_exception(env);
            return JavaException {
                is_timeout: false,
                is_rejected: false,
                message: format!("(unable to read Java exception): {err}"),
            };
        }
    };
    let _ = env.exception_clear();

    let is_timeout = env
        .is_instance_of(&throwable, "java/util/concurrent/TimeoutException")
        .unwrap_or(false);
    clear_pending_exception(env);
    let is_rejected = env
        .is_instance_of(
            &throwable,
            "java/util/concurrent/RejectedExecutionException",
        )
        .unwrap_or(false);
    clear_pending_exception(env);

    let mut messages = Vec::new();
    let mut current = match env.new_local_ref(&throwable) {
        Ok(current) => current,
        Err(_) => {
            clear_pending_exception(env);
            return JavaException {
                is_timeout,
                is_rejected,
                message: format!("(unable to inspect Java exception): {err}"),
            };
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

    JavaException {
        is_timeout,
        is_rejected,
        message: if messages.is_empty() {
            format!("(no message): {err}")
        } else {
            messages.join(": ")
        },
    }
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
            let exception = take_java_exception(env, &e);
            IamCallbackError::InvalidCredentials(format!(
                "expiresAt cannot be represented as epoch milliseconds: {}",
                exception.message
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
    MethodBodyTimeout(Duration),
    FutureTimeout(Duration),
    InvocationRejected(String),
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
            IamCallbackError::MethodBodyTimeout(timeout) => write!(
                f,
                "Timed out after {} ms waiting for getCredentials() method body; invocation was cancelled with interruption",
                timeout.as_millis()
            ),
            IamCallbackError::FutureTimeout(timeout) => write!(
                f,
                "Timed out after {} ms total waiting for getCredentials() CompletableFuture; future was cancelled",
                timeout.as_millis()
            ),
            IamCallbackError::InvocationRejected(message) => write!(
                f,
                "Credentials provider invocation rejected because its single worker is still busy; no invocation was queued: {message}"
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

/// Internal JNI seams used by `IamTokenCallbackTest`. The Java declarations exist only in test
/// sources, so these do not add public Java APIs. Production callbacks always use the nine-second
/// timeout above.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_IamTokenCallbackTest_createProviderCallback(
    mut env: JNIEnv,
    _class: JClass,
    callback: JObject,
    timeout_millis: jlong,
) -> jlong {
    if timeout_millis <= 0 {
        let _ = env.throw_new(
            "java/lang/IllegalArgumentException",
            "timeoutMillis must be positive",
        );
        return 0;
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
        clear_pending_exception(&mut env);
        let _ = env.throw_new(
            "java/lang/RuntimeException",
            "Failed to initialize IAM credentials callback",
        );
        return 0;
    };

    Box::into_raw(Box::new(callback)) as jlong
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_IamTokenCallbackTest_invokeProvider<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    callback_handle: jlong,
) -> JString<'local> {
    if callback_handle == 0 {
        let _ = env.throw_new("java/lang/IllegalStateException", "Callback is closed");
        return JString::default();
    }

    // SAFETY: the test creates this handle with createProviderCallback, invokes it serially, and
    // closes it exactly once after no invocation remains active.
    let callback = unsafe { &*(callback_handle as *const JavaIamTokenCallback) };
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

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_IamTokenCallbackTest_closeProviderCallback(
    mut env: JNIEnv,
    _class: JClass,
    callback_handle: jlong,
) {
    if callback_handle == 0 {
        return;
    }
    // SAFETY: the test owns this handle and closes it exactly once after all invocations finish.
    drop(unsafe { Box::from_raw(callback_handle as *mut JavaIamTokenCallback) });
    clear_pending_exception(&mut env);
}
