#!/bin/bash

# Comprehensive Valkey GLIDE JNI vs UDS Benchmark Script
# This script runs all benchmark permutations for both standalone and cluster modes

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/benchmark_results"
CLUSTER_MANAGER="${SCRIPT_DIR}/../utils/cluster_manager.py"
GLIDE_HOME="${SCRIPT_DIR}/.."

# Test configurations
DATA_SIZES=(100 4000)
CONCURRENT_TASKS=(1 10)
CLIENT_TYPES=("glide")  # Our JNI implementation
CLIENT_COUNTS=(1)
CLUSTER_MODES=("standalone" "cluster")

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')] $1${NC}"
}

error() {
    echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $1${NC}" >&2
}

warn() {
    echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')] WARNING: $1${NC}"
}

info() {
    echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')] INFO: $1${NC}"
}

# Create results directory
mkdir -p "${RESULTS_DIR}"

# Initialize results file
RESULTS_FILE="${RESULTS_DIR}/comprehensive_benchmark_results_$(date +%Y%m%d_%H%M%S).json"
echo "[]" > "${RESULTS_FILE}"

log "Starting comprehensive benchmarks..."
log "Results will be saved to: ${RESULTS_FILE}"

# Function to start cluster
start_cluster() {
    local mode=$1
    local ports=$2
    
    log "Starting ${mode} cluster on ports: ${ports}"
    
    if [ "${mode}" = "standalone" ]; then
        # For standalone, we need 2 ports (1 shard with 1 replica)
        local port_array=(${ports})
        local port1=${port_array[0]}
        local port2=$((port1 + 1))
        cd "${GLIDE_HOME}" && python3 utils/cluster_manager.py start --ports ${port1} ${port2} --folder-path "${SCRIPT_DIR}/test_clusters"
    else
        cd "${GLIDE_HOME}" && python3 utils/cluster_manager.py start --cluster-mode --ports ${ports} --folder-path "${SCRIPT_DIR}/test_clusters"
    fi
    
    # Wait for cluster to be ready
    sleep 5
}

# Function to stop cluster
stop_cluster() {
    log "Stopping cluster..."
    cd "${GLIDE_HOME}" && python3 utils/cluster_manager.py stop --folder-path "${SCRIPT_DIR}/test_clusters" --prefix cluster 2>/dev/null || true
    
    # Kill any remaining processes
    pkill -f valkey-server 2>/dev/null || true
    
    # Wait for cleanup
    sleep 3
}

# Function to run benchmark
run_benchmark() {
    local client=$1
    local client_count=$2
    local concurrent_tasks=$3
    local data_size=$4
    local cluster_mode=$5
    local host=$6
    local port=$7
    
    local cluster_flag=""
    if [ "${cluster_mode}" = "cluster" ]; then
        cluster_flag="--clusterModeEnabled"
    fi
    
    local result_file="${RESULTS_DIR}/temp_${client}_${cluster_mode}_${data_size}_${concurrent_tasks}_${client_count}.json"
    
    info "Running benchmark: ${client} | ${cluster_mode} | data=${data_size} | concurrency=${concurrent_tasks} | clients=${client_count}"
    
    cd "${SCRIPT_DIR}" && timeout 300 ./gradlew :benchmarks:run --args="--clients ${client} --host ${host} --port ${port} --dataSize ${data_size} --concurrentTasks ${concurrent_tasks} --clientCount ${client_count} ${cluster_flag} --resultsFile ${result_file}" --no-daemon
    
    # Append results to main file
    if [ -f "${result_file}" ]; then
        # Read existing results
        local existing_results=$(cat "${RESULTS_FILE}")
        local new_result=$(cat "${result_file}")
        
        # Merge results
        echo "${existing_results}" | jq ". + [${new_result}]" > "${RESULTS_FILE}"
        
        # Clean up temp file
        rm "${result_file}"
        
        log "✅ Benchmark completed successfully"
    else
        error "Benchmark failed - no results file generated"
        return 1
    fi
}

