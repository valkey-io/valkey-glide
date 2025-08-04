use std::sync::Arc;
use std::time::Duration;
use tokio::sync::{Notify, RwLock};
use tokio::task::JoinHandle;
use tokio::time::{MissedTickBehavior, interval};

/// IAM-based authentication token manager for ElastiCache/MemoryDB
///
/// Manages automatic token refresh using AWS IAM credentials and SigV4 signing.
/// Tokens are valid for 15 minutes and refreshed every 8 minutes by default.
pub struct IAMTokenManager {
    /// AWS region for signing requests
    region: String,

    /// ElastiCache/MemoryDB cluster name
    cluster_name: String,

    /// Username for the connection
    username: String,

    /// Currently cached auth token
    cached_token: Arc<RwLock<String>>,

    /// Background refresh task handle
    refresh_task: Option<JoinHandle<()>>,

    /// Shutdown signal for graceful task termination
    shutdown_notify: Arc<Notify>,

    /// Token refresh interval in minutes
    refresh_interval_minutes: u32,
}

impl IAMTokenManager {
    /// Create a new IAM token manager
    pub async fn new(
        cluster_name: String,
        username: String,
        region: String,
        refresh_interval_minutes: Option<u32>,
    ) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        // Generate initial token (placeholder for now)
        let initial_token = Self::generate_token_static(&region, &cluster_name, &username).await?;

        Ok(Self {
            region,
            cluster_name,
            username,
            cached_token: Arc::new(RwLock::new(initial_token)),
            refresh_task: None,
            shutdown_notify: Arc::new(Notify::new()),
            refresh_interval_minutes: refresh_interval_minutes.unwrap_or(8),
        })
    }

    /// Start the background token refresh task
    pub fn start_refresh_task(&mut self) {
        if self.refresh_task.is_some() {
            return; // Task already running
        }

        let region = self.region.clone();
        let cluster_name = self.cluster_name.clone();
        let username = self.username.clone();
        let cached_token = Arc::clone(&self.cached_token);
        let shutdown_notify = Arc::clone(&self.shutdown_notify);
        let refresh_interval = Duration::from_secs(self.refresh_interval_minutes as u64 * 60);

        let task = tokio::spawn(async move {
            let mut interval_timer = interval(refresh_interval);
            interval_timer.set_missed_tick_behavior(MissedTickBehavior::Skip);

            // Skip the first tick since we already have an initial token
            interval_timer.tick().await;

            loop {
                tokio::select! {
                    _ = interval_timer.tick() => {
                        match Self::generate_token_static(
                            &region,
                            &cluster_name,
                            &username,
                        ).await {
                            Ok(new_token) => {
                                let mut token_guard = cached_token.write().await;
                                *token_guard = new_token;
                                println!("IAM token refreshed successfully");
                            }
                            Err(e) => {
                                eprintln!("Failed to refresh IAM token: {e}");
                                // Continue running - temporary failures shouldn't stop the task
                            }
                        }
                    }
                    _ = shutdown_notify.notified() => {
                        println!("IAM token refresh task shutting down");
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
        let token_guard = self.cached_token.read().await;
        token_guard.clone()
    }

    /// Generate a new IAM auth token using SigV4 signing
    ///
    /// Creates an ElastiCache/MemoryDB auth token using AWS IAM credentials and SigV4 signing.
    /// The token is valid for 15 minutes and contains the signed username for authentication.
    async fn generate_token_static(
        region: &str,
        cluster_name: &str,
        username: &str,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        use aws_config::BehaviorVersion;
        use aws_credential_types::{Credentials, provider::ProvideCredentials};
        use aws_sigv4::http_request::{SignableBody, SignableRequest, SigningSettings, sign};
        use aws_sigv4::sign::v4;
        use std::time::SystemTime;

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

        // Create the canonical request for ElastiCache auth token
        let hostname = format!("{cluster_name}.{region}.cache.amazonaws.com");
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
            .name("elasticache")
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
        let new_token =
            Self::generate_token_static(&self.region, &self.cluster_name, &self.username).await?;

        let mut token_guard = self.cached_token.write().await;
        *token_guard = new_token;

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
