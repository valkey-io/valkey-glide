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
    /// Generate a new IAM auth token using SigV4 signing
    ///
    /// Creates an ElastiCache/MemoryDB auth token using AWS IAM credentials and SigV4 signing.
    /// The token is valid for 15 minutes and contains the signed username for authentication.
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
