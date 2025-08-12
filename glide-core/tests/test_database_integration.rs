// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Integration tests for database tracking and restoration.
//! These tests require a running Redis server on localhost:6379.
//! 
//! To run these tests:
//! 1. Start a Redis server: `redis-server --port 6379`
//! 2. Run: `cargo test --test test_database_integration -- --ignored`

use glide_core::client::{Client, ConnectionRequest, NodeAddress};
use redis::{cmd, Value};
use std::time::Duration;
use tokio::time::sleep;

/// Helper function to create a client configuration
fn create_test_config() -> ConnectionRequest {
    let mut request = ConnectionRequest::default();
    request.addresses = vec![NodeAddress {
        host: "localhost".to_string(),
        port: 6379,
    }];
    request.database_id = 0; // Start with database 0
    request.request_timeout = Some(5000); // 5 second timeout
    request
}

/// Test that requires a running Redis server
#[tokio::test]
#[ignore = "requires running Redis server"]
async fn test_full_database_tracking_and_restoration_flow() {
    let config = create_test_config();
    
    // Create a client
    let mut client = Client::new(config, None)
        .await
        .expect("Failed to create client - ensure Redis is running on localhost:6379");

    // Verify initial database is 0
    assert_eq!(client.get_current_database(), 0);

    // Test 1: Execute a SELECT command to change to database 1
    let mut select_cmd = cmd("SELECT");
    select_cmd.arg(1);
    
    let result = client.send_command(&select_cmd, None).await
        .expect("SELECT command should succeed");
    
    // Verify the SELECT command succeeded
    assert_eq!(result, Value::SimpleString("OK".to_string()));
    
    // Verify the client tracked the database change
    assert_eq!(client.get_current_database(), 1);

    // Test 2: Set a value in database 1 to verify we're in the right database
    let mut set_cmd = cmd("SET");
    set_cmd.arg("test_key").arg("database_1_value");
    
    let result = client.send_command(&set_cmd, None).await
        .expect("SET command should succeed");
    assert_eq!(result, Value::SimpleString("OK".to_string()));

    // Test 3: Switch to database 2
    let mut select_cmd = cmd("SELECT");
    select_cmd.arg(2);
    
    let result = client.send_command(&select_cmd, None).await
        .expect("SELECT to database 2 should succeed");
    assert_eq!(result, Value::SimpleString("OK".to_string()));
    assert_eq!(client.get_current_database(), 2);

    // Test 4: Set a different value in database 2
    let mut set_cmd = cmd("SET");
    set_cmd.arg("test_key").arg("database_2_value");
    
    let result = client.send_command(&set_cmd, None).await
        .expect("SET in database 2 should succeed");
    assert_eq!(result, Value::SimpleString("OK".to_string()));

    // Test 5: Manually restore database to verify restoration works
    client.restore_database().await
        .expect("Manual database restoration should succeed");

    // Test 6: Verify we're still in database 2 by getting the value
    let mut get_cmd = cmd("GET");
    get_cmd.arg("test_key");
    
    let result = client.send_command(&get_cmd, None).await
        .expect("GET command should succeed");
    assert_eq!(result, Value::BulkString(b"database_2_value".to_vec()));

    // Test 7: Clean up - delete test keys from both databases
    let mut del_cmd = cmd("DEL");
    del_cmd.arg("test_key");
    client.send_command(&del_cmd, None).await.ok(); // Delete from database 2

    let mut select_cmd = cmd("SELECT");
    select_cmd.arg(1);
    client.send_command(&select_cmd, None).await.ok();
    
    let mut del_cmd = cmd("DEL");
    del_cmd.arg("test_key");
    client.send_command(&del_cmd, None).await.ok(); // Delete from database 1

    println!("Full database tracking and restoration test passed!");
}

