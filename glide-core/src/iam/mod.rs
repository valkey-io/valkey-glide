use aws_config::BehaviorVersion;
use aws_credential_types::{Credentials, provider::ProvideCredentials};
use aws_sigv4::http_request::{
    SignableBody, SignableRequest, SignatureLocation, SigningSettings, sign,
};
use aws_sigv4::sign::v4;
use logger_core::{log_debug, log_error, log_info, log_warn};
use rand::Rng;
use std::sync::Arc;
use std::time::Duration;
use std::time::SystemTime;
use strum_macros::IntoStaticStr;
use thiserror::Error;
use tokio::sync::{Notify, RwLock};
use tokio::task::JoinHandle;
use tokio::time::{MissedTickBehavior, interval};

/// Maximum refresh interval in seconds (12 hours)
const MAX_REFRESH_INTERVAL_SECONDS: u32 = 12 * 60 * 60; // 43200 seconds
/// Default refresh interval in seconds (5 minutes)
const DEFAULT_REFRESH_INTERVAL_SECONDS: u32 = 300; // 300 seconds (5min)
/// Warning threshold for refresh interval in seconds (15 minutes)
/// Setting refresh intervals above this value may have performance consequences
const WARNING_REFRESH_INTERVAL_SECONDS: u32 = 15 * 60; // 900 seconds
/// SigV4 presign expiration (15 minutes)
const TOKEN_TTL_SECONDS: u64 = 15 * 60; // 900

/// Exponential backoff settings for token generation
const TOKEN_GEN_MAX_ATTEMPTS: u32 = 8;
const TOKEN_GEN_INITIAL_BACKOFF_MS: u64 = 100;
/// Safety cap so we never sleep unreasonably long between attempts
const TOKEN_GEN_MAX_BACKOFF_MS: u64 = 3_000;

/// Custom error type for IAM operations in Glide
#[derive(Debug, Error)]
pub enum GlideIAMError {
    /// Invalid refresh interval (must be 1 second to 12 hours)
    #[error(
        "IAM authentication error: Invalid refresh interval. Must be between 1 and {max}, got: {actual}"
    )]
    InvalidRefreshInterval { max: u32, actual: u32 },

    /// AWS credentials resolution error
    #[error("IAM authentication error: Failed to get AWS credentials: {0}")]
    CredentialsError(String),

    /// Token generation error
    #[error("IAM authentication error: Token generation failed: {0}")]
    TokenGenerationError(String),

    /// No callback error
    #[error("IAM authentication error: No token refresh callback set")]
    NoCallbackError,
}

/// AWS service type for IAM authentication
#[derive(Clone, Copy, Debug, PartialEq, Eq, IntoStaticStr)]
pub enum ServiceType {
    /// Amazon ElastiCache service
    #[strum(serialize = "elasticache")]
    ElastiCache,

    /// Amazon MemoryDB service
    #[strum(serialize = "memorydb")]
    MemoryDB,
}

/// Validate refresh interval (1 second to 12 hours, defaults to 5 minutes)
fn validate_refresh_interval(
    refresh_interval_seconds: Option<u32>,
) -> Result<Option<u32>, GlideIAMError> {
    match refresh_interval_seconds {
        Some(0) => {
            // Reject 0 as an invalid interval
            Err(GlideIAMError::InvalidRefreshInterval {
                max: MAX_REFRESH_INTERVAL_SECONDS,
                actual: 0,
            })
        }
        Some(interval) => {
            if interval >= MAX_REFRESH_INTERVAL_SECONDS {
                return Err(GlideIAMError::InvalidRefreshInterval {
                    max: MAX_REFRESH_INTERVAL_SECONDS,
                    actual: interval,
                });
            }

            // Log warning if interval is above 15 minutes
            if interval >= WARNING_REFRESH_INTERVAL_SECONDS {
                log_warn(
                    "IAM token refresh interval warning",
                    format!(
                        "Refresh interval of {} seconds ({}min) exceeds recommended maximum of {} seconds ({}min). \
                        This may increase the risk of token expiration. \
                        Consider using a shorter interval for better reliability.",
                        interval,
                        interval / 60,
                        WARNING_REFRESH_INTERVAL_SECONDS,
                        WARNING_REFRESH_INTERVAL_SECONDS / 60
                    ),
                );
            }

            Ok(Some(interval))
        }
        None => Ok(Some(DEFAULT_REFRESH_INTERVAL_SECONDS)),
    }
}

