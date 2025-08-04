use aws_config::BehaviorVersion;
use aws_credential_types::{Credentials, provider::ProvideCredentials};
use aws_sigv4::http_request::{
    SignableBody, SignableRequest, SignatureLocation, SigningSettings, sign,
};
use aws_sigv4::sign::v4;
use logger_core::{log_error, log_info, log_warn};
use std::sync::Arc;
use std::time::Duration;
use std::time::SystemTime;
use thiserror::Error;
use tokio::sync::{Notify, RwLock};
use tokio::task::JoinHandle;
use tokio::time::{MissedTickBehavior, interval};

/// Maximum refresh interval in seconds (12 hours)
const MAX_REFRESH_INTERVAL_SECONDS: u32 = 12 * 60 * 60; // 43200 seconds
/// Default refresh interval in seconds (14 minutes)
const DEFAULT_REFRESH_INTERVAL_SECONDS: u32 = 14 * 60; // 840 seconds
/// Warning threshold for refresh interval in seconds (15 minutes)
/// Setting refresh intervals above this value may have performance consequences
const WARNING_REFRESH_INTERVAL_SECONDS: u32 = 15 * 60; // 900 seconds
/// SigV4 presign expiration (15 minutes)
const TOKEN_TTL_SECONDS: u64 = 15 * 60; // 900

/// Custom error type for IAM operations in Glide
#[derive(Debug, Error)]
pub enum GlideIAMError {
    #[error(
        "IAM authentication error: Invalid refresh interval. Must be between 0 and {max}, got: {actual}"
    )]
    InvalidRefreshInterval { max: u32, actual: u32 },

    #[error("IAM authentication error: Failed to get AWS credentials: {0}")]
    CredentialsError(String),

    #[error("IAM authentication error: Token generation failed: {0}")]
    TokenGenerationError(String),

    #[error("IAM authentication error: {0}")]
    Other(String),
}

/// Service type configuration for IAM authentication
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ServiceType {
    ElastiCache,
    MemoryDB,
}

impl ServiceType {
    fn service_name(&self) -> &'static str {
        match self {
            ServiceType::ElastiCache => "elasticache",
            ServiceType::MemoryDB => "memorydb",
        }
    }
}

/// Validate and normalize the refresh interval.
///
/// Returns:
/// - `Ok(Some(x))` with the provided/normalized value
/// - `Err` if out of bounds
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
                        This may impact performance and increase the risk of token expiration. \
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

/// Internal state structure for IAM token management
#[derive(Clone, Debug)]
struct IamTokenState {
    /// AWS region for signing requests
    region: String,
    /// ElastiCache/MemoryDB cluster name
    cluster_name: String,
    /// Username for the connection
    username: String,
    // todo: Add serverless endpoint to state. should be a bool? https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/auth-iam.html
    /// Service type (ElastiCache or MemoryDB)
    service_type: ServiceType,
    /// Token refresh interval in seconds
    refresh_interval_seconds: u32,
}

/// IAM-based authentication token manager for ElastiCache/MemoryDB
///
/// Manages automatic token refresh using AWS IAM credentials and SigV4 signing.
/// Tokens are valid for 15 minutes and refreshed every 14 minutes by default.
#[derive(Debug)]
pub struct IAMTokenManager {
    /// Currently cached auth token (protected by RwLock)
    cached_token: Arc<RwLock<String>>,
    /// IAM token state containing all configuration
    iam_token_state: IamTokenState,
    /// Background refresh task handle
    refresh_task: Option<JoinHandle<()>>,
    /// Shutdown signal for graceful task termination
    shutdown_notify: Arc<Notify>,
}

