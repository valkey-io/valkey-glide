#!/bin/bash
# Mimics startClusterTls task - creates a TLS-enabled cluster

set -e

# Force UTF-8 encoding for output
export LC_ALL=C.UTF-8
export LANG=C.UTF-8

# Send debug output to stderr so Gradle shows it
exec 2> >(tee -a /tmp/cluster-debug.log >&2)

echo "=== START CLUSTER TLS SCRIPT DEBUG ===" >&2
echo "Current directory: $(pwd)" >&2

# Try to use WSL temp directory instead of Windows filesystem
WSL_TEMP_DIR="/tmp/valkey-clusters"
echo "Using WSL temp directory: $WSL_TEMP_DIR" >&2

# Create clusters directory in WSL filesystem
mkdir -p "$WSL_TEMP_DIR" || {
    echo "Failed to create WSL temp directory" >&2
    exit 1
}

CLUSTER_DIR="$WSL_TEMP_DIR/cluster-tls-$(date +%Y-%m-%dT%H-%M-%SZ)-$(openssl rand -hex 3)"
echo "Creating cluster directory: $CLUSTER_DIR" >&2

mkdir -p "$CLUSTER_DIR" || {
    echo "Failed to create cluster directory in WSL filesystem" >&2
    exit 1
}

echo "Successfully created cluster directory: $CLUSTER_DIR" >&2
echo "=== END CLUSTER TLS SCRIPT DEBUG ===" >&2

# Check which server is available
if command -v valkey-server >/dev/null 2>&1; then
    SERVER_CMD="valkey-server"
    CLI_CMD="valkey-cli"
    echo "Using Valkey server" >&2
elif command -v redis-server >/dev/null 2>&1; then
    SERVER_CMD="redis-server"
    CLI_CMD="redis-cli"
    echo "Using Redis server" >&2
else
    echo "ERROR: Neither valkey-server nor redis-server found" >&2
    exit 1
fi

# Test valkey-server directly
echo "Testing $SERVER_CMD directly..." >&2
$SERVER_CMD --version >&2 || {
    echo "ERROR: $SERVER_CMD --version failed" >&2
    exit 1
}

echo "Creating TLS cluster in $CLUSTER_DIR"

# Start 6 nodes (3 primaries + 3 replicas) with TLS
PORTS=(7010 7011 7012 7013 7014 7015)
PIDS=()

