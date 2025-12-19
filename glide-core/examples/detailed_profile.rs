use glide_core::{
    client::StandaloneClient,
    connection_request::{self, NodeAddress, ProtocolVersion, TlsMode},
    request_type::RequestType,
};
use std::time::Instant;
use tokio::sync::mpsc;

async fn setup_client() -> StandaloneClient {
    let mut node_address = NodeAddress::new();
    node_address.host = "127.0.0.1".into();
    node_address.port = 6379;
    
    let mut connection_request = connection_request::ConnectionRequest::new();
    connection_request.addresses = vec![node_address];
    connection_request.tls_mode = TlsMode::NoTls.into();
    connection_request.cluster_mode_enabled = false;
    connection_request.request_timeout = 250;
    connection_request.protocol = ProtocolVersion::RESP2.into();
    connection_request.client_name = "detailed_profile".into();
    connection_request.database_id = 0;

    let (push_sender, _push_receiver) = mpsc::unbounded_channel();
    let mut client = StandaloneClient::create_client(connection_request.into(), Some(push_sender), None)
        .await
        .expect("Failed to create client");

    // Setup key
    let mut set_cmd = redis::Cmd::new();
    set_cmd.arg("SET").arg("profile_key").arg("profile_value");
    client.send_command(&set_cmd).await.expect("Failed to set key");

    client
}

#[tokio::main]
async fn main() {
    let iterations = 1000;
    println!("=== DETAILED DIRECT PATH PROFILE ===");
    
    let mut client = setup_client().await;
    
    let mut command_creation_time = std::time::Duration::ZERO;
    let mut send_command_time = std::time::Duration::ZERO;
    let mut total_time = std::time::Duration::ZERO;
    
    let overall_start = Instant::now();
    
    for _ in 0..iterations {
        // Time command creation
        let cmd_start = Instant::now();
        let mut cmd = RequestType::Get.get_command().expect("Failed to get GET command");
        cmd.arg("profile_key");
        command_creation_time += cmd_start.elapsed();
        
        // Time send_command
        let send_start = Instant::now();
        let _result = client.send_command(&cmd).await;
        send_command_time += send_start.elapsed();
    }
    
    total_time = overall_start.elapsed();
    
    println!("Results for {} iterations:", iterations);
    println!("  Total time:           {:?}", total_time);
    println!("  Command creation:     {:?} ({:.1}%)", command_creation_time, 
             command_creation_time.as_nanos() as f64 / total_time.as_nanos() as f64 * 100.0);
    println!("  Send command:         {:?} ({:.1}%)", send_command_time,
             send_command_time.as_nanos() as f64 / total_time.as_nanos() as f64 * 100.0);
    println!("  Other overhead:       {:?} ({:.1}%)", 
             total_time - command_creation_time - send_command_time,
             (total_time - command_creation_time - send_command_time).as_nanos() as f64 / total_time.as_nanos() as f64 * 100.0);
    println!("  TPS: {:.2}", iterations as f64 / total_time.as_secs_f64());
    
    // Per-operation averages
    println!("\nPer-operation averages:");
    println!("  Command creation:     {:?}", command_creation_time / iterations as u32);
    println!("  Send command:         {:?}", send_command_time / iterations as u32);
    println!("  Total per operation:  {:?}", total_time / iterations as u32);
}
