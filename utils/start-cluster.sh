#!/bin/bash
# Mimics startCluster task - creates a 3-node cluster with 1 replica each

set -e

# Force UTF-8 encoding for output
export LC_ALL=C.UTF-8
export LANG=C.UTF-8

# Debug: Show current directory without any potential formatting issues
printf "Current directory: %s\n" "$(pwd)"

# Ensure clusters directory exists with proper permissions
if [ ! -d "clusters" ]; then
    mkdir -p clusters || {
        printf "Failed to create clusters directory, trying alternative approach\n"
        mkdir clusters 2>/dev/null || true
    }
fi
chmod 755 clusters 2>/dev/null || true

# Verify clusters directory exists and is accessible
if [ ! -d "clusters" ]; then
    printf "ERROR: clusters directory does not exist after creation attempts\n"
    exit 1
fi

CLUSTER_DIR="clusters/cluster-$(date +%Y-%m-%dT%H-%M-%SZ)-$(openssl rand -hex 3)"
printf "Creating cluster directory: %s\n" "$CLUSTER_DIR"

# Try multiple approaches to create the cluster directory
mkdir -p "$CLUSTER_DIR" || {
    printf "mkdir -p failed, trying alternatives\n"
    mkdir "$CLUSTER_DIR" 2>/dev/null || {
        printf "Regular mkdir failed, trying with sudo\n"
        sudo mkdir -p "$CLUSTER_DIR" 2>/dev/null || {
            printf "All directory creation methods failed\n"
            exit 1
        }
    }
}

# Verify the cluster directory was created
if [ ! -d "$CLUSTER_DIR" ]; then
    printf "ERROR: Cluster directory %s was not created successfully\n" "$CLUSTER_DIR"
    exit 1
fi

printf "Successfully created cluster directory: %s\n" "$CLUSTER_DIR"

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