/// Get AWS credentials using the default credential chain
async fn get_signing_identity(
    region: &str,
    service_type: ServiceType,
) -> Result<aws_credential_types::Credentials, GlideIAMError> {
    let config = aws_config::defaults(BehaviorVersion::latest())
        .region(aws_config::Region::new(region.to_string()))
        .load()
        .await;

    let provider = config.credentials_provider().ok_or_else(|| {
        GlideIAMError::CredentialsError("No AWS credentials provider found".into())
    })?;

    let creds = provider
        .provide_credentials()
        .await
        .map_err(|e| GlideIAMError::CredentialsError(e.to_string()))?;

    let service_name: &'static str = service_type.into();
    Ok(Credentials::new(
        creds.access_key_id(),
        creds.secret_access_key(),
        creds.session_token().map(|s| s.to_string()),
        None,
        service_name,
    ))
}

/// Internal state structure for IAM token management
#[derive(Clone, Debug)]
struct IamTokenState {
    /// AWS region for signing requests
    region: String,
    /// ElastiCache/MemoryDB cluster name
    cluster_name: String,
    /// Username for the connection
    username: String,
    /// Service type (ElastiCache or MemoryDB)
    service_type: ServiceType,
    /// Token refresh interval in seconds
    refresh_interval_seconds: u32,
    /// Cached AWS credentials used to sign tokens (resolved once in `new()`).
    credentials: aws_credential_types::Credentials,
}

/// IAM-based token manager for ElastiCache/MemoryDB.
///
/// - Tokens: valid 15m, refreshed every 5m by default.
/// - Refresh: periodic, uses exponential backoff with ±20% jitter on failures.
/// - Failures: logged only; cached token stays valid until expiry.
/// - Thread-safe via `Arc<RwLock<...
pub struct IAMTokenManager {
    /// Cached auth token, stored in an `Arc<RwLock<String>>` to allow many concurrent readers,
    /// safe exclusive writes on refresh, and shared access across async tasks.
    cached_token: Arc<RwLock<String>>,
    /// IAM token state containing all configuration
    iam_token_state: IamTokenState,
    /// Background refresh task handle
    refresh_task: Option<JoinHandle<()>>,
    /// Shutdown signal for graceful task termination
    shutdown_notify: Arc<Notify>,
    /// Optional callback for when token is refreshed - used to update connection passwords
    token_refresh_callback: Option<Arc<dyn Fn(String) + Send + Sync>>,
}

/// Custom Debug implementation because of the callback function doesn't implement Debug
impl std::fmt::Debug for IAMTokenManager {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("IAMTokenManager")
            .field("cached_token", &"<RwLock<String>>")
            .field("iam_token_state", &self.iam_token_state)
            .field("refresh_task", &self.refresh_task.is_some())
            .field("shutdown_notify", &"<Notify>")
            .field(
                "token_refresh_callback",
                &self.token_refresh_callback.is_some(),
            )
            .finish()
    }
}

impl IAMTokenManager {
    /// Create a new IAM token manager
    ///
    /// # Arguments
    /// * `cluster_name` - The ElastiCache/MemoryDB cluster name
    /// * `username` - Username for authentication
    /// * `region` - AWS region of the cluster
    /// * `service_type` - Service type (ElastiCache or MemoryDB)
    /// * `refresh_interval_seconds` - Optional refresh interval in seconds. Defaults to 5 minutes (300 seconds).
    ///   Maximum allowed is 12 hours (43200 seconds). Values above 15 minutes (900 seconds) will log a warning
    ///   about potential performance consequences.
    /// * `token_refresh_callback` - Optional callback to be called when the token is refreshed
    pub async fn new(
        cluster_name: String,
        username: String,
        region: String,
        service_type: ServiceType,
        refresh_interval_seconds: Option<u32>,
        token_refresh_callback: Option<Arc<dyn Fn(String) + Send + Sync>>,
    ) -> Result<Self, GlideIAMError> {
        let validated_refresh_interval = validate_refresh_interval(refresh_interval_seconds)?;
        let creds = get_signing_identity(&region, service_type).await?;

        let state = IamTokenState {
            region,
            cluster_name,
            username,
            service_type,
            refresh_interval_seconds: validated_refresh_interval
                .unwrap_or(DEFAULT_REFRESH_INTERVAL_SECONDS),
            credentials: creds,
        };

        // Generate initial token using the state
        let initial_token = Self::generate_token_with_backoff(&state).await?;

        Ok(Self {
            cached_token: Arc::new(RwLock::new(initial_token)),
            iam_token_state: state,
            refresh_task: None,
            shutdown_notify: Arc::new(Notify::new()),
            token_refresh_callback,
        })
    }