# Function to get available ports
get_ports() {
    local count=$1
    local ports=()
    local start_port=30000
    
    for i in $(seq 1 $count); do
        while netstat -ln | grep -q ":${start_port} "; do
            start_port=$((start_port + 1))
        done
        ports+=($start_port)
        start_port=$((start_port + 1))
    done
    
    echo "${ports[@]}"
}

# Main benchmark execution
main() {
    log "=== COMPREHENSIVE VALKEY GLIDE JNI BENCHMARKS ==="
    log "Testing configurations:"
    log "  - Data sizes: ${DATA_SIZES[*]}"
    log "  - Concurrent tasks: ${CONCURRENT_TASKS[*]}"
    log "  - Client counts: ${CLIENT_COUNTS[*]}"
    log "  - Cluster modes: ${CLUSTER_MODES[*]}"
    log ""
    
    local total_tests=0
    local completed_tests=0
    
    # Calculate total tests
    for mode in "${CLUSTER_MODES[@]}"; do
        for client in "${CLIENT_TYPES[@]}"; do
            for client_count in "${CLIENT_COUNTS[@]}"; do
                for data_size in "${DATA_SIZES[@]}"; do
                    for concurrent_tasks in "${CONCURRENT_TASKS[@]}"; do
                        total_tests=$((total_tests + 1))
                    done
                done
            done
        done
    done
    
    log "Total tests to run: ${total_tests}"
    log ""
    
    # Run benchmarks for each configuration
    for mode in "${CLUSTER_MODES[@]}"; do
        log "=== Testing ${mode} mode ==="
        
        for client in "${CLIENT_TYPES[@]}"; do
            for client_count in "${CLIENT_COUNTS[@]}"; do
                for data_size in "${DATA_SIZES[@]}"; do
                    for concurrent_tasks in "${CONCURRENT_TASKS[@]}"; do
                        completed_tests=$((completed_tests + 1))
                        
                        log "Progress: ${completed_tests}/${total_tests}"
                        
                        # Determine port requirements
                        local node_count=2  # standalone needs 2 ports (1 shard + 1 replica)
                        if [ "${mode}" = "cluster" ]; then
                            node_count=6  # 3 shards with 1 replica each
                        fi
                        
                        # Get available ports
                        local ports=($(get_ports $node_count))
                        local primary_port=${ports[0]}
                        
                        # Stop any existing cluster
                        stop_cluster
                        
                        # Start cluster
                        if start_cluster "${mode}" "${ports[*]}"; then
                            # Run benchmark
                            if run_benchmark "${client}" "${client_count}" "${concurrent_tasks}" "${data_size}" "${mode}" "127.0.0.1" "${primary_port}"; then
                                log "✅ Test ${completed_tests}/${total_tests} completed"
                            else
                                error "❌ Test ${completed_tests}/${total_tests} failed"
                            fi
                        else
                            error "❌ Failed to start ${mode} cluster"
                        fi
                        
                        # Stop cluster after each test
                        stop_cluster
                        
                        # Short break between tests
                        sleep 2
                    done
                done
            done
        done
    done
    
    log "=== BENCHMARK SUMMARY ==="
    log "All benchmarks completed!"
    log "Results saved to: ${RESULTS_FILE}"
    log ""
    log "To view results:"
    log "  cat ${RESULTS_FILE} | jq ."
    log ""
    log "To analyze performance:"
    log "  jq '[.[] | {client: .client, cluster_mode: .is_cluster, data_size: .data_size, concurrent_tasks: .num_of_tasks, tps: .tps}] | sort_by(.tps) | reverse' ${RESULTS_FILE}"
}

# Cleanup function
cleanup() {
    log "Cleaning up..."
    stop_cluster
    rm -rf "${SCRIPT_DIR}/test_clusters" 2>/dev/null || true
}

# Set up signal handlers
trap cleanup EXIT INT TERM

# Run main function
main "$@"