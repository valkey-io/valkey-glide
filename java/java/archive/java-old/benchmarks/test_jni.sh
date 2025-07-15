#!/bin/bash

# Quick test script to verify JNI client functionality
set -e

echo "🔧 Building benchmark project with JNI support..."
cd /home/ubuntu/valkey-glide/java
./gradlew :benchmarks:compileJava --no-daemon --quiet

echo "✅ Build successful!"

echo "📋 Listing available benchmark clients:"
echo "  - jedis (sync)"
echo "  - lettuce (async)"
echo "  - glide (async UDS)"
echo "  - glide-jni (async direct JNI) ⚡"
echo ""

echo "📋 JNI Library Status:"
echo "  Location: /home/ubuntu/valkey-glide/rust-jni/target/release/libglidejni.so"
ls -lh /home/ubuntu/valkey-glide/rust-jni/target/release/libglidejni.so

echo ""
echo "🚀 Ready to run benchmarks!"
echo "   Example: ./gradlew :benchmarks:run --args='--clients glide-jni --minimal'"
echo ""
echo "🆚 To compare JNI vs UDS performance:"
echo "   ./gradlew :benchmarks:run --args='--clients glide,glide-jni --minimal'"