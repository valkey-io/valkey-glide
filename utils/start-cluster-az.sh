#!/bin/bash
# Mimics startClusterForAz task - creates a 4-replica cluster for AZ testing

set -e

echo "Current directory: $(pwd)"

# Ensure clusters directory exists with proper permissions
if [ ! -d "clusters" ]; then
    mkdir -p clusters || {
        echo "Failed to create clusters directory, trying alternative approach"
        mkdir clusters 2>/dev/null || true
    }
fi
chmod 755 clusters 2>/dev/null || true

CLUSTER_DIR="clusters/cluster-az-$(date +%Y-%m-%dT%H-%M-%SZ)-$(openssl rand -hex 3)"
mkdir -p "$CLUSTER_DIR"

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
