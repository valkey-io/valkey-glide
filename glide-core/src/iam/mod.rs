use aws_config::BehaviorVersion;
use aws_credential_types::{Credentials, provider::ProvideCredentials};
use aws_sigv4::http_request::{
    SignableBody, SignableRequest, SignatureLocation, SigningSettings, sign,
};
use aws_sigv4::sign::v4;
use logger_core::{log_debug, log_error, log_info, log_warn};
use rand::Rng;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
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
pub const TOKEN_TTL_SECONDS: u64 = 15 * 60; // 900

/// Exponential backoff settings for token generation
const TOKEN_GEN_MAX_ATTEMPTS: u32 = 8;
const TOKEN_GEN_INITIAL_BACKOFF_MS: u64 = 100;
/// Safety cap so we never sleep unreasonably long between attempts
const TOKEN_GEN_MAX_BACKOFF_MS: u64 = 3_000;

/// Callback type for supplying custom AWS credentials to IAM token signing.
/// Returns `(access_key_id, secret_access_key, session_token, expires_at)` or an error.
pub type CredentialsProvider = Arc<
    dyn Fn() -> Result<
            (
                String,
                String,
                Option<String>,
                Option<std::time::SystemTime>,
            ),
            GlideIAMError,
        > + Send
        + Sync,
>;

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
    let mut loader = aws_config::defaults(BehaviorVersion::latest())
        .region(aws_config::Region::new(region.to_string()));

    // Honor AWS_ENDPOINT_URL_STS like boto3 does, but require https to avoid
    // leaking credentials in transit. `.use_fips(false)` is needed because the
    // SDK endpoint resolver otherwise rejects URLs not on its FIPS list. Scoped
    // to this loader; SigV4 presigning is unaffected. See #5967.
    if let Ok(sts_endpoint) = std::env::var("AWS_ENDPOINT_URL_STS") {
        let sts_endpoint = sts_endpoint.trim();
        if !sts_endpoint.is_empty() {
            if !sts_endpoint.starts_with("https://") {
                return Err(GlideIAMError::CredentialsError(format!(
                    "AWS_ENDPOINT_URL_STS must use https:// to protect credentials in transit, got: {sts_endpoint}"
                )));
            }
            loader = loader
                .use_fips(false)
                .endpoint_url(sts_endpoint.to_string());
        }
    }

    let config = loader.load().await;

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
        creds.expiry(),
        service_name,
    ))
}

/// Internal state structure for IAM token management
#[derive(Clone)]
pub(crate) struct IamTokenState {
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
    /// Optional custom credentials callback.
    ///
    /// When `Some`, this closure is invoked to obtain AWS credentials
    /// `(access_key_id, secret_access_key, session_token)` instead of using the
    /// default AWS credential chain. The callback must be `Send + Sync` so that
    /// it can be called from the async background refresh task.
    pub(crate) credentials_provider: Option<CredentialsProvider>,
}

impl std::fmt::Debug for IamTokenState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("IamTokenState")
            .field("region", &self.region)
            .field("cluster_name", &self.cluster_name)
            .field("username", &self.username)
            .field("service_type", &self.service_type)
            .field("refresh_interval_seconds", &self.refresh_interval_seconds)
            .field(
                "credentials_provider",
                &self
                    .credentials_provider
                    .as_ref()
                    .map(|_| "<custom callback>"),
            )
            .finish()
    }
}

/// IAM-based token manager for ElastiCache/MemoryDB.
///
/// - Tokens: valid 15m, refreshed every 5m by default.
/// - Refresh: periodic, uses exponential backoff with ±20% jitter on failures.
/// - Failures: logged only; cached token stays valid until expiry.
/// - Thread-safe via `Arc<RwLock<...>>` for token cache and `Arc<AtomicBool>` for change notification.
pub struct IAMTokenManager {
    /// Cached auth token, stored in an `Arc<RwLock<String>>` to allow many concurrent readers,
    /// safe exclusive writes on refresh, and shared access across async tasks.
    cached_token: Arc<RwLock<String>>,
    /// Timestamp of when the cached token was last generated/refreshed.
    token_created_at: Arc<RwLock<tokio::time::Instant>>,
    /// IAM token state containing all configuration
    iam_token_state: IamTokenState,
    /// Background refresh task handle
    refresh_task: Option<JoinHandle<()>>,
    /// Shutdown signal for graceful task termination
    shutdown_notify: Arc<Notify>,
    /// Atomic flag to signal when token has changed (for efficient change detection)
    token_changed: Arc<AtomicBool>,
}

