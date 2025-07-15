#!/bin/bash
# Benchmark script for comparing GLIDE UDS vs JNI implementations

# Configure benchmark parameters
DATA_SIZE=100
CONCURRENT_TASKS=100
CLIENT_COUNT=1
OUTPUT_DIR="/tmp"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Create output directory if it doesn't exist
mkdir -p $OUTPUT_DIR

# Define output files
UDS_RESULTS="$OUTPUT_DIR/glide_uds_$TIMESTAMP.json"
JNI_RESULTS="$OUTPUT_DIR/glide_jni_$TIMESTAMP.json"

echo "Running UDS benchmark..."
./gradlew :benchmarks:run --args="--clients glide --dataSize $DATA_SIZE --concurrentTasks $CONCURRENT_TASKS --clientCount $CLIENT_COUNT --resultsFile $UDS_RESULTS"

echo "Running JNI benchmark..."
./gradlew :benchmarks:run --args="--clients glide-jni --dataSize $DATA_SIZE --concurrentTasks $CONCURRENT_TASKS --clientCount $CLIENT_COUNT --resultsFile $JNI_RESULTS"

echo "Benchmarks completed!"
echo "UDS results: $UDS_RESULTS"
echo "JNI results: $JNI_RESULTS"

# Print basic comparison
echo "====================================="
echo "Basic Performance Comparison:"
echo "====================================="

# Use Python to extract and compare metrics
python3 -c "
import json

# Load result files
with open('$UDS_RESULTS', 'r') as f:
    uds_data = json.load(f)

with open('$JNI_RESULTS', 'r') as f:
    jni_data = json.load(f)

# Get the first benchmark entry
uds_metrics = uds_data[0] if uds_data else None
jni_metrics = jni_data[0] if jni_data else None

if uds_metrics and jni_metrics:
    # Compare throughput
    uds_tps = uds_metrics.get('tps', 0)
    jni_tps = jni_metrics.get('tps', 0)
    tps_improvement = jni_tps / uds_tps if uds_tps > 0 else 0
    
    print(f'Throughput:')
    print(f'  UDS: {uds_tps:.2f} TPS')
    print(f'  JNI: {jni_tps:.2f} TPS')
    print(f'  Improvement: {tps_improvement:.2f}x')
    print()
    
    # Compare latency for GET operations
    if 'latencies' in uds_metrics and 'latencies' in jni_metrics:
        if 'GET_EXISTING' in uds_metrics['latencies'] and 'GET_EXISTING' in jni_metrics['latencies']:
            uds_avg = uds_metrics['latencies']['GET_EXISTING']['avg']
            jni_avg = jni_metrics['latencies']['GET_EXISTING']['avg']
            latency_improvement = uds_avg / jni_avg if jni_avg > 0 else 0
            
            print(f'Average GET Latency:')
            print(f'  UDS: {uds_avg:.3f} ms')
            print(f'  JNI: {jni_avg:.3f} ms')
            print(f'  Improvement: {latency_improvement:.2f}x')
            print()
            
            # P99 latency
            uds_p99 = uds_metrics['latencies']['GET_EXISTING']['p99']
            jni_p99 = jni_metrics['latencies']['GET_EXISTING']['p99']
            p99_improvement = uds_p99 / jni_p99 if jni_p99 > 0 else 0
            
            print(f'P99 GET Latency:')
            print(f'  UDS: {uds_p99:.3f} ms')
            print(f'  JNI: {jni_p99:.3f} ms')
            print(f'  Improvement: {p99_improvement:.2f}x')
else:
    print('Error: Could not extract metrics from benchmark results')
"