#!/bin/bash

# Simple test script to verify JNI client works with a single benchmark

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GLIDE_HOME="${SCRIPT_DIR}/.."

echo "Starting simple Valkey server..."
nohup valkey-server --port 30000 --protected-mode no --daemonize yes > /dev/null 2>&1

# Wait for server to start
sleep 3

echo "Testing JNI client connection..."
cd "${SCRIPT_DIR}"

# Run a simple benchmark
echo "Running benchmark..."
timeout 60 ./gradlew :benchmarks:run --args="--clients glide --host 127.0.0.1 --port 30000 --dataSize 100 --concurrentTasks 1 --clientCount 1 --resultsFile test_result.json --minimal" --no-daemon

echo "Benchmark completed. Results:"
if [ -f "test_result.json" ]; then
    cat test_result.json | jq .
else
    echo "No results file generated"
fi

echo "Stopping server..."
pkill -f "valkey-server.*30000" || true

echo "Test completed."