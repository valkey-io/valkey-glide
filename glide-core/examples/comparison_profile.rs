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

    fn generate_comparison_dot(&self, other: &Profiler, title: &str) -> String {
        let mut dot = String::from(&format!("digraph {} {{\n", title.replace(" ", "_")));
        dot.push_str("  rankdir=TB;\n");
        dot.push_str("  node [shape=box, style=filled];\n");
        dot.push_str(&format!("  label=\"{}\";\n", title));
        dot.push_str("  labelloc=t;\n\n");
        
        // Combine nodes from both profilers
        let mut all_nodes = HashMap::new();
        for (name, node) in &self.nodes {
            all_nodes.insert(name.clone(), (Some(node), None));
        }
        for (name, node) in &other.nodes {
            let entry = all_nodes.entry(name.clone()).or_insert((None, None));
            entry.1 = Some(node);
        }
        
        for (name, (self_node, other_node)) in &all_nodes {
            let (self_time, self_calls) = if let Some(node) = self_node {
                (node.total_time, node.call_count)
            } else {
                (0, 0)
            };
            
            let (other_time, other_calls) = if let Some(node) = other_node {
                (node.total_time, node.call_count)
            } else {
                (0, 0)
            };
            
            let self_avg = if self_calls > 0 { self_time / self_calls } else { 0 };
            let other_avg = if other_calls > 0 { other_time / other_calls } else { 0 };
            
            let color = if self_avg > other_avg * 2 {
                "lightcoral"  // Self is much slower
            } else if other_avg > self_avg * 2 {
                "lightgreen"  // Other is much slower (self is faster)
            } else {
                "lightyellow" // Similar performance
            };
            
            dot.push_str(&format!(
                "  \"{}\" [label=\"{}\\nDirect: {:.2}μs ({} calls)\\nFFI: {:.2}μs ({} calls)\", fillcolor={}];\n",
                name,
                name,
                self_avg as f64 / 1_000.0,
                self_calls,
                other_avg as f64 / 1_000.0,
                other_calls,
                color
            ));
        }
        
        dot.push_str("\n");
        
        // Add edges from self (primary profiler)
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
    connection_request.client_name = "comparison_profile".into();
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

async fn profiled_direct_operation(client: &mut StandaloneClient, profiler: &mut Profiler) {
    profiler.enter("get_operation");
    
    profiler.enter("requesttype_creation");
    let mut cmd = RequestType::Get.get_command().expect("Failed to get GET command");
    cmd.arg("profile_key");
    profiler.exit("requesttype_creation");
    
    profiler.enter("direct_send_command");
    let _result = client.send_command(&cmd).await;
    profiler.exit("direct_send_command");
    
    profiler.exit("get_operation");
}

async fn profiled_manual_operation(client: &mut StandaloneClient, profiler: &mut Profiler) {
    profiler.enter("get_operation");
    
    profiler.enter("manual_creation");
    let mut cmd = redis::Cmd::new();
    cmd.arg("GET").arg("profile_key");
    profiler.exit("manual_creation");
    
    profiler.enter("manual_send_command");
    let _result = client.send_command(&cmd).await;
    profiler.exit("manual_send_command");
    
    profiler.exit("get_operation");
}

#[tokio::main]
async fn main() {
    let iterations = 50;
    println!("=== COMPARISON PROFILING ===");
    
    // Profile RequestType path
    let mut client1 = setup_client().await;
    let mut direct_profiler = Profiler::new();
    
    direct_profiler.enter("main");
    direct_profiler.enter("benchmark_loop");
    for _ in 0..iterations {
        profiled_direct_operation(&mut client1, &mut direct_profiler).await;
    }
    direct_profiler.exit("benchmark_loop");
    direct_profiler.exit("main");
    
    // Profile manual path
    let mut client2 = setup_client().await;
    let mut manual_profiler = Profiler::new();
    
    manual_profiler.enter("main");
    manual_profiler.enter("benchmark_loop");
    for _ in 0..iterations {
        profiled_manual_operation(&mut client2, &mut manual_profiler).await;
    }
    manual_profiler.exit("benchmark_loop");
    manual_profiler.exit("main");
    
    // Generate comparison DOT file
    let dot_content = direct_profiler.generate_comparison_dot(&manual_profiler, "Direct vs Manual Command Creation");
    std::fs::write("/tmp/comparison_graph.dot", &dot_content).expect("Failed to write DOT file");
    
    // Generate SVG using Graphviz
    let output = std::process::Command::new("dot")
        .args(["-Tsvg", "/tmp/comparison_graph.dot", "-o", "/tmp/comparison_graph.svg"])
        .output()
        .expect("Failed to run dot command");
    
    if output.status.success() {
        println!("Comparison graph generated:");
        println!("  DOT file: /tmp/comparison_graph.dot");
        println!("  SVG file: /tmp/comparison_graph.svg");
        
        // Also generate PNG
        let _png_output = std::process::Command::new("dot")
            .args(["-Tpng", "/tmp/comparison_graph.dot", "-o", "/tmp/comparison_graph.png"])
            .output();
        println!("  PNG file: /tmp/comparison_graph.png");
    } else {
        println!("Failed to generate SVG: {}", String::from_utf8_lossy(&output.stderr));
    }
    
    // Print comparison summary
    println!("\nDirect (RequestType) Profile Summary:");
    let mut direct_nodes: Vec<_> = direct_profiler.nodes.values().collect();
    direct_nodes.sort_by(|a, b| b.total_time.cmp(&a.total_time));
    
    for node in direct_nodes.iter().take(10) {
        let avg_time = if node.call_count > 0 { node.total_time / node.call_count } else { 0 };
        println!("  {}: {} calls, {:.2}ms total, {:.2}μs avg", 
                 node.name, 
                 node.call_count, 
                 node.total_time as f64 / 1_000_000.0,
                 avg_time as f64 / 1_000.0);
    }
    
    println!("\nManual Profile Summary:");
    let mut manual_nodes: Vec<_> = manual_profiler.nodes.values().collect();
    manual_nodes.sort_by(|a, b| b.total_time.cmp(&a.total_time));
    
    for node in manual_nodes.iter().take(10) {
        let avg_time = if node.call_count > 0 { node.total_time / node.call_count } else { 0 };
        println!("  {}: {} calls, {:.2}ms total, {:.2}μs avg", 
                 node.name, 
                 node.call_count, 
                 node.total_time as f64 / 1_000_000.0,
                 avg_time as f64 / 1_000.0);
    }
}
