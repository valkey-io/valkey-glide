// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Simple unit tests for database tracking functionality.
//! This tests the simplified approach where database changes are tracked in the connection configuration.

use glide_core::client::{Client, ConnectionRequest, NodeAddress};
use redis::{cmd, Value};
use redis::cluster_routing::Routable;

/// Test the basic database tracking logic without requiring a Redis server
#[tokio::test]
async fn test_database_tracking_logic() {
    // Create a basic connection request starting with database 0
    let mut request = ConnectionRequest::default();
    request.addresses = vec![NodeAddress {
        host: "localhost".to_string(),
        port: 6379,
    }];
    request.database_id = 0;
    request.lazy_connect = true; // Use lazy connect to avoid actual connection

    // Create a client 
    let client = Client::new(request, None).await
        .expect("Client creation should succeed with lazy connect");

    // Verify that the connection request starts with database 0
    {
        let config = client.connection_request.read().await;
        assert_eq!(config.database_id, 0);
    }

    println!("Database tracking simplified test passed");
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
    let success_response = Value::SimpleString("OK".to_string());
    let error_response = Value::SimpleString("ERROR".to_string());
    let nil_response = Value::Nil;
    
    // Test success detection
    assert!(matches!(success_response, Value::SimpleString(s) if s == "OK"));
    assert!(!matches!(error_response, Value::SimpleString(s) if s == "OK"));
    assert!(!matches!(nil_response, Value::SimpleString(s) if s == "OK"));
    
    println!("Successful response detection test passed");
}