#!/bin/bash
# Mimics startCluster task - creates a 3-node cluster with 1 replica each

set -e

# Force UTF-8 encoding for output
export LC_ALL=C.UTF-8
export LANG=C.UTF-8

# Send debug output to stderr so Gradle shows it
exec 2> >(tee -a /tmp/cluster-debug.log >&2)

echo "=== START CLUSTER SCRIPT DEBUG ===" >&2
echo "Current directory: $(pwd)" >&2

# Try to use WSL temp directory instead of Windows filesystem
WSL_TEMP_DIR="/tmp/valkey-clusters"
echo "Using WSL temp directory: $WSL_TEMP_DIR" >&2

# Create clusters directory in WSL filesystem
mkdir -p "$WSL_TEMP_DIR" || {
    echo "Failed to create WSL temp directory" >&2
    exit 1
}

CLUSTER_DIR="$WSL_TEMP_DIR/cluster-$(date +%Y-%m-%dT%H-%M-%SZ)-$(openssl rand -hex 3)"
echo "Creating cluster directory: $CLUSTER_DIR" >&2

mkdir -p "$CLUSTER_DIR" || {
    echo "Failed to create cluster directory in WSL filesystem" >&2
    exit 1
}

echo "Successfully created cluster directory: $CLUSTER_DIR" >&2
echo "=== END CLUSTER SCRIPT DEBUG ===" >&2

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

# Test if we can bind to ports in WSL
echo "Testing port binding in WSL..." >&2
if nc -l 7999 </dev/null >/dev/null 2>&1 & 
then
    TEST_PID=$!
    sleep 1
    kill $TEST_PID 2>/dev/null
    echo "Port binding test successful" >&2
else
    echo "WARNING: Port binding test failed, but continuing..." >&2
fi

# Test valkey-server directly
echo "Testing $SERVER_CMD directly..." >&2
$SERVER_CMD --version >&2 || {
    echo "ERROR: $SERVER_CMD --version failed" >&2
    exit 1
}

echo "Creating cluster in $CLUSTER_DIR"

# Start 6 nodes (3 primaries + 3 replicas)
PORTS=(7000 7001 7002 7003 7004 7005)
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
    
    echo "Starting Valkey server on port $port in directory $node_dir" >&2
    
    # Start valkey-server and capture output
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
        --pidfile "" 2>&1 || {
        echo "Failed to start Valkey server on port $port" >&2
        echo "Checking if valkey-server is available..." >&2
        which $SERVER_CMD >&2 || echo "$SERVER_CMD not found in PATH" >&2
        echo "Attempting to read server log..." >&2
        cat "$node_dir/server.log" 2>/dev/null || echo "No log file created" >&2
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
    
    echo "Started node on port $port"
done

# Wait for servers to start
sleep 2

# Create cluster
echo "Creating cluster..." >&2
timeout 30 $CLI_CMD --cluster create \
    127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 \
    127.0.0.1:7003 127.0.0.1:7004 127.0.0.1:7005 \
    --cluster-replicas 0 \
    --cluster-yes || {
    echo "ERROR: Cluster creation failed or timed out" >&2
    echo "Checking server processes:" >&2
    ps aux | grep valkey | grep -v grep >&2 || echo "No valkey processes" >&2
    exit 1
}

echo "Cluster creation completed" >&2

# Wait for cluster to stabilize
sleep 3

# Output cluster endpoints
printf "CLUSTER_HOSTS=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002,127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005\r\n"
printf "Cluster created successfully in $CLUSTER_DIR\r\n"
