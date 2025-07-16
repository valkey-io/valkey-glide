#!/bin/bash

# Comprehensive benchmark script for new JNI implementation
# This script runs multiple configurations to get a complete performance picture

set -e

export GLIDE_NAME="glide-java-jni"
export GLIDE_VERSION="1.0.0"

echo "🚀 Running Comprehensive Benchmarks for New JNI Implementation"
echo "=============================================================="

# Create results directory
mkdir -p benchmark_results_new
cd /home/ubuntu/valkey-glide/java

# Configuration 1: Small data, low concurrency
echo "📊 Configuration 1: Small Data (100 bytes), Low Concurrency (1 task)"
./gradlew :benchmarks:run --args="--clients glide-jni --dataSize 100 --concurrentTasks 1 --clientCount 1 --resultsFile benchmark_results_new/config1_small_low.json" --no-daemon

# Configuration 2: Large data, low concurrency  
echo "📊 Configuration 2: Large Data (4000 bytes), Low Concurrency (1 task)"
./gradlew :benchmarks:run --args="--clients glide-jni --dataSize 4000 --concurrentTasks 1 --clientCount 1 --resultsFile benchmark_results_new/config2_large_low.json" --no-daemon

# Configuration 3: Small data, high concurrency
echo "📊 Configuration 3: Small Data (100 bytes), High Concurrency (10 tasks)"
./gradlew :benchmarks:run --args="--clients glide-jni --dataSize 100 --concurrentTasks 10 --clientCount 1 --resultsFile benchmark_results_new/config3_small_high.json" --no-daemon

# Configuration 4: Large data, high concurrency
echo "📊 Configuration 4: Large Data (4000 bytes), High Concurrency (10 tasks)"
./gradlew :benchmarks:run --args="--clients glide-jni --dataSize 4000 --concurrentTasks 10 --clientCount 1 --resultsFile benchmark_results_new/config4_large_high.json" --no-daemon

echo "✅ All benchmark configurations completed!"
echo "📋 Results saved in benchmark_results_new/ directory"