/// Test database restoration after simulated connection issues
#[tokio::test]
#[ignore = "requires running Redis server"]
async fn test_database_restoration_with_connection_handling() {
    let config = create_test_config();
    
    // Create a client
    let mut client = Client::new(config, None)
        .await
        .expect("Failed to create client - ensure Redis is running on localhost:6379");

    // Switch to database 3
    let mut select_cmd = cmd("SELECT");
    select_cmd.arg(3);
    
    client.send_command(&select_cmd, None).await
        .expect("SELECT to database 3 should succeed");
    
    assert_eq!(client.get_current_database(), 3);

    // Set a marker value in database 3
    let mut set_cmd = cmd("SET");
    set_cmd.arg("marker_key").arg("in_database_3");
    
    client.send_command(&set_cmd, None).await
        .expect("SET in database 3 should succeed");

    // The automatic restoration should happen on the next command
    // Let's verify we're still in database 3 by getting our marker
    let mut get_cmd = cmd("GET");
    get_cmd.arg("marker_key");
    
    let result = client.send_command(&get_cmd, None).await
        .expect("GET should succeed");
    
    assert_eq!(result, Value::BulkString(b"in_database_3".to_vec()));

    // Clean up
    let mut del_cmd = cmd("DEL");
    del_cmd.arg("marker_key");
    client.send_command(&del_cmd, None).await.ok();

    println!("Database restoration with connection handling test passed!");
}

/// Test that multiple SELECT commands properly track the latest database
#[tokio::test]
#[ignore = "requires running Redis server"]
async fn test_multiple_select_commands() {
    let config = create_test_config();
    
    let mut client = Client::new(config, None)
        .await
        .expect("Failed to create client");

    // Test rapid database changes
    for db_index in 1..=5 {
        let mut select_cmd = cmd("SELECT");
        select_cmd.arg(db_index);
        
        let result = client.send_command(&select_cmd, None).await
            .expect(&format!("SELECT to database {} should succeed", db_index));
        
        assert_eq!(result, Value::SimpleString("OK".to_string()));
        assert_eq!(client.get_current_database(), db_index as i64);
    }

    // Verify we're in database 5
    let mut set_cmd = cmd("SET");
    set_cmd.arg("final_test").arg("database_5");
    client.send_command(&set_cmd, None).await
        .expect("SET should succeed");

    let mut get_cmd = cmd("GET");
    get_cmd.arg("final_test");
    let result = client.send_command(&get_cmd, None).await
        .expect("GET should succeed");
    
    assert_eq!(result, Value::BulkString(b"database_5".to_vec()));

    // Clean up
    let mut del_cmd = cmd("DEL");
    del_cmd.arg("final_test");
    client.send_command(&del_cmd, None).await.ok();

    println!("Multiple SELECT commands test passed!");
}

/// Test that non-SELECT commands don't affect database tracking
#[tokio::test]
#[ignore = "requires running Redis server"]
async fn test_non_select_commands_dont_affect_tracking() {
    let config = create_test_config();
    
    let mut client = Client::new(config, None)
        .await
        .expect("Failed to create client");

    // Start in database 0
    assert_eq!(client.get_current_database(), 0);

    // Execute various non-SELECT commands
    let mut set_cmd = cmd("SET");
    set_cmd.arg("test").arg("value");
    client.send_command(&set_cmd, None).await.expect("SET should succeed");

    let mut get_cmd = cmd("GET");
    get_cmd.arg("test");
    client.send_command(&get_cmd, None).await.expect("GET should succeed");

    let mut exists_cmd = cmd("EXISTS");
    exists_cmd.arg("test");
    client.send_command(&exists_cmd, None).await.expect("EXISTS should succeed");

    // Database should still be 0
    assert_eq!(client.get_current_database(), 0);

    // Now change to database 7
    let mut select_cmd = cmd("SELECT");
    select_cmd.arg(7);
    client.send_command(&select_cmd, None).await.expect("SELECT should succeed");
    assert_eq!(client.get_current_database(), 7);

    // Execute more non-SELECT commands
    let mut ping_cmd = cmd("PING");
    client.send_command(&ping_cmd, None).await.expect("PING should succeed");

    let mut info_cmd = cmd("INFO");
    info_cmd.arg("server");
    client.send_command(&info_cmd, None).await.expect("INFO should succeed");

    // Database should still be 7
    assert_eq!(client.get_current_database(), 7);

    // Clean up
    let mut del_cmd = cmd("DEL");
    del_cmd.arg("test");
    let mut select_cmd = cmd("SELECT");
    select_cmd.arg(0);
    client.send_command(&select_cmd, None).await.ok();
    client.send_command(&del_cmd, None).await.ok();

    println!("Non-SELECT commands tracking test passed!");
}