#!/bin/bash
set -e

# Function to start valkey with custom configuration
start_valkey() {
    local config_file="/etc/valkey/valkey.conf"
    local port=${VALKEY_PORT:-6379}
    local tls=${VALKEY_TLS:-false}

    # Use cluster config if CLUSTER_MODE is set
    if [ "$CLUSTER_MODE" = "true" ]; then
        config_file="/etc/valkey/valkey-cluster.conf"
    fi

    # Copy config to writable location
    cp "$config_file" /tmp/valkey.conf

    # Update port in config
    sed -i "s/port 6379/port $port/" /tmp/valkey.conf

    # Configure cluster announce port if in cluster mode
    if [ "$CLUSTER_MODE" = "true" ]; then
        echo "cluster-announce-port $port" >> /tmp/valkey.conf
        echo "cluster-announce-bus-port $((port + 10000))" >> /tmp/valkey.conf
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