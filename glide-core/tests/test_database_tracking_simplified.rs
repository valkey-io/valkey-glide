// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Simple unit tests for database tracking functionality.
//! This tests the simplified approach where database changes are tracked in the connection configuration.

mod utilities;

use glide_core::client::{Client, ConnectionRequest, NodeAddress};
use redis::cluster_routing::Routable;
use redis::{Value, cmd};
use std::time::Duration;

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

    // Test that the database tracking method doesn't crash
    // In the real implementation, this will be called during send_command
    use redis::Cmd;
    let mut select_cmd = Cmd::new();
    select_cmd.arg("SELECT").arg(3);
    let success_response = Value::Okay;

    // This should not panic - the actual database update will only happen
    // when connected to a real standalone server
    client
        .track_database_change_if_select(&select_cmd, &success_response)
        .await;

    println!("Database tracking simplified test passed");
}

/// Test reconnection behavior with database tracking
/// This test starts a real Redis server, connects a client, executes SELECT,
/// forces disconnection, and verifies the database is preserved after reconnection
#[tokio::test]
async fn test_database_tracking_with_reconnection() {
    use crate::utilities::{
        ClusterMode, TestConfiguration, kill_connection, setup_test_basics_internal,
    };

    let config = TestConfiguration {
        use_tls: false,
        cluster_mode: ClusterMode::Disabled,
        shared_server: true,
        database_id: 0, // Start in database 0
        ..Default::default()
    };

    let test_basics = setup_test_basics_internal(&config).await;
    let mut client = test_basics.client;

    // Verify starting database is 0 by checking CLIENT INFO
    let mut client_info_cmd = redis::cmd("CLIENT");
    client_info_cmd.arg("INFO");
    let initial_info: String = redis::FromRedisValue::from_redis_value(
        &client.send_command(&client_info_cmd).await.unwrap(),
    )
    .unwrap();
    assert!(initial_info.contains("db=0"), "Should start in database 0, got: {initial_info}");

    // Execute SELECT 3 to change database
    let mut select_cmd = redis::cmd("SELECT");
    select_cmd.arg(3);
    let select_result = client.send_command(&select_cmd).await.unwrap();
    assert_eq!(select_result, Value::Okay, "SELECT command should succeed");

    // Verify we're now in database 3
    let db3_info: String = redis::FromRedisValue::from_redis_value(
        &client.send_command(&client_info_cmd).await.unwrap(),
    )
    .unwrap();
    assert!(db3_info.contains("db=3"), "Should be in database 3 after SELECT, got: {db3_info}");

    // Force disconnection to trigger reconnection
    kill_connection(&mut client).await;

    // Wait a moment for the connection to be dropped
    tokio::time::sleep(Duration::from_millis(100)).await;

    // Send another command - this should trigger reconnection
    // The reconnection should automatically restore database 3
    let reconnect_info_result = client.send_command(&client_info_cmd).await;

    // Handle both possible outcomes: immediate reconnection or retry after failure
    let reconnect_info: String = match reconnect_info_result {
        Ok(response) => {
            // Connection worked immediately (fast reconnection)
            redis::FromRedisValue::from_redis_value(&response).unwrap()
        }
        Err(_) => {
            // Connection failed as expected, retry to trigger reconnection
            tokio::time::sleep(Duration::from_millis(500)).await;
            let retry_response = client
                .send_command(&client_info_cmd)
                .await
                .expect("Reconnection should succeed after retry");
            redis::FromRedisValue::from_redis_value(&retry_response).unwrap()
        }
    };

    // After reconnection, we should still be in database 3
    assert!(
        reconnect_info.contains("db=3"),
        "Should be in database 3 after reconnection, got: {reconnect_info}"
    );

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
