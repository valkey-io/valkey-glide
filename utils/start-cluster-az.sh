#!/bin/bash
# Mimics startClusterForAz task - creates a 4-replica cluster for AZ testing

set -e

# Force UTF-8 encoding for output
export LC_ALL=C.UTF-8
export LANG=C.UTF-8

# Send debug output to stderr so Gradle shows it
exec 2> >(tee -a /tmp/cluster-debug.log >&2)

echo "=== START CLUSTER AZ SCRIPT DEBUG ===" >&2
echo "Current directory: $(pwd)" >&2
echo "User: $(whoami)" >&2
echo "Date: $(date)" >&2

# Check if we can write to current directory
touch test-write.tmp && rm test-write.tmp && echo "Can write to current directory" >&2 || echo "Cannot write to current directory" >&2

# Ensure clusters directory exists with proper permissions
echo "Checking if clusters directory exists..." >&2
if [ ! -d "clusters" ]; then
    echo "clusters directory does not exist, creating..." >&2
    mkdir -p clusters || {
        echo "mkdir -p clusters failed, trying alternatives" >&2
        mkdir clusters 2>/dev/null || echo "mkdir clusters also failed" >&2
    }
else
    echo "clusters directory already exists" >&2
fi

echo "Setting permissions on clusters directory..." >&2
chmod 755 clusters 2>/dev/null || echo "chmod failed" >&2

# Verify clusters directory exists and is accessible
echo "Verifying clusters directory..." >&2
if [ ! -d "clusters" ]; then
    echo "ERROR: clusters directory does not exist after creation attempts" >&2
    exit 1
else
    echo "clusters directory verified to exist" >&2
fi

CLUSTER_DIR="clusters/cluster-az-$(date +%Y-%m-%dT%H-%M-%SZ)-$(openssl rand -hex 3)"
echo "Creating cluster directory: $CLUSTER_DIR" >&2

# Try to create the cluster directory
mkdir -p "$CLUSTER_DIR" || {
    echo "mkdir -p failed for $CLUSTER_DIR" >&2
    exit 1
}

# Verify the cluster directory was created
if [ ! -d "$CLUSTER_DIR" ]; then
    echo "ERROR: Cluster directory $CLUSTER_DIR was not created successfully" >&2
    exit 1
else
    echo "Successfully created cluster directory: $CLUSTER_DIR" >&2
fi

echo "=== END CLUSTER AZ SCRIPT DEBUG ===" >&2

echo "Creating AZ cluster in $CLUSTER_DIR"

# Start 5 nodes (1 primary + 4 replicas)
PORTS=(7000 7001 7002 7003 7004)

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
    
    echo "Started AZ node on port $port"
done

# Wait for servers to start
sleep 2

# Create cluster with 4 replicas (1 primary + 4 replicas)
echo "Creating AZ cluster with 4 replicas..."
valkey-cli --cluster create \
    127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 127.0.0.1:7003 127.0.0.1:7004 \
    --cluster-replicas 4 \
    --cluster-yes

# Wait for cluster to stabilize
sleep 3

# Output cluster endpoints
echo "AZ_CLUSTER_HOSTS=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002,127.0.0.1:7003,127.0.0.1:7004"
echo "AZ cluster created successfully in $CLUSTER_DIR"
