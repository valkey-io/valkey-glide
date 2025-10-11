#!/bin/bash
set -e

# Function to start valkey with custom configuration
start_valkey() {
    local port=${VALKEY_PORT:-6379}
    local tls=${VALKEY_TLS:-false}

    # Generate dynamic configuration
    cat > /tmp/valkey.conf << EOF
# Basic Valkey configuration
port $port
bind 0.0.0.0
protected-mode no
save 900 1
save 300 10
save 60 10000
rdbcompression yes
dbfilename dump.rdb
dir /var/lib/valkey
maxmemory-policy allkeys-lru
timeout 0
tcp-keepalive 300
EOF

    # Add cluster configuration if needed
    if [ "$CLUSTER_MODE" = "true" ]; then
        cat >> /tmp/valkey.conf << EOF
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 15000
cluster-announce-hostname-on-startup no
cluster-announce-port $port
cluster-announce-bus-port $((port + 10000))
EOF
    fi

    # Configure TLS if enabled
    if [ "$tls" = "true" ]; then
        echo "tls-port $port" >> /tmp/valkey.conf
        echo "port 0" >> /tmp/valkey.conf
        echo "tls-cert-file /tls/server.crt" >> /tmp/valkey.conf
        echo "tls-key-file /tls/server.key" >> /tmp/valkey.conf
        echo "tls-ca-cert-file /tls/ca.crt" >> /tmp/valkey.conf
        echo "tls-protocols TLSv1.2 TLSv1.3" >> /tmp/valkey.conf
    fi

    # Start valkey-server
    exec valkey-server /tmp/valkey.conf
}

# If no command provided or valkey-server command, start our custom valkey
if [ $# -eq 0 ] || [ "$1" = "valkey-server" ]; then
    start_valkey
else
    # Execute the provided command
    exec "$@"
fi