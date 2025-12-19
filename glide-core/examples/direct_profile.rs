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
    connection_request.client_name = "direct_profile".into();
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
    println!("=== DIRECT PATH PROFILE ===");
    
    let mut client = setup_client().await;
    let start = Instant::now();
    
    for _ in 0..iterations {
        // Direct path: RequestType enum
        let mut cmd = RequestType::Get.get_command().expect("Failed to get GET command");
        cmd.arg("profile_key");
        let _result = client.send_command(&cmd).await;
    }
    
    let duration = start.elapsed();
    let tps = iterations as f64 / duration.as_secs_f64();
    
    println!("Direct path completed: {} iterations in {:?}", iterations, duration);
    println!("TPS: {:.2}", tps);
}