/// Custom Debug implementation for IAMTokenManager
impl std::fmt::Debug for IAMTokenManager {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("IAMTokenManager")
            .field("cached_token", &"<RwLock<String>>")
            .field("iam_token_state", &self.iam_token_state)
            .field("refresh_task", &self.refresh_task.is_some())
            .field("shutdown_notify", &"<Notify>")
            .field("token_changed", &self.token_changed.load(Ordering::Relaxed))
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
    /// * `credentials_provider` - Optional custom callback to retrieve AWS credentials.
    ///   When `Some`, this closure is called instead of the default AWS credential chain.
    ///   Returns `(access_key_id, secret_access_key, session_token)` where session token
    ///   may be `None` for long-term credentials.
    ///   When `None`, the default AWS credential chain (environment variables,
    ///   `~/.aws/credentials`, EC2/ECS metadata, etc.) is used.
    pub async fn new(
        cluster_name: String,
        username: String,
        region: String,
        service_type: ServiceType,
        refresh_interval_seconds: Option<u32>,
        credentials_provider: Option<CredentialsProvider>,
    ) -> Result<Self, GlideIAMError> {
        let validated_refresh_interval = validate_refresh_interval(refresh_interval_seconds)?;

        let state = IamTokenState {
            region,
            cluster_name,
            username,
            service_type,
            refresh_interval_seconds: validated_refresh_interval
                .unwrap_or(DEFAULT_REFRESH_INTERVAL_SECONDS),
            credentials_provider,
        };

        // Generate initial token using the state
        let initial_token = Self::generate_token_with_backoff(&state).await?;

        Ok(Self {
            cached_token: Arc::new(RwLock::new(initial_token)),
            token_created_at: Arc::new(RwLock::new(tokio::time::Instant::now())),
            iam_token_state: state,
            refresh_task: None,
            shutdown_notify: Arc::new(Notify::new()),
            token_changed: Arc::new(AtomicBool::new(true)), // Initially true to trigger first AUTH
        })
    }

    /// Start the background token refresh task
    pub fn start_refresh_task(&mut self) {
        if self.refresh_task.is_some() {
            return; // Task already running
        }

        let iam_token_state = self.iam_token_state.clone();
        let cached_token = Arc::clone(&self.cached_token);
        let token_created_at = Arc::clone(&self.token_created_at);
        let shutdown_notify = Arc::clone(&self.shutdown_notify);
        let token_changed = Arc::clone(&self.token_changed);

        let task = tokio::spawn(Self::token_refresh_task(
            iam_token_state,
            cached_token,
            token_created_at,
            shutdown_notify,
            token_changed,
        ));

        self.refresh_task = Some(task);
    }

    /// Background token refresh task implementation
    async fn token_refresh_task(
        iam_token_state: IamTokenState,
        cached_token: Arc<RwLock<String>>,
        token_created_at: Arc<RwLock<tokio::time::Instant>>,
        shutdown_notify: Arc<Notify>,
        token_changed: Arc<AtomicBool>,
    ) {
        let refresh_interval = Duration::from_secs(iam_token_state.refresh_interval_seconds as u64);

        let mut interval_timer = interval(refresh_interval);
        interval_timer.set_missed_tick_behavior(MissedTickBehavior::Skip);

        // Skip the first tick since we already have an initial token
        interval_timer.tick().await;

        loop {
            tokio::select! {
                _ = interval_timer.tick() => {
                    Self::handle_token_refresh(&iam_token_state, &cached_token, &token_created_at, &token_changed).await;
                }
                _ = shutdown_notify.notified() => {
                    log_info("IAM token refresh task shutting down", "");
                    break;
                }
            }
        }
    }