    /// Start the background token refresh task
    pub fn start_refresh_task(&mut self) {
        if self.refresh_task.is_some() {
            return; // Task already running
        }

        let iam_token_state = self.iam_token_state.clone();
        let cached_token = Arc::clone(&self.cached_token);
        let shutdown_notify = Arc::clone(&self.shutdown_notify);
        let token_refresh_callback = self.token_refresh_callback.clone();

        let task = tokio::spawn(Self::token_refresh_task(
            iam_token_state,
            cached_token,
            shutdown_notify,
            token_refresh_callback,
        ));

        self.refresh_task = Some(task);
    }

    /// Background token refresh task implementation
    async fn token_refresh_task(
        iam_token_state: IamTokenState,
        cached_token: Arc<RwLock<String>>,
        shutdown_notify: Arc<Notify>,
        token_refresh_callback: Option<Arc<dyn Fn(String) + Send + Sync>>,
    ) {
        let refresh_interval = Duration::from_secs(iam_token_state.refresh_interval_seconds as u64);

        let mut interval_timer = interval(refresh_interval);
        interval_timer.set_missed_tick_behavior(MissedTickBehavior::Skip);

        // Skip the first tick since we already have an initial token
        interval_timer.tick().await;

        loop {
            tokio::select! {
                _ = interval_timer.tick() => {
                    Self::handle_token_refresh(&iam_token_state, &cached_token, &token_refresh_callback).await;
                }
                _ = shutdown_notify.notified() => {
                    log_info("IAM token refresh task shutting down", "");
                    break;
                }
            }
        }
    }

    /// Refresh cached token with backoff + jitter.
    /// On success: update token + run callback.
    /// On failure: log error, keep old token.
    async fn handle_token_refresh(
        iam_token_state: &IamTokenState,
        cached_token: &Arc<RwLock<String>>,
        token_refresh_callback: &Option<Arc<dyn Fn(String) + Send + Sync>>,
    ) {
        match Self::generate_token_with_backoff(iam_token_state).await {
            Ok(new_token) => {
                Self::set_cached_token_static(cached_token, new_token.clone()).await;

                if let Some(callback) = token_refresh_callback {
                    callback(new_token);
                } else {
                    log_error(
                        "IAM token refresh warning",
                        "No callback set for connection password update",
                    );
                }
            }
            Err(err) => {
                // Leave cached token unchanged; logs already emitted in backoff routine
                log_error(
                    "IAM token refresh failed",
                    format!("Could not refresh token after backoff: {}", err),
                );
            }
        }
    }

    /// Generate a token with exponential backoff + ±20% jitter.
    /// Retries up to `TOKEN_GEN_MAX_ATTEMPTS`, doubling backoff each time (capped).
    /// Returns token on success, last error on failure.
    async fn generate_token_with_backoff(state: &IamTokenState) -> Result<String, GlideIAMError> {
        let mut attempt: u32 = 0;
        let mut backoff_ms = TOKEN_GEN_INITIAL_BACKOFF_MS;

        loop {
            match Self::generate_token_static(state).await {
                Ok(token) => {
                    return Ok(token);
                }
                Err(e) => {
                    attempt += 1;

                    if attempt >= TOKEN_GEN_MAX_ATTEMPTS {
                        log_error(
                            "IAM token generation failed",
                            format!(
                                "Exhausted {} attempts with exponential backoff. error: {}",
                                TOKEN_GEN_MAX_ATTEMPTS, e
                            ),
                        );
                        return Err(e);
                    }

                    log_warn(
                        "IAM token generation failed",
                        format!(" {}. Retrying in {}ms", e, backoff_ms),
                    );

                    tokio::time::sleep(Duration::from_millis(backoff_ms)).await;

                    // Exponential increase with cap
                    // Add random jitter of ±20% to backoff_ms
                    let jitter = (backoff_ms as f64 * 0.2) as u64;
                    let min = backoff_ms.saturating_sub(jitter);
                    let max = backoff_ms.saturating_add(jitter);
                    let mut rng = rand::thread_rng();
                    backoff_ms = rng.gen_range(min..=max);

                    // Exponential increase with cap
                    backoff_ms = (backoff_ms.saturating_mul(2)).min(TOKEN_GEN_MAX_BACKOFF_MS);
                }
            }
        }
    }

