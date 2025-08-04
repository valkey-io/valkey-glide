# IAM Authentication API Documentation

Valkey GLIDE supports IAM-based authentication for connecting to AWS ElastiCache and MemoryDB clusters. This feature automatically manages authentication tokens using AWS IAM credentials and SigV4 signing.

## Overview

IAM authentication provides a secure way to connect to ElastiCache/MemoryDB clusters without hardcoding passwords. The client automatically:

- Generates authentication tokens using AWS IAM credentials and SigV4 signing
- Refreshes tokens automatically before they expire (tokens are valid for 15 minutes)
- Handles token rotation seamlessly in the background
- Uses AWS SDK credential chain for authentication

## Core Components

### IAMTokenManager

The `IAMTokenManager` is the core component that handles automatic token generation and refresh:

```rust
pub struct IAMTokenManager {
    region: String,
    cluster_name: String,
    username: String,
    cached_token: Arc<RwLock<String>>,
    refresh_task: Option<JoinHandle<()>>,
    shutdown_notify: Arc<Notify>,
    refresh_interval_minutes: u32,
}
```

### Configuration Structure

IAM authentication is configured through the `IamAuthenticationConfig` structure:

```rust
pub struct IamAuthenticationConfig {
    pub cluster_name: String,
    pub region: String,
    pub refresh_interval_minutes: Option<u32>,
}
```

## Quick Start

Here's a minimal example to get started with IAM authentication:

```rust
use valkey_glide::client::{Client, ConnectionRequest};
use valkey_glide::client::types::{AuthenticationInfo, IamAuthenticationConfig, NodeAddress};

// Configure IAM authentication
let connection_request = ConnectionRequest {
    addresses: vec![NodeAddress {
        host: "my-cluster.cache.amazonaws.com".to_string(),
        port: 6379,
    }],
    authentication_info: Some(AuthenticationInfo {
        username: Some("my-iam-user".to_string()),
        password: None,
        iam_config: Some(IamAuthenticationConfig {
            cluster_name: "my-elasticache-cluster".to_string(),
            region: "us-west-2".to_string(),
            refresh_interval_minutes: None, // Use default (8 minutes)
        }),
    }),
    cluster_mode_enabled: true,
    ..Default::default()
};

// Create client - IAM token manager starts automatically
let mut client = Client::new(connection_request, None).await?;

// Use the client normally - authentication is handled transparently
let result = client.send_command(&redis::cmd("PING"), None).await?;
```

## API Usage

### Connection Request Configuration

To use IAM authentication, configure the `AuthenticationInfo` in your connection request:

```rust
use valkey_glide::client::types::{AuthenticationInfo, IamAuthenticationConfig};

let auth_info = AuthenticationInfo {
    username: Some("your-iam-username".to_string()),
    password: None, // Not used with IAM authentication
    iam_config: Some(IamAuthenticationConfig {
        cluster_name: "my-elasticache-cluster".to_string(),
        region: "us-west-2".to_string(),
        refresh_interval_minutes: Some(8), // Optional, defaults to 8 minutes
    }),
};
```

### Protocol Buffer Configuration

At the protocol level, IAM credentials are specified using the `IamCredentials` message:

```protobuf
message IamCredentials {
    string cluster_name = 1;
    string username = 2;
    string region = 3;
    optional uint32 refresh_interval_minutes = 4;  // Default: 8 minutes
}

message AuthenticationInfo {
    oneof credentials {
        ServerCredentials server_credentials = 1;
        IamCredentials iam_credentials = 2;
    }
}
```

### Complete Connection Example

```rust
use valkey_glide::client::{Client, ConnectionRequest};
use valkey_glide::client::types::{AuthenticationInfo, IamAuthenticationConfig, NodeAddress};

let connection_request = ConnectionRequest {
    addresses: vec![NodeAddress {
        host: "my-cluster.cache.amazonaws.com".to_string(),
        port: 6379,
    }],
    authentication_info: Some(AuthenticationInfo {
        username: Some("my-iam-user".to_string()),
        password: None,
        iam_config: Some(IamAuthenticationConfig {
            cluster_name: "my-elasticache-cluster".to_string(),
            region: "us-west-2".to_string(),
            refresh_interval_minutes: Some(8),
        }),
    }),
    cluster_mode_enabled: true,
    // ... other configuration options
    ..Default::default()
};

let client = Client::new(connection_request, None).await?;
```

## Token Management

### Automatic Token Refresh

The IAM token manager automatically handles token refresh:

- **Token Validity**: Tokens are valid for 15 minutes
- **Refresh Interval**: Tokens are refreshed every 8 minutes by default (configurable)
- **Background Task**: Refresh happens in a background task without blocking operations
- **Error Handling**: Temporary failures don't stop the refresh task

