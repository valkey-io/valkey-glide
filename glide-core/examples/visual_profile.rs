use glide_core::{
    client::StandaloneClient,
    connection_request::{self, NodeAddress, ProtocolVersion, TlsMode},
    request_type::RequestType,
};
use std::collections::HashMap;
use std::time::Instant;
use tokio::sync::mpsc;

#[derive(Debug, Clone)]
struct CallGraphNode {
    name: String,
    self_time: u64,
    total_time: u64,
    call_count: u64,
    children: Vec<String>,
}

struct Profiler {
    nodes: HashMap<String, CallGraphNode>,
    call_stack: Vec<(String, Instant)>,
}

impl Profiler {
    fn new() -> Self {
        Self {
            nodes: HashMap::new(),
            call_stack: Vec::new(),
        }
    }

    fn enter(&mut self, name: &str) {
        self.call_stack.push((name.to_string(), Instant::now()));
    }

    fn exit(&mut self, name: &str) {
        if let Some((stack_name, start_time)) = self.call_stack.pop() {
            assert_eq!(stack_name, name);
            let duration = start_time.elapsed().as_nanos() as u64;
            
            let node = self.nodes.entry(name.to_string()).or_insert_with(|| CallGraphNode {
                name: name.to_string(),
                self_time: 0,
                total_time: 0,
                call_count: 0,
                children: Vec::new(),
            });
            
            node.total_time += duration;
            node.call_count += 1;
            
            // Add parent-child relationship
            if let Some((parent_name, _)) = self.call_stack.last() {
                let parent_node = self.nodes.entry(parent_name.clone()).or_insert_with(|| CallGraphNode {
                    name: parent_name.clone(),
                    self_time: 0,
                    total_time: 0,
                    call_count: 0,
                    children: Vec::new(),
                });
                if !parent_node.children.contains(&name.to_string()) {
                    parent_node.children.push(name.to_string());
                }
            }
        }
    }

    fn generate_dot(&self) -> String {
        let mut dot = String::from("digraph CallGraph {\n");
        dot.push_str("  rankdir=TB;\n");
        dot.push_str("  node [shape=box, style=filled];\n\n");
        
        for (name, node) in &self.nodes {
            let avg_time = if node.call_count > 0 { node.total_time / node.call_count } else { 0 };
            let color = match avg_time {
                0..=1000 => "lightgreen",
                1001..=10000 => "yellow", 
                10001..=100000 => "orange",
                _ => "red",
            };
            
            dot.push_str(&format!(
                "  \"{}\" [label=\"{}\\nCalls: {}\\nTotal: {:.2}ms\\nAvg: {:.2}μs\", fillcolor={}];\n",
                name,
                name,
                node.call_count,
                node.total_time as f64 / 1_000_000.0,
                avg_time as f64 / 1_000.0,
                color
            ));
        }
        
        dot.push_str("\n");
        
        for (name, node) in &self.nodes {
            for child in &node.children {
                dot.push_str(&format!("  \"{}\" -> \"{}\";\n", name, child));
            }
        }
        
        dot.push_str("}\n");
        dot
    }
}

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
    connection_request.client_name = "visual_profile".into();
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

async fn profiled_get_operation(client: &mut StandaloneClient, profiler: &mut Profiler) {
    profiler.enter("get_operation");
    
    profiler.enter("command_creation");
    let mut cmd = RequestType::Get.get_command().expect("Failed to get GET command");
    cmd.arg("profile_key");
    profiler.exit("command_creation");
    
    profiler.enter("send_command");
    let _result = client.send_command(&cmd).await;
    profiler.exit("send_command");
    
    profiler.exit("get_operation");
}

#[tokio::main]
async fn main() {
    let iterations = 100;
    println!("=== VISUAL PROFILING ===");
    
    let mut client = setup_client().await;
    let mut profiler = Profiler::new();
    
    profiler.enter("main");
    
    profiler.enter("setup");
    // Setup already done
    profiler.exit("setup");
    
    profiler.enter("benchmark_loop");
    for _ in 0..iterations {
        profiled_get_operation(&mut client, &mut profiler).await;
    }
    profiler.exit("benchmark_loop");
    
    profiler.exit("main");
    
    // Generate DOT file
    let dot_content = profiler.generate_dot();
    std::fs::write("/tmp/call_graph.dot", &dot_content).expect("Failed to write DOT file");
    
    // Generate SVG using Graphviz
    let output = std::process::Command::new("dot")
        .args(["-Tsvg", "/tmp/call_graph.dot", "-o", "/tmp/call_graph.svg"])
        .output()
        .expect("Failed to run dot command");
    
    if output.status.success() {
        println!("Call graph generated:");
        println!("  DOT file: /tmp/call_graph.dot");
        println!("  SVG file: /tmp/call_graph.svg");
        
        // Also generate PNG
        let _png_output = std::process::Command::new("dot")
            .args(["-Tpng", "/tmp/call_graph.dot", "-o", "/tmp/call_graph.png"])
            .output();
        println!("  PNG file: /tmp/call_graph.png");
    } else {
        println!("Failed to generate SVG: {}", String::from_utf8_lossy(&output.stderr));
    }
    
    // Print summary
    println!("\nProfile Summary:");
    let mut nodes: Vec<_> = profiler.nodes.values().collect();
    nodes.sort_by(|a, b| b.total_time.cmp(&a.total_time));
    
    for node in nodes.iter().take(10) {
        let avg_time = if node.call_count > 0 { node.total_time / node.call_count } else { 0 };
        println!("  {}: {} calls, {:.2}ms total, {:.2}μs avg", 
                 node.name, 
                 node.call_count, 
                 node.total_time as f64 / 1_000_000.0,
                 avg_time as f64 / 1_000.0);
    }
}
