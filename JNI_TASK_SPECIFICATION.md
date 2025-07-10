# JNI Direct Integration Task Specification

## Current State vs Target State

### **Current Architecture (UDS-based)**
```
Java Glide Client → UDS Socket → Standalone Glide Process → Rust Core → Valkey Server
```
**Problems:**
- **High latency** due to inter-process communication
- **Serialization overhead** for all commands and responses
- **Additional complexity** of managing separate processes
- **Resource overhead** of maintaining UDS connections

### **Target Architecture (JNI-based)**
```
Java Glide Client → JNI → Rust Core (in-process) → Valkey Server
```
**Benefits:**
- **Direct in-process calls** - eliminate IPC overhead
- **Zero-copy memory sharing** between JVM and Rust
- **Shared connection pools** within the same process
- **Simplified deployment** - single JVM process

## Task Goals

### **Primary Objective**
Create a **minimal but real** JNI implementation that:
1. **Uses actual glide-core client** (not mock/placeholder)
2. **Connects to real Valkey server** for benchmarking
3. **Implements GET/SET operations** with real Redis protocol
4. **Achieves measurable performance improvements** over UDS approach

### **Performance Requirements**
- **Zero-copy operations** where possible
- **Optimal memory management** between JVM and Rust
- **Aligned runtimes** - JVM threads + Tokio async runtime
- **Direct pointer passing** for maximum efficiency
- **Correct payload marshaling** without unnecessary allocations

### **Quality Standards**
- **Every line must be production-quality** - no placeholders
- **Safety first** - proper memory management and error handling
- **Best practices** - follow established JNI and Rust patterns
- **Benchmarkable** - ready for real performance comparison

## Implementation Scope

### **What to Implement**
1. **Real glide-core client integration**
   - Actual connection to Valkey server
   - Real async/sync client management
   - Proper connection lifecycle

2. **GET/SET command implementation**
   - Direct glide-core command execution
   - Real Redis protocol handling
   - Actual response processing

3. **Optimal JNI layer**
   - Zero-copy byte array handling
   - Efficient pointer management
   - Runtime alignment (JVM ↔ Tokio)

4. **Memory management**
   - Safe Rust ↔ Java memory sharing
   - Proper resource cleanup
   - No memory leaks or corruption

### **What NOT to Implement**
- ❌ Placeholder FFI layer (not needed - direct glide-core usage)
- ❌ Mock storage systems (use real Valkey connection)
- ❌ Future-proofing for other commands (focus on GET/SET)
- ❌ Complex configuration (minimal viable setup)

## Success Criteria

### **Functional Requirements**
- ✅ Successfully connect to Valkey server
- ✅ Execute real GET/SET operations
- ✅ Handle errors and edge cases correctly
- ✅ Pass all basic functionality tests

### **Performance Requirements**
- ✅ **Lower latency** than UDS implementation
- ✅ **Higher throughput** in benchmark tests
- ✅ **Reduced memory overhead** per operation
- ✅ **Better CPU utilization** (no IPC overhead)

### **Quality Requirements**
- ✅ **Memory safe** - no leaks, corruption, or crashes
- ✅ **Thread safe** - proper concurrency handling
- ✅ **Error resilient** - graceful failure handling
- ✅ **Benchmarkable** - integrates with existing Java benchmarks

## Implementation Strategy

### **Phase 1: Core Integration**
1. Remove all placeholder/mock code
2. Integrate real glide-core client
3. Establish actual Valkey connection
4. Implement basic GET operation

### **Phase 2: Command Implementation**
1. Implement SET operation with real protocol
2. Add proper error handling and response processing
3. Optimize memory management and payload handling
4. Ensure thread safety and runtime alignment

### **Phase 3: Performance Optimization**
1. Implement zero-copy operations
2. Optimize JNI call overhead
3. Align JVM and Tokio runtime performance
4. Fine-tune memory management

### **Phase 4: Validation**
1. Run existing Java benchmarks
2. Compare performance with UDS implementation
3. Validate memory usage and latency improvements
4. Document performance gains

## Technical Requirements

### **Rust Dependencies**
- `glide-core` - actual client implementation
- `jni` - JNI bindings
- `tokio` - async runtime (aligned with glide-core)

### **Java Integration**
- Must work with existing Java benchmark framework
- Compatible with current Java client interfaces
- Drop-in replacement for UDS client in benchmarks

### **Performance Metrics**
- **Latency**: P50, P95, P99 response times
- **Throughput**: Operations per second
- **Memory**: Heap usage, allocation rate
- **CPU**: Utilization efficiency

## Deliverables

1. **Production JNI implementation** with real glide-core integration
2. **Benchmark-ready code** that works with existing Java tests
3. **Performance comparison** showing improvements over UDS
4. **Documentation** of implementation approach and results

---

**Bottom Line**: Build a real, minimal, high-performance JNI integration that proves direct Rust core usage significantly outperforms the current UDS approach.
