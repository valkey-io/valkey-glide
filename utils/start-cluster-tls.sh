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

echo "Creating TLS cluster in $CLUSTER_DIR"

# Start 6 nodes (3 primaries + 3 replicas) with TLS
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
    
    echo "Starting TLS Valkey server on port $port in directory $node_dir" >&2
    
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
        --bind 127.0.0.1 \
        --tls-port $((port + 1000)) \
        --tls-cert-file ../tls_crts/server.crt \
        --tls-key-file ../tls_crts/server.key \
        --tls-ca-cert-file ../tls_crts/ca.crt \
        --tls-cluster yes || {
        echo "Failed to start TLS Valkey server on port $port" >&2
        exit 1
    }
    
    echo "Started TLS node on port $port (TLS port $((port + 1000)))"
done

# Wait for servers to start
sleep 2

# Create cluster using TLS ports
echo "Creating TLS cluster..."
valkey-cli --cluster create \
    127.0.0.1:8000 127.0.0.1:8001 127.0.0.1:8002 \
    127.0.0.1:8003 127.0.0.1:8004 127.0.0.1:8005 \
    --cluster-replicas 1 \
    --cluster-yes \
    --tls \
    --cert ../tls_crts/client.crt \
    --key ../tls_crts/client.key \
    --cacert ../tls_crts/ca.crt

# Wait for cluster to stabilize
sleep 3

# Output cluster endpoints (TLS ports)
echo "CLUSTER_TLS_HOSTS=127.0.0.1:8000,127.0.0.1:8001,127.0.0.1:8002,127.0.0.1:8003,127.0.0.1:8004,127.0.0.1:8005"
echo "TLS Cluster created successfully in $CLUSTER_DIR"

echo "Creating TLS cluster in $CLUSTER_DIR"

# Generate TLS certificates if they don't exist
TLS_DIR="$CLUSTER_DIR/tls"
mkdir -p "$TLS_DIR"

if [ ! -f "$TLS_DIR/server.crt" ]; then
    echo "Generating TLS certificates..."
    
    # Generate CA key and certificate
    openssl genrsa -out "$TLS_DIR/ca.key" 2048
    openssl req -new -x509 -key "$TLS_DIR/ca.key" -out "$TLS_DIR/ca.crt" -days 365 -subj "/CN=ValkeyCa"
    
    # Generate server key and certificate
    openssl genrsa -out "$TLS_DIR/server.key" 2048
    openssl req -new -key "$TLS_DIR/server.key" -out "$TLS_DIR/server.csr" -subj "/CN=127.0.0.1"
    openssl x509 -req -in "$TLS_DIR/server.csr" -CA "$TLS_DIR/ca.crt" -CAkey "$TLS_DIR/ca.key" -out "$TLS_DIR/server.crt" -days 365 -CAcreateserial
    
    # Generate client key and certificate
    openssl genrsa -out "$TLS_DIR/client.key" 2048
    openssl req -new -key "$TLS_DIR/client.key" -out "$TLS_DIR/client.csr" -subj "/CN=client"
    openssl x509 -req -in "$TLS_DIR/client.csr" -CA "$TLS_DIR/ca.crt" -CAkey "$TLS_DIR/ca.key" -out "$TLS_DIR/client.crt" -days 365
fi

# Start 6 TLS nodes
PORTS=(7000 7001 7002 7003 7004 7005)

for port in "${PORTS[@]}"; do
    node_dir="$CLUSTER_DIR/$port"
    mkdir -p "$node_dir"
    
    valkey-server \
        --tls-port $port \
        --port 0 \
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
        --tls-cert-file "$TLS_DIR/server.crt" \
        --tls-key-file "$TLS_DIR/server.key" \
        --tls-ca-cert-file "$TLS_DIR/ca.crt" \
        --tls-cluster yes \
        --tls-replication yes \
        --tls-auth-clients no
    
    echo "Started TLS node on port $port"
done

# Wait for servers to start
sleep 3

# Create TLS cluster
echo "Creating TLS cluster..."
valkey-cli --tls \
    --cert "$TLS_DIR/client.crt" \
    --key "$TLS_DIR/client.key" \
    --cacert "$TLS_DIR/ca.crt" \
    --cluster create \
    127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 \
    127.0.0.1:7003 127.0.0.1:7004 127.0.0.1:7005 \
    --cluster-replicas 1 \
    --cluster-yes

# Wait for cluster to stabilize
sleep 3

# Output cluster endpoints
echo "CLUSTER_TLS_HOSTS=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002,127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005"
echo "TLS cluster created successfully in $CLUSTER_DIR"