for port in "${PORTS[@]}"; do
    node_dir="$CLUSTER_DIR/$port"
    echo "Creating node directory: $node_dir" >&2
    mkdir -p "$node_dir" || {
        echo "Failed to create node directory: $node_dir" >&2
        exit 1
    }
    
    # Ensure the node directory is writable
    chmod 755 "$node_dir" 2>/dev/null || true
    
    echo "Starting TLS Valkey server on port $port in directory $node_dir" >&2
    
    # Get absolute path to TLS certificates
    TLS_DIR="$(cd "$(dirname "$0")" && pwd)/tls_crts"
    echo "Using TLS certificates from: $TLS_DIR" >&2
    
    # Generate TLS certificates if they don't exist (matching cluster_manager.py behavior)
    if [ ! -f "$TLS_DIR/server.crt" ] || [ ! -f "$TLS_DIR/server.key" ] || [ ! -f "$TLS_DIR/ca.crt" ]; then
        echo "Generating TLS certificates..." >&2
        mkdir -p "$TLS_DIR"
        
        # Generate CA key and certificate (matching cluster_manager.py)
        openssl genrsa -out "$TLS_DIR/ca.key" 4096 2>/dev/null
        openssl req -x509 -new -nodes -sha256 -key "$TLS_DIR/ca.key" -days 3650 \
            -subj "/O=Valkey GLIDE Test/CN=Certificate Authority" \
            -out "$TLS_DIR/ca.crt" 2>/dev/null
        
        # Generate server key and certificate (matching cluster_manager.py)
        openssl genrsa -out "$TLS_DIR/server.key" 2048 2>/dev/null
        openssl req -new -sha256 -subj "/O=Valkey GLIDE Test/CN=Generic-cert" \
            -key "$TLS_DIR/server.key" | \
        openssl x509 -req -sha256 -CA "$TLS_DIR/ca.crt" -CAkey "$TLS_DIR/ca.key" \
            -CAcreateserial -days 3650 \
            -extensions v3_req -extfile <(echo "keyUsage = digitalSignature, keyEncipherment
subjectAltName = IP:127.0.0.1,DNS:localhost") \
            -out "$TLS_DIR/server.crt" 2>/dev/null
        
        echo "TLS certificates generated successfully" >&2
    fi
    
    # Start valkey-server with TLS configuration and disable PID file
    $SERVER_CMD \
        --port $port \
        --cluster-enabled yes \
        --cluster-config-file "$node_dir/nodes.conf" \
        --cluster-node-timeout 5000 \
        --appendonly yes \
        --appendfilename "appendonly-$port.aof" \
        --dbfilename "dump-$port.rdb" \
        --logfile "$node_dir/server.log" \
        --daemonize yes \
        --dir "$node_dir" \
        --protected-mode no \
        --bind 127.0.0.1 \
        --pidfile "" \
        --tls-port $((port + 1000)) \
        --tls-cert-file "$TLS_DIR/server.crt" \
        --tls-key-file "$TLS_DIR/server.key" \
        --tls-ca-cert-file "$TLS_DIR/ca.crt" \
        --tls-cluster yes 2>&1 || {
        echo "Failed to start TLS Valkey server on port $port" >&2
        echo "Server log:" >&2
        cat "$node_dir/server.log" 2>/dev/null | tail -10 >&2 || echo "No log file" >&2
        exit 1
    }
    
    # Wait a moment and check if the server actually started
    sleep 2
    
    # Check for the actual process format: valkey-server 127.0.0.1:7005
    if pgrep -f "127.0.0.1:$port" >/dev/null 2>&1; then
        echo "SUCCESS: Server process found for port $port" >&2
    else
        echo "ERROR: No server process found for port $port" >&2
        echo "Server log:" >&2
        cat "$node_dir/server.log" 2>/dev/null | tail -5 >&2 || echo "No log file" >&2
    fi
    
    echo "Started TLS node on port $port (TLS port $((port + 1000)))"
    
    echo "Started TLS node on port $port (TLS port $((port + 1000)))"
done

# Wait for servers to start
sleep 2

# Create cluster using regular ports (not TLS ports for cluster creation)
echo "Creating TLS cluster..." >&2
echo "Running: $CLI_CMD --cluster create 127.0.0.1:7010 127.0.0.1:7011 127.0.0.1:7012 127.0.0.1:7013 127.0.0.1:7014 127.0.0.1:7015 --cluster-replicas 1 --cluster-yes" >&2
timeout 30 $CLI_CMD --cluster create \
    127.0.0.1:7010 127.0.0.1:7011 127.0.0.1:7012 \
    127.0.0.1:7013 127.0.0.1:7014 127.0.0.1:7015 \
    --cluster-replicas 1 \
    --cluster-yes 2>&1 | tee /dev/stderr || {
    echo "ERROR: TLS Cluster creation failed or timed out" >&2
    echo "Checking server processes:" >&2
    ps aux | grep valkey | grep -v grep >&2 || echo "No valkey processes" >&2
    exit 1
}

echo "TLS Cluster creation completed" >&2

# Wait for cluster to stabilize
sleep 3

# Output cluster endpoints (regular ports, TLS is handled by server config)
printf "CLUSTER_TLS_HOSTS=127.0.0.1:7010,127.0.0.1:7011,127.0.0.1:7012,127.0.0.1:7013,127.0.0.1:7014,127.0.0.1:7015\r\n"
printf "TLS Cluster created successfully in $CLUSTER_DIR\r\n"

# Ensure output is flushed and exit immediately
sync
exit 0
