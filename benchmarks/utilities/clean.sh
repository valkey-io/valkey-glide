#!/usr/bin/env bash
# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

set -e

# Get the paths
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BENCH_DIR="$(dirname "$SCRIPT_DIR")"
MAIN_DIR="$(dirname "$BENCH_DIR")"

echo "Cleaning benchmark resources..."

# Clean utilities directory
echo "Cleaning utilities..."
cd "$SCRIPT_DIR"
rm -rf node_modules
rm -f package-lock.json
rm -f *.js

# Handle data directory with proper permissions
echo "Cleaning data directory (may require sudo)..."
if [ -d data ]; then
    # Try without sudo first
    rm -rf data/* 2>/dev/null || {
        echo "Permission issue detected, trying with sudo..."
        sudo rm -rf data/* || {
            echo "Warning: Could not clean data directory completely. Some files may remain."
        }
    }
fi
# Keep the data directory itself
mkdir -p data

# Clean benchmark results
echo "Cleaning benchmark results..."
rm -rf "$BENCH_DIR/results"/*
mkdir -p "$BENCH_DIR/results"

# Clean Node benchmark
echo "Cleaning Node benchmark..."
cd "$BENCH_DIR/node"
rm -rf node_modules
rm -f package-lock.json
rm -f *.js

# Clean main Node directory
echo "Cleaning Node project modules..."
cd "$MAIN_DIR/node"
rm -rf node_modules
rm -f package-lock.json
rm -rf build-ts

# Clean C# benchmark
echo "Cleaning C# benchmark..."
cd "$BENCH_DIR/csharp"
dotnet clean || true

# Clean Python benchmark (no need to clean the compiled files as they're contained in __pycache__)
echo "Cleaning Python benchmark..."
rm -rf "$BENCH_DIR/python/__pycache__"
rm -rf "$BENCH_DIR/python/venv"
find "$BENCH_DIR/python" -name "*.pyc" -delete

# Clean Rust benchmark
echo "Cleaning Rust benchmark..."
cd "$BENCH_DIR/rust"
cargo clean || true

# Clean any Docker containers used for benchmarking
echo "Cleaning Docker containers..."
docker rm -f valkey-standalone >/dev/null 2>&1 || true
docker compose -f "$SCRIPT_DIR/docker-compose.yml" down >/dev/null 2>&1 || true

echo "Cleanup complete!"
