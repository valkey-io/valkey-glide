#!/usr/bin/env bash
set -e

echo "Setting up cluster environment..."

# Determine data directory based on environment
if [ -z "$DATA_DIR" ]; then
    # Default to /data for Docker, ./data for local
    if [ -d "/data" ] && [ -w "/data" ]; then
        DATA_DIR="/data"
    else
        DATA_DIR="$(dirname "$0")/data"
    fi
fi

# Ensure data directory exists and is writable
echo "Checking data directory permissions at $DATA_DIR..."
if [ ! -w "$DATA_DIR" ]; then
    echo "Data directory not writable, attempting to fix permissions..."
    if command -v sudo &> /dev/null; then
        sudo mkdir -p "$DATA_DIR"
        sudo chmod 777 "$DATA_DIR"
    else
        mkdir -p "$DATA_DIR" || echo "Cannot create data directory, please run with appropriate permissions"
        chmod 777 "$DATA_DIR" 2>/dev/null || echo "Cannot set permissions on data directory"
    fi
fi

echo "Cleaning up data directories at $DATA_DIR..."
rm -rf "$DATA_DIR"/* || {
    echo "Permission issue when cleaning data directory, trying with sudo..."
    if command -v sudo &> /dev/null; then
        sudo rm -rf "$DATA_DIR"/*
    else
        echo "Warning: Could not clean data directory. Please check permissions."
    fi
}

# Initialize nodes
init_nodes() {
    for port in $(seq 6379 6381); do
        mkdir -p "$DATA_DIR/${port}"
        cat > "$DATA_DIR/${port}/valkey.conf" <<EOF
port ${port}
cluster-enabled yes
cluster-config-file "$DATA_DIR/${port}/nodes.conf"
cluster-node-timeout 30000
# Disable RDB snapshots completely to avoid persistence issues
save ""
stop-writes-on-bgsave-error no
appendonly yes
dir "$DATA_DIR/${port}"
bind 0.0.0.0
protected-mode no
cluster-announce-ip 127.0.0.1
daemonize yes
EOF
        valkey-server "$DATA_DIR/${port}/valkey.conf"
        
        until valkey-cli -p ${port} ping 2>/dev/null; do
            echo "Waiting for node ${port} to start..."
            sleep 1
        done
        
        # Explicitly disable RDB snapshots after node is running
        valkey-cli -p ${port} CONFIG SET save "" || echo "Could not disable RDB snapshots for port ${port}"
        valkey-cli -p ${port} CONFIG SET stop-writes-on-bgsave-error no || echo "Could not configure bgsave error handling for port ${port}"
    done
}

# Check if cluster is already running
check_existing_cluster() {
    local cluster_state=""
    # Try to get cluster info from the first node
    if cluster_state=$(valkey-cli -p 6379 cluster info 2>/dev/null); then
        if echo "$cluster_state" | grep -q "cluster_state:ok"; then
            echo "A Valkey cluster is already running and properly configured."
            return 0  # Cluster is already running
        fi
    fi
    
    # Check if any of the nodes have data or are already part of a cluster
    for port in $(seq 6379 6381); do
        if valkey-cli -p $port ping 2>/dev/null; then
            echo "Node on port $port is already running."
            
            # Check if node has keys
            local dbsize=$(valkey-cli -p $port dbsize 2>/dev/null || echo "0")
            if [ "$dbsize" != "0" ]; then
                echo "Node on port $port contains data (keys: $dbsize)."
                echo "To recreate the cluster, you must first clean up using the clean.sh script."
                return 0  # Node has data
            fi
            
            # Check if node is part of a cluster
            if valkey-cli -p $port cluster nodes 2>/dev/null | grep -qv "myself"; then
                echo "Node on port $port is already part of a cluster."
                echo "To recreate the cluster, you must first clean up using the clean.sh script."
                return 0  # Node is part of a cluster
            fi
        fi
    done
    
    return 1  # No existing cluster found or nodes are clean
}

echo "Initializing nodes..."

# Check if cluster is already running before initializing new nodes
if check_existing_cluster; then
    echo "Using existing cluster setup."
else
    # Initialize cluster nodes
    init_nodes

    echo "Waiting for nodes to stabilize..."
    sleep 5

    echo "Initializing cluster..."
    yes "yes" | valkey-cli --cluster create \
        127.0.0.1:6379 127.0.0.1:6380 127.0.0.1:6381 \
        --cluster-replicas 0 || {
        echo "Failed to create cluster. This could happen if nodes aren't empty."
        echo "Try running clean.sh to remove existing data."
        exit 1
    }

    # Wait for cluster to stabilize
    echo "Waiting for cluster to stabilize..."
    for i in {1..30}; do
        if valkey-cli -p 6379 cluster info | grep -q "cluster_state:ok"; then
            echo "Cluster is stable"
            break
        fi
        echo "Waiting for cluster to stabilize (attempt $i)..."
        sleep 2
    done
fi

echo "Cluster initialization complete"

# Check final cluster status
echo "Final cluster status:"
valkey-cli -p 6379 cluster info

echo "Valkey cluster is now ready for benchmarking"

# If RUN_AS_DAEMON is set, keep monitoring the cluster (for Docker mode)
if [ -n "$RUN_AS_DAEMON" ]; then
    echo "Running in daemon mode, monitoring cluster..."
    while true; do
        echo "Cluster Status at $(date):"
        valkey-cli -p 6379 cluster info || echo "Failed to get cluster info"
        sleep 60
    done
fi
