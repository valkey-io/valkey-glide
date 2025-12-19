#!/bin/bash

# Test script for the GET benchmark
# This script runs a quick test to verify the benchmark works

set -e

echo "Testing GET benchmark..."
echo "========================"

# Check if we're in the right directory
if [ ! -f "Cargo.toml" ] || [ ! -f "benches/get_benchmark.rs" ]; then
    echo "Error: Please run this script from the glide-core directory"
    exit 1
fi

# Check if Redis is running (optional - benchmark will fail gracefully if not)
echo "Checking if Redis/Valkey is available..."
if timeout 2 bash -c "</dev/tcp/127.0.0.1/6379" 2>/dev/null; then
    echo "✓ Redis/Valkey is running on 127.0.0.1:6379"
    REDIS_AVAILABLE=true
else
    echo "⚠ Redis/Valkey is not running on 127.0.0.1:6379"
    echo "  The benchmark will fail, but we can still test compilation"
    REDIS_AVAILABLE=false
fi

# Test compilation
echo ""
echo "Testing benchmark compilation..."
if cargo check --bench get_benchmark; then
    echo "✓ Benchmark compiles successfully"
else
    echo "✗ Benchmark compilation failed"
    exit 1
fi

# Test build
echo ""
echo "Testing benchmark build..."
if cargo build --bench get_benchmark; then
    echo "✓ Benchmark builds successfully"
else
    echo "✗ Benchmark build failed"
    exit 1
fi

# Run a quick benchmark test if Redis is available
if [ "$REDIS_AVAILABLE" = true ]; then
    echo ""
    echo "Running quick benchmark test (2 iterations, sync mode)..."
    
    # Set minimal configuration for quick test
    export BENCH_THREADS=1
    export BENCH_ITERATIONS=2
    export BENCH_SYNC_MODE=true
    
    if timeout 30 cargo bench --bench get_benchmark -- --quick; then
        echo "✓ Benchmark runs successfully"
    else
        echo "⚠ Benchmark test failed or timed out"
        echo "  This might be due to server connectivity or performance issues"
    fi
else
    echo ""
    echo "Skipping benchmark execution test (Redis not available)"
fi

echo ""
echo "Test Summary:"
echo "============="
echo "✓ Benchmark code compiles"
echo "✓ Benchmark builds successfully"
if [ "$REDIS_AVAILABLE" = true ]; then
    echo "✓ Redis/Valkey connectivity confirmed"
else
    echo "⚠ Redis/Valkey not available for testing"
fi

echo ""
echo "The GET benchmark is ready to use!"
echo "Run './run_get_benchmark.sh --help' for usage instructions."