    /// Refresh cached token with backoff + jitter.
    /// On success: update token + set atomic flag.
    /// On failure: log error, keep old token.
    async fn handle_token_refresh(
        iam_token_state: &IamTokenState,
        cached_token: &Arc<RwLock<String>>,
        token_created_at: &Arc<RwLock<tokio::time::Instant>>,
        token_changed: &Arc<AtomicBool>,
    ) {
        match Self::generate_token_with_backoff(iam_token_state).await {
            Ok(new_token) => {
                Self::set_cached_token_static(cached_token, new_token.clone()).await;
                {
                    let mut ts = token_created_at.write().await;
                    *ts = tokio::time::Instant::now();
                }
                token_changed.store(true, Ordering::Release);
            }
            Err(_err) => {
                // Backoff routine has already logged the failure details.
                // Do not re-log here to avoid double-logging credential-related
                // error messages.
                log_error(
                    "IAM token refresh failed",
                    "Could not refresh token after backoff. Check your GlideCredentialProvider implementation.",
                );
            }
        }
    }

    /// Generate a token with exponential backoff + ±20% jitter.
    /// Retries up to `TOKEN_GEN_MAX_ATTEMPTS`, doubling backoff each time (capped).
    /// Returns token on success, last error on failure.
    pub(crate) async fn generate_token_with_backoff(
        state: &IamTokenState,
    ) -> Result<String, GlideIAMError> {
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
                                "Exhausted {} attempts with exponential backoff. \
                                 Check your GlideCredentialProvider implementation for details.",
                                TOKEN_GEN_MAX_ATTEMPTS
                            ),
                        );
                        return Err(e);
                    }

                    log_warn(
                        "IAM token generation failed",
                        format!(
                            "Attempt {}/{}: credentials provider returned an error. \
                             Retrying in {}ms.",
                            attempt, TOKEN_GEN_MAX_ATTEMPTS, backoff_ms
                        ),
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
            &self.token_created_at,
            &self.token_changed,
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

    /// Check if token has changed since last check
    pub fn token_changed(&self) -> bool {
        self.token_changed.load(Ordering::Acquire)
    }

    /// Clear the token changed flag after handling the change
    pub fn clear_token_changed(&self) {
        self.token_changed.store(false, Ordering::Release)
    }

    /// Create a lightweight handle to the token cache for use by the reconnection path.
    ///
    /// The returned handle shares the same `Arc`s as this manager, so any token
    /// refresh performed by the background task is immediately visible through
    /// the handle without requiring a reference back to the full `IAMTokenManager`.
    pub fn get_token_handle(&self) -> crate::client::IAMTokenHandle {
        crate::client::IAMTokenHandle {
            cached_token: Arc::clone(&self.cached_token),
            token_created_at: Arc::clone(&self.token_created_at),
            iam_token_state: self.iam_token_state.clone(),
        }
    }

    /// Generate IAM authentication token using SigV4 signing (valid for 15 minutes)
    async fn generate_token_static(state: &IamTokenState) -> Result<String, GlideIAMError> {
        let service_name: &'static str = state.service_type.into();
        let signing_time = SystemTime::now();
        let hostname = state.cluster_name.clone();
        let base_url = build_base_url(&hostname, &state.username);

        // Fetch fresh credentials on every token generation to handle credential rotation
        // (e.g., EC2 instance profile credentials rotate every ~6 hours).
        // When a custom credentials callback is configured, use it; otherwise fall back to
        // the default AWS credential chain.
        let creds = if let Some(provider) = &state.credentials_provider {
            let provider = Arc::clone(provider);
            // Bound the callback with a timeout so a slow or hung credentials
            // provider (e.g. an unreachable Vault endpoint) cannot block token
            // refresh indefinitely.  10 seconds is generous for a network round
            // trip while still being short enough to surface the problem quickly.
            const CREDENTIALS_CALLBACK_TIMEOUT: Duration = Duration::from_secs(10);
            let (access_key_id, secret_access_key, session_token, expires_at) =
                tokio::time::timeout(
                    CREDENTIALS_CALLBACK_TIMEOUT,
                    tokio::task::spawn_blocking(move || provider()),
                )
                .await
                .map_err(|_| {
                    GlideIAMError::CredentialsError(format!(
                        "Custom credentials callback did not return within {:?}. \
                         Check your GlideCredentialProvider implementation.",
                        CREDENTIALS_CALLBACK_TIMEOUT
                    ))
                })?
                .map_err(|e| {
                    GlideIAMError::CredentialsError(format!("spawn_blocking panicked: {e}"))
                })??;
            aws_credential_types::Credentials::new(
                access_key_id,
                secret_access_key,
                session_token,
                expires_at,
                "glide-custom-credentials",
            )
        } else {
            get_signing_identity(&state.region, state.service_type).await?
        };
        let identity_value = creds.into();

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
        IamTokenState {
            region: region.to_string(),
            cluster_name: cluster_name.to_string(),
            username: username.to_string(),
            service_type,
            refresh_interval_seconds: DEFAULT_REFRESH_INTERVAL_SECONDS,
            credentials_provider: None,
        }
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_with_atomic_flag() {
        initialize_test_environment();
        setup_test_credentials();

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        // Create IAM token manager
        let mut manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            ServiceType::ElastiCache,
            Some(2), // 2 second refresh interval for fast testing
            None,    // credentials_provider
        )
        .await
        .unwrap();

        // Initially, token_changed should be true (to trigger first AUTH)
        assert!(
            manager.token_changed(),
            "Initial token_changed should be true"
        );

        // Clear the flag
        manager.clear_token_changed();
        assert!(
            !manager.token_changed(),
            "After clear, token_changed should be false"
        );

        // Start the refresh task
        manager.start_refresh_task();

        // Wait for a refresh cycle
        sleep(Duration::from_secs(3)).await;

        // After refresh, flag should be true again
        assert!(
            manager.token_changed(),
            "After refresh, token_changed should be true"
        );

        // Stop the refresh task
        manager.stop_refresh_task().await;

        log_info(
            "Test completed successfully!",
            "Atomic flag working as expected",
        );
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_manual_refresh_sets_flag() {
        initialize_test_environment();
        setup_test_credentials();

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        // Create IAM token manager
        let manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            ServiceType::ElastiCache,
            None,
            None, // credentials_provider
        )
        .await
        .unwrap();

        // Clear the initial flag
        manager.clear_token_changed();
        assert!(!manager.token_changed(), "Flag should be false after clear");

        // Manually refresh the token
        manager.refresh_token().await;

        // Verify that the flag was set
        assert!(
            manager.token_changed(),
            "Flag should be true after manual refresh"
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

        let result = IAMTokenManager::new(
            cluster_name.clone(),
            username.clone(),
            region.clone(),
            ServiceType::ElastiCache,
            None,
            None, // credentials_provider
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

        let manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            ServiceType::ElastiCache,
            None,
            None, // credentials_provider
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

        let manager = IAMTokenManager::new(
            cluster_name.clone(),
            username.clone(),
            region.clone(),
            ServiceType::ElastiCache,
            None,
            None, // credentials_provider
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

        let mut manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            ServiceType::ElastiCache,
            Some(1), // 1 minute refresh interval for faster testing
            None,    // credentials_provider
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
        let valid_intervals = [60, 900, 21600, 43199]; // 1 minute, 15 minutes, 6 hours, 12 hours - 1 sec
        for interval in valid_intervals {
            let result = IAMTokenManager::new(
                cluster_name.clone(),
                username.clone(),
                region.clone(),
                ServiceType::ElastiCache,
                Some(interval),
                None, // credentials_provider
            )
            .await;

            assert!(
                result.is_ok(),
                "IAMTokenManager creation should succeed with valid interval: {interval} seconds"
            );
        }

        // Test invalid refresh intervals (greater than 43200 seconds / 12 hours)
        let invalid_intervals = [0, 43200, 86400, 172800]; // 0, 12 hours, 24 hours, 48 hours
        for interval in invalid_intervals {
            let result = IAMTokenManager::new(
                cluster_name.clone(),
                username.clone(),
                region.clone(),
                ServiceType::ElastiCache,
                Some(interval),
                None, // credentials_provider
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

        // Create IAMTokenManager with 2-second refresh interval
        let mut manager = IAMTokenManager::new(
            cluster_name.clone(),
            username.clone(),
            region.clone(),
            ServiceType::ElastiCache,
            Some(REFRESH_TIME_SECONDS),
            None, // credentials_provider
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

    #[tokio::test]
    #[serial]
    async fn test_get_signing_identity_honors_aws_endpoint_url_sts() {
        initialize_test_environment();
        setup_test_credentials();

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-gov-west-1".to_string();

        // A populated AWS_ENDPOINT_URL_STS override must not break credential
        // acquisition when static credentials are available.
        unsafe {
            env::set_var(
                "AWS_ENDPOINT_URL_STS",
                "https://sts.us-gov-west-1.amazonaws.com",
            );
        }

        let with_override = IAMTokenManager::new(
            cluster_name.clone(),
            username.clone(),
            region.clone(),
            ServiceType::ElastiCache,
            None,
            None, // credentials_provider
        )
        .await;
        assert!(
            with_override.is_ok(),
            "IAMTokenManager creation should succeed when AWS_ENDPOINT_URL_STS is set: {:?}",
            with_override.err(),
        );
        let token = with_override.unwrap().get_token().await;
        assert!(
            token.starts_with(&format!("{}/", cluster_name)),
            "token should be generated with override set"
        );

        // Empty and whitespace-only values are treated as unset.
        for blank in ["", "   \t  "] {
            unsafe {
                env::set_var("AWS_ENDPOINT_URL_STS", blank);
            }
            let result = IAMTokenManager::new(
                cluster_name.clone(),
                username.clone(),
                region.clone(),
                ServiceType::ElastiCache,
                None,
                None, // credentials_provider
            )
            .await;
            assert!(
                result.is_ok(),
                "IAMTokenManager creation should succeed when AWS_ENDPOINT_URL_STS is {:?}: {:?}",
                blank,
                result.err(),
            );
        }

        // Non-https overrides are rejected to prevent leaking credentials.
        unsafe {
            env::set_var("AWS_ENDPOINT_URL_STS", "http://sts.example.com");
        }
        let with_http = IAMTokenManager::new(
            cluster_name.clone(),
            username.clone(),
            region.clone(),
            ServiceType::ElastiCache,
            None,
            None, // credentials_provider
        )
        .await;
        assert!(
            matches!(
                with_http,
                Err(GlideIAMError::CredentialsError(ref msg)) if msg.contains("https")
            ),
            "IAMTokenManager creation should fail with CredentialsError mentioning https when AWS_ENDPOINT_URL_STS uses http://, got: {:?}",
            with_http.err(),
        );

        unsafe {
            env::remove_var("AWS_ENDPOINT_URL_STS");
        }
    }

    /// Test that a custom credentials callback is used when provided.
    ///
    /// The callback returns hard-coded mock credentials.  No real AWS account
    /// is needed – the SigV4 signing step accepts any non-empty key material.
    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_with_custom_callback() {
        initialize_test_environment();

        let callback: CredentialsProvider = Arc::new(|| {
            Ok((
                "test_access_key".to_string(),
                "test_secret_key".to_string(),
                Some("test_session_token".to_string()),
                None, // no expiry
            ))
        });

        let result = IAMTokenManager::new(
            "test-cluster".to_string(),
            "test-user".to_string(),
            "us-east-1".to_string(),
            ServiceType::ElastiCache,
            None,
            Some(callback),
        )
        .await;

        assert!(
            result.is_ok(),
            "IAMTokenManager creation with custom callback should succeed: {:?}",
            result.err()
        );

        let manager = result.unwrap();
        let token = manager.get_token().await;
        assert!(
            !token.is_empty(),
            "Token generated via callback should not be empty"
        );
        assert!(
            token.starts_with("test-cluster/"),
            "Token should start with cluster name, got: {token}"
        );
        assert!(
            token.contains("Action=connect"),
            "Token should contain Action=connect"
        );
        assert!(
            token.contains("X-Amz-Signature="),
            "Token should contain X-Amz-Signature"
        );
    }

    /// Test that an error returned by the custom callback propagates correctly.
    ///
    /// The callback always returns a `CredentialsError`; we verify that
    /// `IAMTokenManager::new` surfaces that error rather than silently swallowing it.
    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_callback_error_propagates() {
        initialize_test_environment();

        let callback: CredentialsProvider = Arc::new(|| {
            Err(GlideIAMError::CredentialsError(
                "injected credential failure".to_string(),
            ))
        });

        let result = IAMTokenManager::new(
            "test-cluster".to_string(),
            "test-user".to_string(),
            "us-east-1".to_string(),
            ServiceType::ElastiCache,
            None,
            Some(callback),
        )
        .await;

        assert!(
            result.is_err(),
            "IAMTokenManager creation should fail when callback returns an error"
        );

        match result.unwrap_err() {
            GlideIAMError::CredentialsError(msg) => {
                assert!(
                    msg.contains("injected credential failure"),
                    "Error message should propagate the callback's message, got: {msg}"
                );
            }
            other => panic!("Expected CredentialsError, got: {other:?}"),
        }
    }

    /// Verify that the default credential-chain path (no callback) still works.
    ///
    /// This is a regression guard: the existing test
    /// `test_iam_token_manager_new_creates_initial_token` already covers this,
    /// but we add an explicit assertion here to document the expectation.
    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_default_path_when_no_callback() {
        initialize_test_environment();
        // Set standard env-var credentials so the default chain resolves.
        setup_test_credentials();

        let result = IAMTokenManager::new(
            "test-cluster".to_string(),
            "test-user".to_string(),
            "us-east-1".to_string(),
            ServiceType::ElastiCache,
            None,
            None, // <-- no callback: default AWS credential chain
        )
        .await;

        assert!(
            result.is_ok(),
            "Default credential chain path should succeed when env-var creds are set: {:?}",
            result.err()
        );
        let token = result.unwrap().get_token().await;
        assert!(
            !token.is_empty(),
            "Token from default chain should not be empty"
        );
        assert!(
            token.starts_with("test-cluster/"),
            "Token should start with cluster name"
        );
    }

    /// Test that the custom callback is invoked again after `refresh_token()`.
    ///
    /// Uses an `AtomicUsize` counter shared between the callback closure and
    /// the test body.  After the initial token is generated (counter ≥ 1) we
    /// call `refresh_token()` and assert the counter increases.
    #[tokio::test]
    #[serial]
    async fn test_custom_callback_invoked_on_manual_refresh() {
        initialize_test_environment();

        let call_count = Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let call_count_clone = Arc::clone(&call_count);

        let callback: CredentialsProvider = Arc::new(move || {
            call_count_clone.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
            Ok((
                "test_access_key".to_string(),
                "test_secret_key".to_string(),
                Some("test_session_token".to_string()),
                None,
            ))
        });

        let manager = IAMTokenManager::new(
            "test-cluster".to_string(),
            "test-user".to_string(),
            "us-east-1".to_string(),
            ServiceType::ElastiCache,
            None,
            Some(callback),
        )
        .await
        .expect("IAMTokenManager creation should succeed");

        let after_new = call_count.load(std::sync::atomic::Ordering::SeqCst);
        assert!(after_new >= 1, "Callback should be invoked during new()");

        manager.refresh_token().await;

        let after_refresh = call_count.load(std::sync::atomic::Ordering::SeqCst);
        assert!(
            after_refresh > after_new,
            "Callback should be invoked again after refresh_token() (before={after_new}, after={after_refresh})"
        );
    }

    /// Test that the custom callback is invoked by the background refresh task.
    ///
    /// Starts the background refresh task with a 1-second interval, waits up to
    /// 5 seconds for a second invocation, then shuts down.
    #[tokio::test]
    #[serial]
    async fn test_custom_callback_invoked_on_scheduled_refresh() {
        initialize_test_environment();

        let call_count = Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let call_count_clone = Arc::clone(&call_count);

        let callback: CredentialsProvider = Arc::new(move || {
            call_count_clone.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
            Ok((
                "test_access_key".to_string(),
                "test_secret_key".to_string(),
                Some("test_session_token".to_string()),
                None,
            ))
        });

        let mut manager = IAMTokenManager::new(
            "test-cluster".to_string(),
            "test-user".to_string(),
            "us-east-1".to_string(),
            ServiceType::ElastiCache,
            Some(1), // 1-second refresh interval
            Some(callback),
        )
        .await
        .expect("IAMTokenManager creation should succeed");

        let after_new = call_count.load(std::sync::atomic::Ordering::SeqCst);
        assert!(after_new >= 1, "Callback should be invoked during new()");

        manager.start_refresh_task();

        // Poll for up to 5 seconds for the background task to invoke the callback.
        let deadline = std::time::Instant::now() + Duration::from_secs(5);
        loop {
            let current = call_count.load(std::sync::atomic::Ordering::SeqCst);
            if current > after_new {
                break;
            }
            if std::time::Instant::now() >= deadline {
                panic!(
                    "Background refresh task did not invoke the callback within 5 seconds \
                     (after_new={after_new}, current={current})"
                );
            }
            tokio::time::sleep(Duration::from_millis(100)).await;
        }

        // Stop the background task to prevent it from racing with subsequent
        // serial tests that mutate environment variables via env::set_var.
        manager.stop_refresh_task().await;
    }
}
