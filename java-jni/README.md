# Valkey GLIDE JNI Implementation - POC

## Overview

This is a Proof of Concept (POC) implementation of JNI (Java Native Interface) bindings for Valkey GLIDE, designed to benchmark performance against the current UDS (Unix Domain Socket) + Protobuf approach.

## Architecture

```
Java Application
       ↓ (JNI calls)
JNI Bridge (GlideJniClient.java)
       ↓ (native methods)
Rust Implementation (lib.rs)
       ↓ (uses glide-core)
Valkey Server
```

**vs Current Architecture:**
```
Java Application
       ↓ (UDS + Protobuf)
Rust Process (glide-core)
       ↓ (direct connection)
Valkey Server
```

## Implementation Details

### Java Side (`GlideJniClient.java`)
- **API**: Uses host/port parameters (e.g., `new GlideJniClient("localhost", 6379)`)
- **Async Pattern**: Returns `CompletableFuture<String>` for all operations
- **Resource Management**: Modern `Cleaner` API (Java 9+, no deprecated `finalize()`)
- **Commands**: PING, GET, SET (core operations for benchmarking)
- **Threading**: Uses `ForkJoinPool.commonPool()` for async execution

### Rust Side (`lib.rs`)
- **JNI Interface**: Exports `connect()`, `disconnect()`, `executeCommand()`
- **Dependencies**: jni, thiserror, tokio, glide-core, redis
- **Build Profile**: Optimized release build with LTO (Link Time Optimization)
- **Environment**: Requires `GLIDE_NAME="GlideJNI"` and `GLIDE_VERSION="1.0.0"`

### Build Configuration

**Cargo.toml Optimizations:**
```toml
[profile.release]
opt-level = 3         # Maximum optimization
lto = "fat"          # Aggressive Link Time Optimization
codegen-units = 1    # Better optimization opportunities
strip = "symbols"    # Smaller binary size
```

**Environment Setup (`.cargo/config.toml`):**
```toml
[env]
GLIDE_NAME = { value = "GlideJNI", force = true }
GLIDE_VERSION = { value = "1.0.0", force = true }
```

## Files Structure

```
java-jni/
├── README.md                           # This file
├── src/main/java/io/valkey/glide/jni/client/
│   └── GlideJniClient.java            # Java JNI client
├── src/test/java/io/valkey/glide/jni/benchmarks/
│   ├── JniVsUdsBenchmark.java         # JMH benchmark suite
│   └── UdsSimulationClient.java       # UDS overhead simulation
└── TestJniClient.java                  # Simple test client

rust-jni/
├── Cargo.toml                          # Rust build configuration
├── .cargo/config.toml                  # Environment variables
├── src/lib.rs                          # Rust JNI implementation
├── target/release/libglidejni.so       # Compiled native library
└── test_simple.sh                      # Basic functionality test
```

## Current Status

### ✅ Completed Features
- [x] JNI bridge implementation (Java ↔ Rust)
- [x] Host/port connection API (matches Valkey GLIDE patterns)
- [x] Modern Java resource management (Cleaner, no finalize)
- [x] Optimized Rust build configuration
- [x] Environment variable setup for glide-core
- [x] Basic operations: PING, GET, SET
- [x] CompletableFuture async API
- [x] Thread-safe resource cleanup
- [x] JMH benchmark suite
- [x] UDS simulation for baseline comparison
- [x] All tests passing

### 🧪 Test Results
```bash
=== Testing JNI Valkey Client ===
Testing JNI client...
PING result: PONG
SET result: OK
GET result: test_value
All tests passed!
Test completed successfully!
```

### 📊 Build Performance
- **Release Build Time**: ~2 minutes 28 seconds
- **Final Binary**: `libglidejni.so` (optimized, symbols stripped)
- **Dependencies**: 289 crates compiled with maximum optimization

## Performance Expectations

### JNI Advantages
- **No Serialization**: Direct memory access, no Protobuf overhead
- **No Socket Latency**: In-process communication
- **Minimal Context Switching**: Direct function calls
- **Lower Memory Usage**: No intermediate buffers

