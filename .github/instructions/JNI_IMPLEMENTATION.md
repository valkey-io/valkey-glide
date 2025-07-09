# JNI Implementation with jni-rs

## Overview

This document provides a detailed plan for implementing Java-Rust interoperability in Valkey GLIDE using the jni-rs library. This approach aims to replace the current UDS-based implementation with a more direct, higher-performance JNI-based solution. We will create a separate implementation in new directories to avoid modifying the existing client code.

## Project Structure

We will create the following directory structure for our implementation:

```
valkey-glide/
├── java-jni/                   # New directory for JNI-based Java client
│   ├── src/                    # Java source files
│   ├── build.gradle            # Gradle build configuration
│   └── native/                 # JNI native code (C/C++ headers)
├── rust-jni/                   # New directory for Rust JNI implementation
│   ├── Cargo.toml              # Rust dependencies including jni-rs
│   └── src/                    # Rust source files for JNI implementation
└── benchmark-jni/              # New directory for benchmarking
    ├── src/                    # Benchmark source code
    └── results/                # Benchmark results
```

## Research Summary

### jni-rs Library
- Rust crate providing safe and complete JNI bindings ([jni-rs GitHub](https://github.com/jni-rs/jni-rs))
- Provides Rust abstractions for JNI to reduce memory safety risks
- Supports implementing native Java methods in Rust and calling Java code from Rust
- Compatible with Java 11+ (our target version)

### Design Pattern: Metadata Struct as Direct ByteBuffer (Zero-Copy)

**Core approach:**
- Define a C-style struct in Rust for all metadata fields using `#[repr(C)]` for predictable layout
- In Java, allocate a Direct ByteBuffer, write the struct fields in native order
- Pass the metadata buffer to JNI alongside the command buffer (also a Direct ByteBuffer)
- Rust reads both buffers with zero-copy using `get_direct_buffer_address`

**Advantages:**
- Keeps the JNI signature stable as metadata evolves (future-proof)
- Enables zero-copy for both metadata and payload (max performance)
- Scalable and maintainable for complex client requirements
- Common pattern in high-performance, cross-language systems

**Requirements:**
- Java and Rust must agree on struct layout and byte order
- Always use `ByteOrder.nativeOrder()` in Java and `#[repr(C)]` in Rust
- Validate buffer sizes and field alignment on both sides
- Document the struct layout and update both sides together

## Implementation Todo List

### Phase 1: Setup New Project Directories

- [ ] Create `java-jni` directory with basic Gradle project structure
- [ ] Create `rust-jni` directory with Cargo.toml and jni-rs dependency
- [ ] Create `benchmark-jni` directory for benchmarking tools
- [ ] Setup basic build scripts for the new directories

### Phase 2: Setup and Basic Integration

- [ ] Add jni-rs dependency to the Rust project
- [ ] Define basic Java native method signatures
- [ ] Create JNI header files using `javac -h`
- [ ] Implement simple proof-of-concept method to verify JNI works
- [ ] Set up proper Java native library loading mechanism

### Phase 3: Core Data Structures

- [ ] Define the command metadata struct in Rust with `#[repr(C)]`
- [ ] Create corresponding ByteBuffer handling in Java
- [ ] Implement serialization/deserialization of basic data types
- [ ] Test data consistency between Java and Rust

### Phase 4: Command Passing Implementation

- [ ] Implement command execution via JNI
- [ ] Add error handling and exception propagation
- [ ] Create the connection management layer
- [ ] Implement asynchronous result handling

### Phase 5: Memory Management

- [ ] Implement proper resource cleanup on Java and Rust sides
- [ ] Create safeguards against memory leaks
- [ ] Set up proper thread synchronization
- [ ] Test with memory profiling tools

### Phase 6: Testing and Benchmarking

- [ ] Create unit tests for all components
- [ ] Implement integration tests for end-to-end functionality
- [ ] Set up benchmark suite to compare with UDS implementation
- [ ] Measure and document performance characteristics

## Design Decisions and Questions

### Struct Layout Versioning
- Include a version field at the start of the metadata struct
- Version checking in Rust code to ensure compatibility

### Optional/Variable-length Metadata
- Fixed-size struct with flags indicating which fields are active
- Separate buffer for variable-length data with offsets in the metadata

### Error Mapping
- Define error code constants shared between Java and Rust
- Map Rust Result errors to appropriate Java exceptions
- Include detailed error information in exception messages

## Next Steps

1. Create new directory structure and basic project files
2. Set up the jni-rs dependency and create a minimal proof-of-concept
3. Define the metadata struct layout and document it thoroughly
4. Implement basic command execution with error handling
5. Create test harness and initial benchmarks

## Required Dependencies

### Java
- JDK 11+
- Gradle

### Rust
- Rust 1.88
- jni-rs crate
- Any additional crates needed from the core Valkey GLIDE implementation

### Build Tools
- cmake
- gcc/clang
- pkg-config
- protobuf compiler (if needed)

## Benchmark Criteria

- Latency (p50, p90, p99) compared to UDS implementation
- Throughput under various concurrency levels
- Memory overhead of JNI vs UDS
- CPU utilization during peak load
- Connection establishment time
