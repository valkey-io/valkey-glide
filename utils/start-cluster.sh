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
    
    valkey-server \
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
        --bind 127.0.0.1 || {
        echo "Failed to start Valkey server on port $port" >&2
        exit 1
    }
    
    echo "Started node on port $port"
done

# Wait for servers to start
sleep 2

# Create cluster
echo "Creating cluster..."
valkey-cli --cluster create \
    127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 \
    127.0.0.1:7003 127.0.0.1:7004 127.0.0.1:7005 \
    --cluster-replicas 1 \
    --cluster-yes

# Wait for cluster to stabilize
sleep 3

# Output cluster endpoints
echo "CLUSTER_HOSTS=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002,127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005"
echo "Cluster created successfully in $CLUSTER_DIR"

echo "Creating cluster in $CLUSTER_DIR"

# Start 6 nodes (3 primaries + 3 replicas)
PORTS=(7000 7001 7002 7003 7004 7005)
PIDS=()

for port in "${PORTS[@]}"; do
    node_dir="$CLUSTER_DIR/$port"
    mkdir -p "$node_dir"
    
    valkey-server \
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
        --bind 127.0.0.1
    
    echo "Started node on port $port"
done

# Wait for servers to start
sleep 2

# Create cluster
echo "Creating cluster..."
valkey-cli --cluster create \
    127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 \
    127.0.0.1:7003 127.0.0.1:7004 127.0.0.1:7005 \
    --cluster-replicas 1 \
    --cluster-yes

# Wait for cluster to stabilize
sleep 3

# Output cluster endpoints
echo "CLUSTER_HOSTS=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002,127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005"
echo "Cluster created successfully in $CLUSTER_DIR"