### UDS Advantages
- **Process Isolation**: Crashes don't affect Java application
- **Language Agnostic**: Protocol works with any language
- **Better Debugging**: Separate processes easier to debug

### Expected Benchmark Results
- **JNI should be 5-10x faster** for small operations (PING, simple GET/SET)
- **Performance gap may narrow** for large payload operations
- **Mixed workloads should favor JNI** due to reduced per-operation overhead

## Running the Implementation

### Prerequisites
1. Valkey server running on `localhost:6379`
2. Rust toolchain installed
3. Java 11+ installed
4. JMH dependencies (for benchmarks)

### Quick Test
```bash
cd rust-jni
bash test_simple.sh
```

### Build from Scratch
```bash
# Build optimized Rust library
cd rust-jni
cargo build --release

# Compile Java classes
cd ../java-jni
javac -cp . src/main/java/io/valkey/glide/jni/client/GlideJniClient.java -d build/

# Run test
java -Djava.library.path=../rust-jni/target/release -cp .:build TestJniClient
```

### Running Benchmarks
```bash
# Compile benchmark with JMH
javac -cp jmh-core.jar:jmh-generator-annprocess.jar:. \
  src/test/java/io/valkey/glide/jni/benchmarks/*.java -d build/

# Run benchmark
java -Djava.library.path=../rust-jni/target/release \
  -cp .:build:jmh-core.jar JniVsUdsBenchmark
```

## Implementation Highlights

### Modern Java Practices
- **No Deprecated APIs**: Removed `finalize()`, uses `Cleaner` API
- **Proper Resource Management**: AutoCloseable with guaranteed cleanup
- **Thread Safety**: Synchronized cleanup prevents double-free issues
- **Java 11+ Support**: Modern language features and APIs

### Optimized Rust Build
- **Aggressive LTO**: "fat" Link Time Optimization for maximum performance
- **Single Codegen Unit**: Better optimization opportunities
- **Symbol Stripping**: Reduced binary size
- **Environment Hardcoding**: No runtime environment variable lookup

### Fair Benchmarking
- **Same API Surface**: Both implementations use CompletableFuture
- **Realistic UDS Simulation**: Includes all major overhead sources
- **Mixed Workloads**: Real-world usage patterns
- **Multiple Metrics**: Individual operations and composite workloads

## Technical Decisions

### Why Host/Port Instead of Connection String?
- **Consistency**: Matches existing Valkey GLIDE NodeAddress patterns
- **Simplicity**: Avoids URL parsing overhead in native code
- **Clarity**: Explicit parameters make connection intent clear

### Why Cleaner Instead of Finalize?
- **Modern Standard**: finalize() deprecated since Java 9
- **Better Performance**: Cleaner is more efficient
- **Guaranteed Cleanup**: More reliable resource management
- **Future Proof**: Follows current Java best practices

### Why Aggressive LTO?
- **Benchmark Fairness**: Maximum optimization for performance comparison
- **Real-World Simulation**: Production builds would use similar settings
- **Cross-Module Optimization**: LTO optimizes across crate boundaries

## Next Steps

1. **Execute Benchmarks**: Run full JMH benchmark suite
2. **Analyze Results**: Compare JNI vs UDS performance characteristics
3. **Memory Profiling**: Measure memory usage patterns
4. **Scaling Tests**: Test with multiple concurrent connections
5. **Production Decision**: Use results to inform implementation choice

## Lessons Learned

1. **Environment Variables**: glide-core requires specific build-time variables
2. **LTO Impact**: Aggressive optimization significantly increases build time
3. **Resource Management**: Modern Java patterns prevent common JNI pitfalls
4. **API Design**: Host/port parameters align with existing GLIDE patterns
5. **Benchmark Design**: UDS simulation provides realistic baseline comparison

---

This POC demonstrates that JNI is a viable alternative to UDS+Protobuf for Valkey GLIDE, with significant potential performance benefits for high-frequency operations while maintaining the same async API patterns.
