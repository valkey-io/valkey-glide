use aws_config::BehaviorVersion;
use aws_credential_types::{Credentials, provider::ProvideCredentials};
use aws_sigv4::http_request::{SignableBody, SignableRequest, SigningSettings, sign};
use aws_sigv4::sign::v4;
use logger_core::{log_error, log_info};
use std::sync::Arc;
use std::time::Duration;
use std::time::SystemTime;
use tokio::sync::{Notify, RwLock};
use tokio::task::JoinHandle;
use tokio::time::{MissedTickBehavior, interval};

// Service type configuration for IAM authentication
#[derive(Clone, Debug)]
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
    
    fn hostname_suffix(&self) -> &'static str {
        match self {
            ServiceType::ElastiCache => "cache.amazonaws.com",
            ServiceType::MemoryDB => "memorydb.amazonaws.com",
        }
    }
}

/// Internal state for IAM token manager
struct IAMTokenState {
    /// AWS region for signing requests
    region: String,
    /// ElastiCache/MemoryDB cluster name
    cluster_name: String,
    /// Username for the connection
    username: String,
    /// Service type (ElastiCache or MemoryDB)
    service_type: ServiceType,
    /// Currently cached auth token
    cached_token: String,
    /// Token refresh interval in minutes
    refresh_interval_minutes: u32,
}

/// IAM-based authentication token manager for ElastiCache/MemoryDB
///
/// Manages automatic token refresh using AWS IAM credentials and SigV4 signing.
/// Tokens are valid for 15 minutes and refreshed every 8 minutes by default.
pub struct IAMTokenManager {
    /// Internal state protected by a single RwLock
    state: Arc<RwLock<IAMTokenState>>,

    /// Background refresh task handle
    refresh_task: Option<JoinHandle<()>>,

    /// Shutdown signal for graceful task termination
    shutdown_notify: Arc<Notify>,
}

impl IAMTokenManager {
    /// Create a new IAM token manager
    pub async fn new(
        cluster_name: String,
        username: String,
        region: String,
        refresh_interval_minutes: Option<u32>,
        service_type: Option<ServiceType>,
    ) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let service_type = service_type.unwrap_or(ServiceType::ElastiCache);
        
        // Generate initial token
        let initial_token = Self::generate_token_static(&region, &cluster_name, &username, &service_type).await?;

        let state = IAMTokenState {
            region,
            cluster_name,
            username,
            service_type,
            cached_token: initial_token,
            refresh_interval_minutes: refresh_interval_minutes.unwrap_or(8),
        };