    /// Force refresh the token immediately
    ///
    /// - Never returns errors; all failures are logged only
    pub async fn refresh_token(&self) {
        Self::handle_token_refresh(
            &self.iam_token_state,
            &self.cached_token,
            &self.token_refresh_callback,
        )
        .await;
    }

    /// Stop the background refresh task gracefully
    pub async fn stop_refresh_task(&mut self) {
        if let Some(task) = self.refresh_task.take() {
            self.shutdown_notify.notify_one();
            // Give the task a moment to shut down gracefully
            let _ = tokio::time::timeout(Duration::from_secs(5), task).await;
        }
    }

    /// Set a new cached token (static version for use in background tasks)
    async fn set_cached_token_static(cached_token: &Arc<RwLock<String>>, new_token: String) {
        let mut token_guard = cached_token.write().await;
        *token_guard = new_token;
    }

    /// Get the current cached token
    pub async fn get_token(&self) -> String {
        let token_guard = self.cached_token.read().await;
        token_guard.clone()
    }

    /// Generate IAM authentication token using SigV4 signing (valid for 15 minutes)
    async fn generate_token_static(state: &IamTokenState) -> Result<String, GlideIAMError> {
        let service_name: &'static str = state.service_type.into();
        let signing_time = SystemTime::now();
        let hostname = state.cluster_name.clone();
        let base_url = build_base_url(&hostname, &state.username);
        let identity_value = state.credentials.clone().into();

        let mut signing_settings = SigningSettings::default();
        signing_settings.signature_location = SignatureLocation::QueryParams;
        signing_settings.expires_in = Some(Duration::from_secs(TOKEN_TTL_SECONDS));

        let signing_params = v4::SigningParams::builder()
            .identity(&identity_value)
            .region(&state.region)
            .name(service_name)
            .time(signing_time)
            .settings(signing_settings)
            .build()
            .map_err(|e| {
                GlideIAMError::TokenGenerationError(format!("Failed to build signing params: {e}"))
            })?
            .into();

        // Create signable request with the simple hostname
        let signable_request = SignableRequest::new(
            "GET",
            &base_url,
            std::iter::empty(),
            SignableBody::Bytes(b""),
        )
        .map_err(|e| {
            GlideIAMError::TokenGenerationError(format!("Failed to create signable request: {e}"))
        })?;

        // Sign the request (with presigning settings, this will generate query parameters)
        let (instructions, _sig) = sign(signable_request, &signing_params)
            .map_err(|e| GlideIAMError::TokenGenerationError(format!("Failed to sign: {e}")))?
            .into_parts();

        // Build a temporary HTTP request to apply the signing instructions
        let mut req = http::Request::builder()
            .method("GET")
            .uri(&base_url)
            .header("host", &hostname)
            .body(())
            .map_err(|e| {
                GlideIAMError::TokenGenerationError(format!("Build HTTP request failed: {e}"))
            })?;

        instructions.apply_to_request_http1x(&mut req);

        // Extract the token from the signed request URI
        let token = strip_scheme(req.uri().to_string());
        log_debug("Generated new IAM token", "");
        Ok(token)
    }
}

impl Drop for IAMTokenManager {
    fn drop(&mut self) {
        // Signal shutdown to the background task
        self.shutdown_notify.notify_one();

        // Note: We can't await in Drop, so the task cleanup happens in stop_refresh_task()
        // or will be handled by the tokio runtime when the JoinHandle is dropped
    }
}

/// Build the presign base URL for the target host and user.
fn build_base_url(hostname: &str, username: &str) -> String {
    format!(
        "https://{}/?Action=connect&User={}",
        hostname,
        urlencoding::encode(username)
    )
}

