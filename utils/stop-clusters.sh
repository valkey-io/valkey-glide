#!/bin/bash
# Stop all valkey-server processes and clean up cluster directories

set -e

echo "Stopping all valkey-server processes..."

# Kill all valkey-server processes
pkill -f valkey-server || true

# Wait for processes to stop
sleep 2

# Force kill any remaining processes
pkill -9 -f valkey-server || true

echo "Cleaning up cluster directories..."

# Remove cluster directories
rm -rf clusters/cluster-*

echo "All clusters stopped and cleaned up"