        Ok(Self {
            state: Arc::new(RwLock::new(state)),
            refresh_task: None,
            shutdown_notify: Arc::new(Notify::new()),
        })
    }

    /// Start the background token refresh task
    pub fn start_refresh_task(&mut self) {
        if self.refresh_task.is_some() {
            return; // Task already running
        }

        let state = Arc::clone(&self.state);
        let shutdown_notify = Arc::clone(&self.shutdown_notify);

        let task = tokio::spawn(async move {
            // Get refresh interval from state
            let refresh_interval = {
                let state_guard = state.read().await;
                Duration::from_secs(state_guard.refresh_interval_minutes as u64 * 60)
            };

            let mut interval_timer = interval(refresh_interval);
            interval_timer.set_missed_tick_behavior(MissedTickBehavior::Skip);

            // Skip the first tick since we already have an initial token
            interval_timer.tick().await;

            loop {
                tokio::select! {
                    _ = interval_timer.tick() => {
                        // Get current state values for token generation
                        let (region, cluster_name, username, service_type) = {
                            let state_guard = state.read().await;
                            (
                                state_guard.region.clone(),
                                state_guard.cluster_name.clone(),
                                state_guard.username.clone(),
                                state_guard.service_type.clone(),
                            )
                        };

                        match Self::generate_token_static(
                            &region,
                            &cluster_name,
                            &username,
                            &service_type,
                        ).await {
                            Ok(new_token) => {
                                let mut state_guard = state.write().await;
                                state_guard.cached_token = new_token;
                                log_info("IAM token refreshed successfully", "");
                            }
                            Err(e) => {
                                log_error("Failed to refresh IAM token", format!("{e}"));
                                // Continue running - temporary failures shouldn't stop the task
                            }
                        }
                    }
                    _ = shutdown_notify.notified() => {
                        log_info("IAM token refresh task shutting down", "");
                        break;
                    }
                }
            }
        });

        self.refresh_task = Some(task);
    }

    /// Stop the background refresh task gracefully
    pub async fn stop_refresh_task(&mut self) {
        if let Some(task) = self.refresh_task.take() {
            self.shutdown_notify.notify_one();

            // Give the task a moment to shut down gracefully
            let _ = tokio::time::timeout(Duration::from_secs(5), task).await;
        }
    }

    /// Get the current cached token
    pub async fn get_token(&self) -> String {
        let state_guard = self.state.read().await;
        state_guard.cached_token.clone()
    }

    // todo: test it works well
    /// This method creates an ElastiCache/MemoryDB authentication token using AWS IAM credentials
    /// and SigV4 signing. The generated token is valid for 15 minutes and includes the signed
    /// username for authentication. It is designed to be used in environments where secure
    /// authentication to AWS services is required.
    async fn generate_token_static(
        region: &str,
        cluster_name: &str,
        username: &str,
        service_type: &ServiceType,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        // Load AWS credentials from the environment or AWS config files
        let config = aws_config::defaults(BehaviorVersion::latest())
            .region(aws_config::Region::new(region.to_string()))
            .load()
            .await;

        // Get credentials provider from the loaded config
        let credentials_provider = config
            .credentials_provider()
            .ok_or("No AWS credentials provider found")?;

        // Retrieve credentials
        let creds = credentials_provider
            .provide_credentials()
            .await
            .map_err(|e| format!("Failed to get AWS credentials: {e}"))?;

        // todo: why shouldn't it be a singleton? instead of creating a new one every time?
        // Create AWS identity from credentials
        let identity = Credentials::new(
            creds.access_key_id(),
            creds.secret_access_key(),
            creds.session_token().map(|s| s.to_string()),
            None,
            "elasticache-auth",
        );

        // Calculate the current time for signing
        let signing_time = SystemTime::now();

        // Create the canonical request for ElastiCache/MemoryDB auth token
        let hostname = format!("{cluster_name}.{region}.{}", service_type.hostname_suffix());
        let canonical_uri = "/";
        let canonical_querystring = format!(
            "Action=connect&User={}&X-Amz-Expires=900",
            urlencoding::encode(username)
        );

        let signing_settings = SigningSettings::default();
        let identity_value = identity.into();
        let signing_params = v4::SigningParams::builder()
            .identity(&identity_value)
            .region(region)
            .name(service_type.service_name())
            .time(signing_time)
            .settings(signing_settings)
            .build()
            .map_err(|e| format!("Failed to build signing params: {e}"))?
            .into();

        // Create signable request
        let request_url = format!("https://{hostname}{canonical_uri}?{canonical_querystring}");
        let signable_request = SignableRequest::new(
            "GET",
            &request_url,
            std::iter::empty(),
            SignableBody::Bytes(b""),
        )
        .map_err(|e| format!("Failed to create signable request: {e}"))?;

        // Sign the request
        let (signing_instructions, _signature) = sign(signable_request, &signing_params)
            .map_err(|e| format!("Failed to sign request: {e}"))?
            .into_parts();

        // Build a temporary HTTP request to apply the signing instructions
        let mut temp_request = http::Request::builder()
            .method("GET")
            .uri(&request_url)
            .header("Host", &hostname)
            .body("")
            .map_err(|e| format!("Failed to build temp HTTP request: {e}"))?;

        // Apply the signing instructions to get the authorization header
        signing_instructions.apply_to_request_http1x(&mut temp_request);

        // Extract the authorization header
        let auth_header = temp_request
            .headers()
            .get("authorization")
            .ok_or("Authorization header not found in signed request")?
            .to_str()
            .map_err(|e| format!("Failed to convert authorization header to string: {e}"))?;

        // The ElastiCache auth token format: username?query_params&Authorization=signature
        let token = format!(
            "{}?{}&Authorization={}",
            username,
            canonical_querystring,
            urlencoding::encode(auth_header)
        );

        Ok(token)
    }

    /// Force refresh the token immediately
    pub async fn refresh_token(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        // todo: check why write and read in two seperate blocks and not in one
        let new_token = {
            let state_guard = self.state.read().await;
            Self::generate_token_static(
                &state_guard.region,
                &state_guard.cluster_name,
                &state_guard.username,
                &state_guard.service_type,
            )
            .await?
        };
        
        let mut state_guard = self.state.write().await;
        state_guard.cached_token = new_token;

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

#[cfg(test)]
mod tests {
    use super::*;
    use std::env;
    use tokio::time::{sleep, Duration};

    // Helper function to set up mock AWS credentials for testing
    fn setup_test_credentials() {
        unsafe {
            env::set_var("AWS_ACCESS_KEY_ID", "test_access_key");
            env::set_var("AWS_SECRET_ACCESS_KEY", "test_secret_key");
            env::set_var("AWS_SESSION_TOKEN", "test_session_token");
        }
    }

    #[tokio::test]
    async fn test_service_type_service_name() {
        assert_eq!(ServiceType::ElastiCache.service_name(), "elasticache");
        assert_eq!(ServiceType::MemoryDB.service_name(), "memorydb");
    }

    #[tokio::test]
    async fn test_service_type_hostname_suffix() {
        assert_eq!(ServiceType::ElastiCache.hostname_suffix(), "cache.amazonaws.com");
        assert_eq!(ServiceType::MemoryDB.hostname_suffix(), "memorydb.amazonaws.com");
    }

    #[tokio::test]
    async fn test_generate_token_static_returns_valid_token_format() {
        setup_test_credentials();
        
        let region = "us-east-1";
        let cluster_name = "test-cluster";
        let username = "test-user";
        let service_type = ServiceType::ElastiCache;

        let result = IAMTokenManager::generate_token_static(
            region,
            cluster_name,
            username,
            &service_type,
        ).await;

        assert!(result.is_ok(), "Token generation should succeed with valid credentials");
        
        let token = result.unwrap();
        
        // Verify token format: username?query_params&Authorization=signature
        assert!(token.starts_with(username), "Token should start with username");
        assert!(token.contains("Action=connect"), "Token should contain Action=connect");
        assert!(token.contains("User=test-user"), "Token should contain User parameter");
        assert!(token.contains("X-Amz-Expires=900"), "Token should contain 15-minute expiration");
        assert!(token.contains("Authorization="), "Token should contain Authorization parameter");
    }

    #[tokio::test]
    async fn test_generate_token_static_with_memorydb_service() {
        setup_test_credentials();
        
        let region = "us-west-2";
        let cluster_name = "memorydb-cluster";
        let username = "memorydb-user";
        let service_type = ServiceType::MemoryDB;

        let result = IAMTokenManager::generate_token_static(
            region,
            cluster_name,
            username,
            &service_type,
        ).await;

        assert!(result.is_ok(), "Token generation should succeed for MemoryDB");
        
        let token = result.unwrap();
        assert!(token.starts_with(username), "Token should start with username");
        assert!(token.contains("User=memorydb-user"), "Token should contain correct username");
    }

    #[tokio::test]
    async fn test_generate_token_static_with_special_characters_in_username() {
        setup_test_credentials();
        
        let region = "eu-west-1";
        let cluster_name = "test-cluster";
        let username = "test@user.com";
        let service_type = ServiceType::ElastiCache;

        let result = IAMTokenManager::generate_token_static(
            region,
            cluster_name,
            username,
            &service_type,
        ).await;

        assert!(result.is_ok(), "Token generation should succeed with special characters in username");
        
        let token = result.unwrap();
        assert!(token.starts_with(username), "Token should start with encoded username");
        assert!(token.contains("User=test%40user.com"), "Username should be URL encoded in token");
    }

    #[tokio::test]
    async fn test_iam_token_manager_new_creates_initial_token() {
        setup_test_credentials();
        
        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        let result = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            None,
            None,
        ).await;

        assert!(result.is_ok(), "IAMTokenManager creation should succeed");
        
        let manager = result.unwrap();
        let token = manager.get_token().await;
        
        assert!(!token.is_empty(), "Initial token should not be empty");
        assert!(token.starts_with("test-user"), "Token should start with username");
    }

    #[tokio::test]
    async fn test_iam_token_manager_new_with_custom_refresh_interval() {
        setup_test_credentials();
        
        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();
        let custom_interval = Some(5); // 5 minutes

        let result = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            custom_interval,
            Some(ServiceType::MemoryDB),
        ).await;

        assert!(result.is_ok(), "IAMTokenManager creation should succeed with custom interval");
        
        let manager = result.unwrap();
        let token = manager.get_token().await;
        
        assert!(!token.is_empty(), "Initial token should not be empty");
    }

    #[tokio::test]
    async fn test_iam_token_manager_get_token_returns_cached_token() {
        setup_test_credentials();
        
        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        let manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            None,
            None,
        ).await.unwrap();

        let token1 = manager.get_token().await;
        let token2 = manager.get_token().await;
        
        assert_eq!(token1, token2, "get_token should return the same cached token");
    }

    #[tokio::test]
    async fn test_iam_token_manager_refresh_token_updates_cached_token() {
        setup_test_credentials();
        
        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        let manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            None,
            None,
        ).await.unwrap();

        let initial_token = manager.get_token().await;
        
        // Wait a small amount to ensure timestamp difference
        sleep(Duration::from_millis(10)).await;
        
        let refresh_result = manager.refresh_token().await;
        assert!(refresh_result.is_ok(), "Token refresh should succeed");
        
        let new_token = manager.get_token().await;
        
        // Tokens should be different due to different timestamps in signing
        assert_ne!(initial_token, new_token, "Refreshed token should be different from initial token");
        assert!(new_token.starts_with("test-user"), "New token should still start with username");
    }

    #[tokio::test]
    async fn test_iam_token_manager_start_and_stop_refresh_task() {
        setup_test_credentials();
        
        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        let mut manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            Some(1), // 1 minute refresh interval for faster testing
            None,
        ).await.unwrap();

        // Start the refresh task
        manager.start_refresh_task();
        assert!(manager.refresh_task.is_some(), "Refresh task should be started");
        
        // Starting again should not create a new task
        manager.start_refresh_task();
        assert!(manager.refresh_task.is_some(), "Refresh task should still exist");
        
        // Stop the refresh task
        manager.stop_refresh_task().await;
        assert!(manager.refresh_task.is_none(), "Refresh task should be stopped");
    }

    #[tokio::test]
    async fn test_generate_token_static_fails_without_credentials() {
        // Clear any existing AWS credentials
        unsafe {
            env::remove_var("AWS_ACCESS_KEY_ID");
            env::remove_var("AWS_SECRET_ACCESS_KEY");
            env::remove_var("AWS_SESSION_TOKEN");
            env::remove_var("AWS_PROFILE");
        }
        
        let region = "us-east-1";
        let cluster_name = "test-cluster";
        let username = "test-user";
        let service_type = ServiceType::ElastiCache;

        let result = IAMTokenManager::generate_token_static(
            region,
            cluster_name,
            username,
            &service_type,
        ).await;

        assert!(result.is_err(), "Token generation should fail without credentials");
        
        // Restore test credentials for other tests
        setup_test_credentials();
    }

    #[tokio::test]
    async fn test_service_type_clone_and_debug() {
        let elasticache = ServiceType::ElastiCache;
        let elasticache_clone = elasticache.clone();
        
        assert_eq!(elasticache.service_name(), elasticache_clone.service_name());
        
        let debug_str = format!("{:?}", elasticache);
        assert!(debug_str.contains("ElastiCache"), "Debug output should contain service type name");
    }

    #[tokio::test]
    async fn test_iam_token_manager_defaults() {
        setup_test_credentials();
        
        let cluster_name = "test-cluster".to_string();
        let username = "test-user".to_string();
        let region = "us-east-1".to_string();

        // Test with all defaults (None values)
        let manager = IAMTokenManager::new(
            cluster_name,
            username,
            region,
            None, // Should default to 8 minutes
            None, // Should default to ElastiCache
        ).await.unwrap();

        let token = manager.get_token().await;
        
        // Verify it uses ElastiCache service by checking the token format
        assert!(token.starts_with("test-user"), "Token should start with username");
        assert!(token.contains("Action=connect"), "Token should contain Action=connect");
    }
}
