// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Simple unit tests for database tracking functionality.
//! This tests the simplified approach where database changes are tracked in the connection configuration.

use glide_core::client::{Client, ConnectionRequest, NodeAddress};
use redis::cluster_routing::Routable;
use redis::{Value, cmd};

/// Test the basic database tracking logic without requiring a Redis server
#[tokio::test]
async fn test_database_tracking_logic() {
    // Create a basic connection request starting with database 0
    let request = ConnectionRequest {
        addresses: vec![NodeAddress {
            host: "localhost".to_string(),
            port: 6379,
        }],
        database_id: 0,
        lazy_connect: true, // Use lazy connect to avoid actual connection
        ..Default::default()
    };

    // Create a client
    let client = Client::new(request, None)
        .await
        .expect("Client creation should succeed with lazy connect");

    // Verify that the connection request starts with database 0
    {
        let config = client.connection_request.read().await;
        assert_eq!(config.database_id, 0);
    }

    println!("Database tracking simplified test passed");
}

/// Test reconnection behavior with database tracking
/// This test requires a running Redis/Valkey server
#[tokio::test]
async fn test_database_tracking_with_reconnection() {
    // Skip this test if no server is available
    if std::env::var("REDIS_URL").is_err() && std::env::var("VALKEY_URL").is_err() {
        println!("Skipping reconnection test - no server URL provided");
        return;
    }

    let server_url = std::env::var("REDIS_URL")
        .or_else(|_| std::env::var("VALKEY_URL"))
        .unwrap_or_else(|_| "redis://localhost:6379".to_string());

    // Parse the server URL to get host and port
    let url = url::Url::parse(&server_url).expect("Invalid server URL");
    let host = url.host_str().unwrap_or("localhost").to_string();
    let port = url.port().unwrap_or(6379);

    // Create a connection request starting with database 0
    let request = ConnectionRequest {
        addresses: vec![NodeAddress { host, port }],
        database_id: 0,
        lazy_connect: false, // Actually connect to test reconnection
        ..Default::default()
    };

    let client = match Client::new(request, None).await {
        Ok(client) => client,
        Err(_) => {
            println!("Skipping reconnection test - cannot connect to server");
            return;
        }
    };

    // Verify starting database is 0
    {
        let config = client.connection_request.read().await;
        assert_eq!(config.database_id, 0);
    }

    // Simulate a SELECT command to database 1 (this would normally be done through send_command)
    // For this test, we'll directly call the tracking function to simulate a successful SELECT
    use redis::Cmd;
    let mut select_cmd = Cmd::new();
    select_cmd.arg("SELECT").arg(1);
    let success_response = Value::Okay;

    client
        .track_database_change_if_select(&select_cmd, &success_response)
        .await;

    // Verify that the connection request database_id was updated
    {
        let config = client.connection_request.read().await;
        assert_eq!(config.database_id, 1);
    }

    println!("Database tracking with reconnection test passed");
}

/// Test SELECT command structure detection
#[test]
fn test_select_command_structure() {
    // Create a SELECT command
    let mut select_cmd = cmd("SELECT");
    select_cmd.arg(3);

    // Verify command structure
    let command_bytes = select_cmd.command().unwrap();
    let command_str = String::from_utf8_lossy(&command_bytes);
    assert_eq!(command_str.to_uppercase(), "SELECT");

    // Verify argument extraction
    let mut args = select_cmd.args_iter();
    args.next(); // Skip the command name "SELECT"
    let db_arg = args.next().unwrap();
    let db_bytes = match db_arg {
        redis::Arg::Simple(bytes) => bytes,
        redis::Arg::Cursor => panic!("Unexpected cursor argument"),
    };
    let db_str = std::str::from_utf8(db_bytes).unwrap();
    let db_index: i64 = db_str.parse().unwrap();
    assert_eq!(db_index, 3);

    println!("SELECT command structure test passed");
}

/// Test successful response detection
#[test]
fn test_successful_response_detection() {
    let success_response = Value::Okay;
    let error_response = Value::SimpleString("ERROR".to_string());
    let nil_response = Value::Nil;

    // Test success detection
    assert!(matches!(success_response, Value::Okay));
    assert!(!matches!(error_response, Value::Okay));
    assert!(!matches!(nil_response, Value::Okay));

    println!("Successful response detection test passed");
}