### Manual Token Operations

The `IAMTokenManager` provides methods for manual token management:

```rust
impl IAMTokenManager {
    // Create a new token manager
    pub async fn new(
        cluster_name: String,
        username: String,
        region: String,
        refresh_interval_minutes: Option<u32>,
    ) -> Result<Self, Box<dyn std::error::Error + Send + Sync>>;

    // Start automatic token refresh
    pub fn start_refresh_task(&mut self);

    // Stop automatic token refresh
    pub async fn stop_refresh_task(&mut self);

    // Get current cached token
    pub async fn get_token(&self) -> String;

    // Force immediate token refresh
    pub async fn refresh_token(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>>;
}
```

## AWS Credentials

### Credential Chain

The IAM authentication uses the standard AWS SDK credential chain:

1. Environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`)
2. AWS credentials file (`~/.aws/credentials`)
3. AWS config file (`~/.aws/config`)
4. IAM roles for EC2 instances
5. IAM roles for ECS tasks
6. IAM roles for Lambda functions

### Required Permissions

The IAM user or role must have the following permissions:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "elasticache:Connect"
            ],
            "Resource": [
                "arn:aws:elasticache:region:account-id:replicationgroup/cluster-name",
                "arn:aws:elasticache:region:account-id:user/username"
            ]
        }
    ]
}
```

## Token Format

The generated authentication token follows this format:

```
username?Action=connect&User=username&X-Amz-Expires=900&Authorization=AWS4-HMAC-SHA256...
```

Where:
- `username`: The IAM username for the connection
- `Action=connect`: The ElastiCache action being performed
- `User=username`: URL-encoded username parameter
- `X-Amz-Expires=900`: Token expiration time (15 minutes = 900 seconds)
- `Authorization`: SigV4 signature with AWS credentials

## Configuration Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `cluster_name` | String | Yes | - | ElastiCache/MemoryDB cluster name |
| `username` | String | Yes | - | IAM username for authentication |
| `region` | String | Yes | - | AWS region where the cluster is located |
| `refresh_interval_minutes` | u32 | No | 8 | Token refresh interval in minutes |

## Error Handling

### Common Errors

1. **Missing AWS Credentials**
   ```
   Error: "No AWS credentials provider found"
   ```
   Solution: Configure AWS credentials using one of the supported methods

2. **Invalid Cluster Name**
   ```
   Error: "Failed to sign request"
   ```
   Solution: Verify the cluster name and region are correct

3. **Insufficient Permissions**
   ```
   Error: "Access Denied"
   ```
   Solution: Ensure the IAM user/role has `elasticache:Connect` permission

4. **Token Refresh Failure**
   ```
   Error: "Failed to refresh IAM token"
   ```
   Solution: Check AWS credentials and network connectivity

### Best Practices

1. **Refresh Interval**: Use the default 8-minute refresh interval unless you have specific requirements
2. **Error Monitoring**: Monitor token refresh failures in production environments
3. **Credential Rotation**: Use IAM roles instead of long-term access keys when possible
4. **Network Security**: Ensure your application can reach AWS STS endpoints for token signing

## Integration with Client

The IAM authentication is seamlessly integrated with the Valkey GLIDE client:

- **Lazy Initialization**: IAM token manager is created only when needed
- **Thread Safety**: Token operations are thread-safe using Arc and RwLock
- **Graceful Shutdown**: Background refresh tasks are properly cleaned up
- **Connection Pooling**: Works with both standalone and cluster connections

## Troubleshooting

### Debug Token Generation

To debug token generation issues, check:

1. AWS credentials are properly configured
2. The IAM user/role has necessary permissions
3. The cluster name and region are correct
4. Network connectivity to AWS services

### Monitoring Token Refresh

The token manager logs refresh events:

```
IAM token refreshed successfully
```

Failed refresh attempts are logged as errors:

```
Failed to refresh IAM token: <error details>
```

## Security Considerations

1. **Token Lifetime**: Tokens are short-lived (15 minutes) for security
2. **Automatic Rotation**: Tokens are automatically rotated before expiration
3. **No Password Storage**: No passwords are stored or transmitted
4. **AWS Security**: Leverages AWS IAM security model
5. **Encryption**: All token operations use HTTPS/TLS

## Migration from Password Authentication

To migrate from password-based authentication to IAM:

1. Set up IAM users/roles with appropriate permissions
2. Update connection configuration to use `IamAuthenticationConfig`
3. Remove password-based authentication configuration
4. Test the connection with IAM authentication
5. Monitor for any authentication issues during the transition

This IAM authentication feature provides a secure, scalable way to authenticate with ElastiCache and MemoryDB clusters while following AWS security best practices.