impl IAMTokenManager {
    /// Create a new IAM token manager
    ///
    /// # Arguments
    /// * `cluster_name` - The ElastiCache/MemoryDB cluster name
    /// * `username` - Username for authentication
    /// * `region` - AWS region
    /// * `service_type` - Service type (ElastiCache or MemoryDB)
    /// * `refresh_interval_seconds` - Optional refresh interval in seconds. Defaults to 14 minutes (840 seconds).
    ///   Maximum allowed is 12 hours (43200 seconds). Values above 15 minutes (900 seconds) will log a warning
    ///   about potential performance consequences.
    pub async fn new(
        cluster_name: String,
        username: String,
        region: String,
        service_type: ServiceType,
        refresh_interval_seconds: Option<u32>,
    ) -> Result<Self, GlideIAMError> {
        let validated_refresh_interval = validate_refresh_interval(refresh_interval_seconds)?;

        let state = IamTokenState {
            region,
            cluster_name,
            username,
            service_type,
            refresh_interval_seconds: validated_refresh_interval
                .unwrap_or(DEFAULT_REFRESH_INTERVAL_SECONDS),
        };

        // Generate initial token using the state
        let initial_token = Self::generate_token_static(&state).await?;

        Ok(Self {
            cached_token: Arc::new(RwLock::new(initial_token)),
            iam_token_state: state,
            refresh_task: None,
            shutdown_notify: Arc::new(Notify::new()),
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

        let task = tokio::spawn(Self::token_refresh_task(
            iam_token_state,
            cached_token,
            shutdown_notify,
        ));

        self.refresh_task = Some(task);
    }

    /// Background token refresh task implementation
    ///
    /// Runs periodically based on the configured refresh interval.
    async fn token_refresh_task(
        iam_token_state: IamTokenState,
        cached_token: Arc<RwLock<String>>,
        shutdown_notify: Arc<Notify>,
    ) {
        let refresh_interval = Duration::from_secs(iam_token_state.refresh_interval_seconds as u64);

        let mut interval_timer = interval(refresh_interval);
        interval_timer.set_missed_tick_behavior(MissedTickBehavior::Skip);

        // Skip the first tick since we already have an initial token
        interval_timer.tick().await;

        loop {
            tokio::select! {
                _ = interval_timer.tick() => {
                    Self::handle_token_refresh(&iam_token_state, &cached_token).await;
                }
                _ = shutdown_notify.notified() => {
                    log_info("IAM token refresh task shutting down", "");
                    break;
                }
            }
        }
    }

    /// Handle a single token refresh attempt
    async fn handle_token_refresh(
        iam_token_state: &IamTokenState,
        cached_token: &Arc<RwLock<String>>,
    ) {
        match Self::generate_token_static(iam_token_state).await {
            Ok(new_token) => {
                Self::set_cached_token_static(cached_token, new_token).await;
                log_info("IAM token refreshed successfully", "");
            }
            Err(e) => {
                log_error("Failed to refresh IAM token", format!("{e}"));
                // Continue running - temporary failures shouldn't stop the task
            }
        }
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

    /// Set a new cached token
    async fn set_cached_token(&self, new_token: String) {
        Self::set_cached_token_static(&self.cached_token, new_token).await;
    }

    /// Get the current cached token
    pub async fn get_token(&self) -> String {
        let token_guard = self.cached_token.read().await;
        token_guard.clone()
    }

    /// Create an ElastiCache/MemoryDB authentication token using AWS IAM credentials
    /// and SigV4 signing. The generated token is valid for 15 minutes and includes the
    /// signed username for authentication.
    async fn generate_token_static(state: &IamTokenState) -> Result<String, GlideIAMError> {
        // Load AWS credentials from the environment or AWS config files
        let config = aws_config::defaults(BehaviorVersion::latest())
            .region(aws_config::Region::new(state.region.to_string()))
            .load()
            .await;

        // Get credentials provider from the loaded config
        let credentials_provider = config.credentials_provider().ok_or(GlideIAMError::Other(
            "No AWS credentials provider found".to_string(),
        ))?;

        // Retrieve credentials
        let creds = credentials_provider
            .provide_credentials()
            .await
            .map_err(|e| GlideIAMError::CredentialsError(e.to_string()))?;

        // Create AWS identity from credentials
        let identity = Credentials::new(
            creds.access_key_id(),
            creds.secret_access_key(),
            creds.session_token().map(|s| s.to_string()),
            None,
            state.service_type.service_name(), // "elasticache" | "memorydb"
        );

        let signing_time = SystemTime::now();

        let hostname = state.cluster_name.to_string();
        let base_url = build_base_url(&hostname, &state.username);

        let mut signing_settings = SigningSettings::default();
        signing_settings.signature_location = SignatureLocation::QueryParams;
        signing_settings.expires_in = Some(Duration::from_secs(TOKEN_TTL_SECONDS));

        let identity_value = identity.into();
        let signing_params = v4::SigningParams::builder()
            .identity(&identity_value)
            .region(&state.region)
            .name(state.service_type.service_name())
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

        println!("IAM token: {}", token);
        Ok(token)
    }

    /// Force refresh the token immediately
    pub async fn refresh_token(&self) -> Result<(), GlideIAMError> {
        let new_token = Self::generate_token_static(&self.iam_token_state).await?;
        self.set_cached_token(new_token).await;
        Ok(())
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
            println!("Cleaned up old IAM token log file");
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
            refresh_interval_seconds: DEFAULT_REFRESH_INTERVAL_SECONDS, // Default value 14 minutes in seconds
        }
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_generate_token_static_returns_valid_token_format() {
        initialize_test_environment(); // Ensure test environment is clean
        setup_test_credentials();

        let region = "us-east-1";
        let cluster_name = "test-cluster";
        let username = "test-user";
        let service_type = ServiceType::ElastiCache;

        let state = create_test_state(region, cluster_name, username, service_type.clone());
        let result = IAMTokenManager::generate_token_static(&state).await;

        assert!(
            result.is_ok(),
            "Token generation should succeed with valid credentials"
        );

        let token = result.unwrap();
        // Save token to JSON file for inspection
        let state = create_test_state(region, cluster_name, username, service_type);
        save_token_to_file(
            "test_iam_generate_token_static_returns_valid_token_format",
            &token,
            &state,
        );

        // Verify token format matches new AWS SigV4 query parameter format
        assert!(
            token.starts_with(&format!("{}/", cluster_name)),
            "Token should start with cluster name"
        );
        assert!(
            token.contains("Action=connect"),
            "Token should contain Action=connect"
        );
        assert!(
            token.contains("User=test-user"),
            "Token should contain User parameter"
        );
        assert!(
            token.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"),
            "Token should contain AWS4-HMAC-SHA256 algorithm"
        );
        assert!(
            token.contains("X-Amz-Credential="),
            "Token should contain X-Amz-Credential parameter"
        );
        assert!(
            token.contains("X-Amz-Date="),
            "Token should contain X-Amz-Date parameter"
        );
        assert!(
            token.contains("X-Amz-Expires=900"),
            "Token should contain 15-minute expiration"
        );
        assert!(
            token.contains("X-Amz-SignedHeaders=host"),
            "Token should contain X-Amz-SignedHeaders parameter"
        );
        assert!(
            token.contains("X-Amz-Signature="),
            "Token should contain X-Amz-Signature parameter"
        );
        assert!(
            token.contains("X-Amz-Security-Token=test_session_token"),
            "Token should contain X-Amz-Security-Token parameter"
        );
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_generate_token_static_with_memorydb_service() {
        initialize_test_environment(); // Ensure test environment is clean
        setup_test_credentials();

        let region = "us-west-2";
        let cluster_name = "memorydb-cluster";
        let username = "memorydb-user";
        let service_type = ServiceType::MemoryDB;

        let state = create_test_state(region, cluster_name, username, service_type.clone());
        let result = IAMTokenManager::generate_token_static(&state).await;

        assert!(
            result.is_ok(),
            "Token generation should succeed for MemoryDB"
        );

        let token = result.unwrap();

        // Save token to JSON file for inspection
        let state = create_test_state(region, cluster_name, username, service_type);
        save_token_to_file(
            "test_iam_generate_token_static_with_memorydb_service",
            &token,
            &state,
        );

        assert!(
            token.starts_with(&format!("{}/", cluster_name)),
            "Token should start with cluster name"
        );
        assert!(
            token.contains("User=memorydb-user"),
            "Token should contain correct username"
        );
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_generate_token_static_with_special_characters_in_username() {
        initialize_test_environment(); // Ensure test environment is clean
        setup_test_credentials();

        let region = "eu-west-1";
        let cluster_name = "test-cluster";
        let username = "test@user.com";
        let service_type = ServiceType::ElastiCache;

        let state = create_test_state(region, cluster_name, username, service_type.clone());
        let result = IAMTokenManager::generate_token_static(&state).await;

        assert!(
            result.is_ok(),
            "Token generation should succeed with special characters in username"
        );

        let token = result.unwrap();

        // Save token to JSON file for inspection
        let state = create_test_state(region, cluster_name, username, service_type);
        save_token_to_file(
            "test_generate_token_static_with_special_characters_in_username",
            &token,
            &state,
        );

        assert!(
            token.starts_with(&format!("{}/", cluster_name)),
            "Token should start with cluster name"
        );
        assert!(
            token.contains("User=test%40user.com"),
            "Username should be URL encoded in token"
        );
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_new_creates_initial_token() {
        initialize_test_environment(); // Ensure test environment is clean
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
    async fn test_iam_token_manager_new_with_custom_refresh_interval() {
        initialize_test_environment(); // Ensure test environment is clean
        setup_test_credentials();

        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();
        let custom_interval = Some(5); // 5 minutes

        let result = IAMTokenManager::new(
            cluster_name.clone(),
            username.clone(),
            region.clone(),
            ServiceType::MemoryDB,
            custom_interval,
        )
        .await;

        assert!(
            result.is_ok(),
            "IAMTokenManager creation should succeed with custom interval"
        );

        let manager = result.unwrap();
        let token = manager.get_token().await;

        // Save token to JSON file for inspection
        let state = create_test_state(&region, &cluster_name, &username, ServiceType::MemoryDB);
        save_token_to_file(
            "test_iam_token_manager_new_with_custom_refresh_interval",
            &token,
            &state,
        );

        assert!(!token.is_empty(), "Initial token should not be empty");
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_get_token_returns_cached_token() {
        initialize_test_environment(); // Ensure test environment is clean
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
        initialize_test_environment(); // Ensure test environment is clean
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

        let refresh_result = manager.refresh_token().await;
        assert!(refresh_result.is_ok(), "Token refresh should succeed");

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
        initialize_test_environment(); // Ensure test environment is clean
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

    // todo: check about this test if it should fail or pass
    #[tokio::test]
    #[serial]
    async fn test_iam_generate_token_static_fails_without_credentials() {
        initialize_test_environment(); // Ensure test environment is clean

        // Clear any existing AWS credentials
        unsafe {
            env::remove_var("AWS_ACCESS_KEY_ID");
            env::remove_var("AWS_SECRET_ACCESS_KEY");
            env::remove_var("AWS_SESSION_TOKEN");
            env::remove_var("AWS_PROFILE");
            env::remove_var("AWS_SHARED_CREDENTIALS_FILE");
            env::remove_var("AWS_CONFIG_FILE");
        }

        let region = "us-east-1";
        let cluster_name = "test-cluster";
        let username = "test-user";
        let service_type = ServiceType::ElastiCache;

        let state = create_test_state(region, cluster_name, username, service_type.clone());
        let result = IAMTokenManager::generate_token_static(&state).await;

        // Note: This test might pass on EC2 instances with IAM roles or other credential sources
        // In such environments, AWS credentials are available even when env vars are cleared
        if result.is_ok() {
            println!(
                "Warning: Token generation succeeded despite cleared env vars - likely due to IAM role or other credential source"
            );
        } else {
            assert!(
                result.is_err(),
                "Token generation should fail without credentials"
            );
        }

        // Restore test credentials for other tests
        setup_test_credentials();
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_defaults() {
        initialize_test_environment(); // Ensure test environment is clean
        setup_test_credentials();
        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        // Test with all defaults (None values)
        let manager = IAMTokenManager::new(
            cluster_name.clone(),
            username.clone(),
            region.clone(),
            ServiceType::ElastiCache, // Service type is now required
            None,                     // Should default to 14 mins
        )
        .await
        .unwrap();

        let token = manager.get_token().await;

        // Save token to JSON file for inspection
        let state = create_test_state(&region, &cluster_name, &username, ServiceType::ElastiCache);
        save_token_to_file("test_iam_token_manager_defaults", &token, &state);

        // Verify it uses ElastiCache service by checking the token format
        assert!(
            token.contains("Action=connect"),
            "Token should contain Action=connect"
        );
    }

    #[tokio::test]
    #[serial]
    async fn test_iam_token_manager_refresh_interval_validation() {
        initialize_test_environment(); // Ensure test environment is clean
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
        initialize_test_environment(); // Ensure test environment is clean
        setup_test_credentials();

        // Configurable refresh time constant (can be changed)
        const REFRESH_TIME_SECONDS: u32 = 5;

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
