#!/bin/bash

# Test cluster mode specifically

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GLIDE_HOME="${SCRIPT_DIR}/.."

echo "Starting cluster..."
cd "${GLIDE_HOME}/utils"
python3 cluster_manager.py --host 127.0.0.1 start --cluster-mode -p 30001 30002 30003 30004 30005 30006 -n 3 -r 1

echo "Waiting for cluster to be ready..."
sleep 5

echo "Testing JNI client with cluster mode..."
cd "${SCRIPT_DIR}"

# Run a simple benchmark with cluster mode
echo "Running benchmark..."
timeout 60 ./gradlew :benchmarks:run --args="--clients glide --host 127.0.0.1 --port 30001 --dataSize 100 --concurrentTasks 1 --clientCount 1 --resultsFile cluster_test_result.json --minimal --clusterMode" --no-daemon

echo "Benchmark completed. Results:"
if [ -f "cluster_test_result.json" ]; then
    cat cluster_test_result.json | jq .
else
    echo "No results file generated"
fi

echo "Stopping cluster..."
cd "${GLIDE_HOME}/utils"
python3 cluster_manager.py --host 127.0.0.1 stop

echo "Test completed."