/// Remove `http://` or `https://` scheme from a URL string.
fn strip_scheme(full: String) -> String {
    full.strip_prefix("https://")
        .or_else(|| full.strip_prefix("http://"))
        .unwrap_or(&full)
        .to_string()
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json;
    use serial_test::serial;
    use std::env;
    use std::fs;
    use std::sync::Once;
    use std::sync::{Arc, Mutex};
    use tokio::time::{Duration, sleep};

    const IAM_TOKENS_JSON: &str = "/tmp/iam_tokens.json";

    // This ensures the file is deleted once before all tests
    static INIT: Once = Once::new();

    fn initialize_test_environment() {
        INIT.call_once(|| {
            let _ = std::fs::remove_file(IAM_TOKENS_JSON);
            log_info("Test setup", "Cleaned up old IAM token log file");
        });
    }

    /// Helper function to set up mock AWS credentials for testing
    fn setup_test_credentials() {
        unsafe {
            env::set_var("AWS_ACCESS_KEY_ID", "test_access_key");
            env::set_var("AWS_SECRET_ACCESS_KEY", "test_secret_key");
            env::set_var("AWS_SESSION_TOKEN", "test_session_token");
        }
    }

    /// Helper function to save token to JSON file for inspection
    fn save_token_to_file(test_name: &str, token: &str, state: &IamTokenState) {
        let token_data = serde_json::json!({
            "test_name": test_name,
            "token": token,
            "region": state.region,
            "cluster_name": state.cluster_name,
            "username": state.username,
            "service_type": format!("{:?}", state.service_type),
            "refresh_interval_seconds": state.refresh_interval_seconds,
            "timestamp": std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_secs()
        });

        // Read existing content or create new array
        let mut tokens = if let Ok(content) = fs::read_to_string(IAM_TOKENS_JSON) {
            serde_json::from_str::<Vec<serde_json::Value>>(&content).unwrap_or_else(|_| Vec::new())
        } else {
            Vec::new()
        };

        tokens.push(token_data);

        // Write back to file
        if let Ok(json_string) = serde_json::to_string_pretty(&tokens) {
            let _ = fs::write(IAM_TOKENS_JSON, json_string);
        }
    }

    /// Helper function to create IAMTokenState for testing
    fn create_test_state(
        region: &str,
        cluster_name: &str,
        username: &str,
        service_type: ServiceType,
    ) -> IamTokenState {
        // Create mock credentials for testing
        let credentials = aws_credential_types::Credentials::new(
            "test_access_key",
            "test_secret_key",
            Some("test_session_token".to_string()),
            None,
            "test_provider",
        );

        IamTokenState {
            region: region.to_string(),
            cluster_name: cluster_name.to_string(),
            username: username.to_string(),
            service_type,
            refresh_interval_seconds: DEFAULT_REFRESH_INTERVAL_SECONDS,
            credentials,
        }
    }

    /// Helper function to create a test callback that logs when invoked
    fn create_test_callback() -> Arc<dyn Fn(String) + Send + Sync> {
        Arc::new(move |_token: String| {
            log_info("Refresh callback invoked!", "");
        })
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_with_callback_in_constructor() {
        initialize_test_environment();
        setup_test_credentials();

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        // Create a shared counter to track callback invocations
        let callback_counter = Arc::new(Mutex::new(0));
        let callback_counter_clone = callback_counter.clone();

        // Create a callback that increments the counter
        let callback = Arc::new(move |_token: String| {
            let mut counter = callback_counter_clone.lock().unwrap();
            *counter += 1;
            log_info("Callback invoked! ", format!("Count: {}", *counter));
        });

        // Create IAM token manager with callback provided in constructor
        let mut manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            ServiceType::ElastiCache,
            Some(2), // 2 second refresh interval for fast testing
            Some(callback),
        )
        .await
        .unwrap();

        // Start the refresh task
        manager.start_refresh_task();

        // Wait for a few refresh cycles
        sleep(Duration::from_secs(3)).await;

        // Stop the refresh task
        manager.stop_refresh_task().await;

        // Verify that the callback was invoked at least once
        let final_count = *callback_counter.lock().unwrap();
        assert!(
            final_count > 0,
            "Callback should have been invoked at least once, got: {}",
            final_count
        );

        log_info(
            "Test completed successfully!",
            format!("Callback was invoked {} times", final_count),
        );
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_manual_refresh_with_callback() {
        initialize_test_environment();
        setup_test_credentials();

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        // Create a shared flag to track callback invocation
        let callback_invoked = Arc::new(Mutex::new(false));
        let callback_invoked_clone = callback_invoked.clone();

        // Create a callback that sets the flag
        let callback = Arc::new(move |_token: String| {
            let mut invoked = callback_invoked_clone.lock().unwrap();
            *invoked = true;
            log_info("Manual refresh callback invoked!", "");
        });

        // Create IAM token manager with callback provided in constructor
        let manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            ServiceType::ElastiCache,
            None,
            Some(callback),
        )
        .await
        .unwrap();

        // Manually refresh the token
        manager.refresh_token().await;

        // Verify that the callback was invoked
        let was_invoked = *callback_invoked.lock().unwrap();
        assert!(
            was_invoked,
            "Callback should have been invoked during manual refresh"
        );

        log_info("Manual refresh test completed successfully!", "");
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_new_creates_initial_token() {
        initialize_test_environment();
        setup_test_credentials();

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        let callback = Arc::new(move |_token: String| {
            log_info("Manual refresh callback invoked!", "");
        });

        let result = IAMTokenManager::new(
            cluster_name.clone(),
            username.clone(),
            region.clone(),
            ServiceType::ElastiCache,
            None,
            Some(callback),
        )
        .await;

        assert!(result.is_ok(), "IAMTokenManager creation should succeed");

        let manager = result.unwrap();
        let token = manager.get_token().await;

        // Save token to JSON file for inspection
        let state = create_test_state(&region, &cluster_name, &username, ServiceType::ElastiCache);
        save_token_to_file(
            "test_iam_token_manager_new_creates_initial_token",
            &token,
            &state,
        );

        assert!(!token.is_empty(), "Initial token should not be empty");
        assert!(
            token.starts_with(&format!("{}/", cluster_name)),
            "Token should start with cluster name"
        );
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_get_token_returns_cached_token() {
        initialize_test_environment();
        setup_test_credentials();

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        let callback = Arc::new(move |_token: String| {
            log_info("Manual refresh callback invoked!", "");
        });

        let manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            ServiceType::ElastiCache,
            None,
            Some(callback),
        )
        .await
        .unwrap();

        let token1 = manager.get_token().await;
        let token2 = manager.get_token().await;

        assert_eq!(
            token1, token2,
            "get_token should return the same cached token"
        );
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_refresh_token_updates_cached_token() {
        initialize_test_environment();
        setup_test_credentials();

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        let callback = Arc::new(move |_token: String| {
            log_info("Manual refresh callback invoked!", "");
        });

        let manager = IAMTokenManager::new(
            cluster_name.clone(),
            username.clone(),
            region.clone(),
            ServiceType::ElastiCache,
            None,
            Some(callback),
        )
        .await
        .unwrap();

        let initial_token = manager.get_token().await;

        // Save initial token to JSON file for inspection
        let state = create_test_state(&region, &cluster_name, &username, ServiceType::ElastiCache);
        save_token_to_file(
            "test_iam_token_manager_refresh_token_updates_cached_token_initial",
            &initial_token,
            &state,
        );

        // Wait at least 1 second to ensure timestamp difference in AWS SigV4 signing
        sleep(Duration::from_secs(1)).await;

        manager.refresh_token().await;

        let new_token = manager.get_token().await;

        // Save refreshed token to JSON file for inspection
        let state = create_test_state(&region, &cluster_name, &username, ServiceType::ElastiCache);
        save_token_to_file(
            "test_iam_token_manager_refresh_token_updates_cached_token_refreshed",
            &new_token,
            &state,
        );

        // Tokens should be different due to different timestamps in signing
        assert_ne!(
            initial_token, new_token,
            "Refreshed token should be different from initial token"
        );
        assert!(
            new_token.starts_with(&format!("{}/", cluster_name)),
            "New token should still start with cluster name"
        );
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_start_and_stop_refresh_task() {
        initialize_test_environment();
        setup_test_credentials();

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        let callback = Arc::new(move |_token: String| {
            log_info("Manual refresh callback invoked!", "");
        });

        let mut manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            ServiceType::ElastiCache,
            Some(1), // 1 minute refresh interval for faster testing
            Some(callback),
        )
        .await
        .unwrap();

        // Start the refresh task
        manager.start_refresh_task();
        assert!(
            manager.refresh_task.is_some(),
            "Refresh task should be started"
        );

        // Starting again should not create a new task
        manager.start_refresh_task();
        assert!(
            manager.refresh_task.is_some(),
            "Refresh task should still exist"
        );

        // Stop the refresh task
        manager.stop_refresh_task().await;
        assert!(
            manager.refresh_task.is_none(),
            "Refresh task should be stopped"
        );
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_refresh_interval_validation() {
        initialize_test_environment();
        setup_test_credentials();

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        // Test valid refresh intervals in seconds
        let valid_intervals = [60, 900, 21600, 43199]; // 0 seconds, 1 minute, 15 minutes, 6 hours, 12 hours
        for interval in valid_intervals {
            let result = IAMTokenManager::new(
                cluster_name.clone(),
                username.clone(),
                region.clone(),
                ServiceType::ElastiCache,
                Some(interval),
                Some(create_test_callback()),
            )
            .await;

            assert!(
                result.is_ok(),
                "IAMTokenManager creation should succeed with valid interval: {interval} seconds"
            );
        }

        // Test invalid refresh intervals (greater than 43200 seconds / 12 hours)
        let invalid_intervals = [0, 43200, 86400, 172800]; // 12 hours, 24 hours, 48 hours
        for interval in invalid_intervals {
            let result = IAMTokenManager::new(
                cluster_name.clone(),
                username.clone(),
                region.clone(),
                ServiceType::ElastiCache,
                Some(interval),
                Some(create_test_callback()),
            )
            .await;

            assert!(
                result.is_err(),
                "IAMTokenManager creation should fail with invalid interval: {interval} seconds"
            );

            let error = result.unwrap_err();
            match error {
                GlideIAMError::InvalidRefreshInterval { max, actual } => {
                    assert_eq!(
                        max, MAX_REFRESH_INTERVAL_SECONDS,
                        "Max value should be 43200 seconds"
                    );
                    assert_eq!(actual, interval, "Actual value should match input interval");
                }
                _ => panic!("Expected InvalidRefreshInterval error, got: {error:?}"),
            }
        }
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_generates_new_token_every_x_seconds() {
        initialize_test_environment();
        setup_test_credentials();

        // Configurable refresh time constant (can be changed)
        const REFRESH_TIME_SECONDS: u32 = 2;

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        // Create IAMTokenManager with 5-second refresh interval
        let mut manager = IAMTokenManager::new(
            cluster_name.clone(),
            username.clone(),
            region.clone(),
            ServiceType::ElastiCache,
            Some(REFRESH_TIME_SECONDS),
            Some(create_test_callback()),
        )
        .await
        .unwrap();

        // Get initial token
        let initial_token = manager.get_token().await;
        assert!(
            !initial_token.is_empty(),
            "Initial token should not be empty"
        );

        // Save initial token to JSON file for inspection
        let state = create_test_state(&region, &cluster_name, &username, ServiceType::ElastiCache);
        save_token_to_file(
            "test_iam_token_manager_generates_new_token_every_5_seconds_initial",
            &initial_token,
            &state,
        );

        // Start the refresh task
        manager.start_refresh_task();

        // Wait for first refresh (5 seconds + small buffer)
        sleep(Duration::from_secs(REFRESH_TIME_SECONDS as u64 + 1)).await;

        let first_refresh_token = manager.get_token().await;
        assert_ne!(
            initial_token, first_refresh_token,
            "Token should be different after first refresh interval"
        );

        // Save first refreshed token to JSON file for inspection
        save_token_to_file(
            "test_iam_token_manager_generates_new_token_every_5_seconds_first_refresh",
            &first_refresh_token,
            &state,
        );

        // Wait for second refresh (another 5 seconds + small buffer)
        sleep(Duration::from_secs(REFRESH_TIME_SECONDS as u64 + 1)).await;

        let second_refresh_token = manager.get_token().await;
        assert_ne!(
            first_refresh_token, second_refresh_token,
            "Token should be different after second refresh interval"
        );
        assert_ne!(
            initial_token, second_refresh_token,
            "Second refresh token should be different from initial token"
        );

        // Save second refreshed token to JSON file for inspection
        save_token_to_file(
            "test_iam_token_manager_generates_new_token_every_5_seconds_second_refresh",
            &second_refresh_token,
            &state,
        );

        // Verify all tokens have the correct format
        for (name, token) in [
            ("initial", &initial_token),
            ("first_refresh", &first_refresh_token),
            ("second_refresh", &second_refresh_token),
        ] {
            assert!(
                token.starts_with(&format!("{}/", cluster_name)),
                "{name} token should start with cluster name"
            );
            assert!(
                token.contains("Action=connect"),
                "{name} token should contain Action=connect"
            );
            assert!(
                token.contains("X-Amz-Expires=900"),
                "{name} token should contain 15-minute expiration"
            );
            assert!(
                token.contains("X-Amz-Signature="),
                "{name} token should contain X-Amz-Signature parameter"
            );
        }

        // Stop the refresh task
    }
}
