#!/bin/bash

# GET Benchmark Runner for Valkey GLIDE
# This script runs the GET benchmark with configurable parameters

set -e

# Default values
THREADS=4
ITERATIONS=1000
SYNC_MODE=false
DIRECT_MODE=false
REDIS_HOST="127.0.0.1"
REDIS_PORT=6379

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -t|--threads)
            THREADS="$2"
            shift 2
            ;;
        -i|--iterations)
            ITERATIONS="$2"
            shift 2
            ;;
        -s|--sync)
            SYNC_MODE=true
            shift
            ;;
        -a|--async)
            SYNC_MODE=false
            shift
            ;;
        -d|--direct)
            DIRECT_MODE=true
            shift
            ;;
        -p|--protobuf)
            DIRECT_MODE=false
            shift
            ;;
        --host)
            REDIS_HOST="$2"
            shift 2
            ;;
        --port)
            REDIS_PORT="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -t, --threads NUM     Number of threads (default: 4)"
            echo "  -i, --iterations NUM  Number of iterations per thread (default: 1000)"
            echo "  -s, --sync           Run in synchronous mode"
            echo "  -a, --async          Run in asynchronous mode (default)"
            echo "  -d, --direct         Use GLIDE RequestType enum (optimized)"
            echo "  -p, --protobuf       Use manual command construction (default)"
            echo "  --host HOST          Redis host (default: 127.0.0.1)"
            echo "  --port PORT          Redis port (default: 6379)"
            echo "  -h, --help           Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0 --threads 8 --iterations 5000 --sync --direct"
            echo "  $0 -t 2 -i 100 --async --protobuf"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use -h or --help for usage information"
            exit 1
            ;;
    esac
done

# Check if Redis is running
echo "Checking Redis connection at $REDIS_HOST:$REDIS_PORT..."
if ! timeout 5 bash -c "</dev/tcp/$REDIS_HOST/$REDIS_PORT" 2>/dev/null; then
    echo "Error: Cannot connect to Redis at $REDIS_HOST:$REDIS_PORT"
    echo "Please ensure Redis/Valkey is running on the specified host and port"
    exit 1
fi

echo "Redis connection successful!"
echo ""
echo "Running GET benchmark with configuration:"
echo "  Threads: $THREADS"
echo "  Iterations: $ITERATIONS"
echo "  Mode: $([ "$SYNC_MODE" = "true" ] && echo "Synchronous" || echo "Asynchronous")"
echo "  Command: $([ "$DIRECT_MODE" = "true" ] && echo "RequestType enum" || echo "Manual construction")"
echo "  Redis: $REDIS_HOST:$REDIS_PORT"
echo ""

# Set environment variables for the benchmark
export BENCH_THREADS=$THREADS
export BENCH_ITERATIONS=$ITERATIONS
export BENCH_SYNC_MODE=$SYNC_MODE
export BENCH_DIRECT_MODE=$DIRECT_MODE

# Change to the glide-core directory
cd "$(dirname "$0")"

# Run the benchmark
echo "Starting benchmark..."
cargo bench --bench get_benchmark

echo ""
echo "Benchmark completed! Results are available in target/criterion/get_commands/"
