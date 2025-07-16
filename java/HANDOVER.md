# Java JNI Implementation Handover - Missing Features Restoration

## Current Status: ✅ Core Architecture Complete, ❌ Missing Critical Features

**Date**: 2025-07-16  
**Priority**: CRITICAL - Integration tests failing due to missing features  
**Task**: Restore missing functionality from old UDS implementation  

## Executive Summary

The Java JNI implementation has **excellent core architecture** with 1.8-2.9x performance improvements, but is **missing critical features** from the original UDS implementation that are required for integration tests to pass.

## ✅ What's Working (Core Architecture)

### 1. Runtime Lifecycle Management (`src/runtime.rs`)
- **Fixed**: Reference-counted shutdown prevents premature runtime shutdown
- **Architecture**: Per-client JniRuntime instances with proper resource isolation
- **Performance**: Validated across all benchmark scenarios

### 2. Callback System (`src/callback.rs` + `src/async_bridge.rs`)
- **Working**: Request/response correlation with callback IDs
- **Architecture**: Non-blocking async execution without deadlocks
- **Integration**: Sync channels for callback completion tracking

### 3. Basic Command Execution (`src/client.rs`)
- **Working**: GET, SET, PING and basic commands
- **Performance**: 1.8-2.9x improvement over UDS implementation
- **Stability**: All basic benchmark tests passing (8/8 configurations)

## ❌ Critical Missing Features (Integration Test Blockers)

### 1. **Script Management System** - HIGH PRIORITY
**Integration Tests Failing**: `invokeScript()`, `scriptShow()`, `scriptFlush()`, `scriptKill()`

**Missing Implementation**:
```rust
// In src/client.rs - Need to add these JNI exports
#[jni_export]
fn storeScript(script_code: &[u8]) -> JniResult<String> // Returns SHA1 hash
#[jni_export]  
fn invokeScript(script_hash: String, keys: Vec<String>, args: Vec<Value>) -> JniResult<Value>
#[jni_export]
fn dropScript(script_hash: String) -> JniResult<()>
#[jni_export]
fn scriptShow(script_hash: String) -> JniResult<String>
```

**Reference Implementation**: `archive/java-old/client/src/main/java/glide/ffi/resolvers/ScriptResolver.java`

**glide-core Integration**: Uses `glide_core::scripts_container` for script storage

### 2. **Function Commands** - HIGH PRIORITY  
**Integration Tests Failing**: `fcall()`, `fcallReadOnly()`, `functionList()`, `functionStats()`, `functionLoad()`, `functionDelete()`, `functionFlush()`

**Missing Implementation**:
```rust
// In src/client.rs - Need to add these JNI exports
#[jni_export]
fn fcall(function_name: String, keys: Vec<String>, args: Vec<Value>) -> JniResult<Value>
#[jni_export]
fn fcallReadOnly(function_name: String, keys: Vec<String>, args: Vec<Value>) -> JniResult<Value>
#[jni_export]
fn functionList(library_name: Option<String>) -> JniResult<Vec<Value>>
#[jni_export]
fn functionStats() -> JniResult<Value>
#[jni_export]
fn functionLoad(library_code: String) -> JniResult<()>
#[jni_export]
fn functionDelete(library_name: String) -> JniResult<()>
#[jni_export]
fn functionFlush() -> JniResult<()>
```

### 3. **Cluster Scan Operations** - HIGH PRIORITY
**Integration Tests Failing**: `scan()`, `scanBinary()`, `ClusterScanCursor` operations

**Missing Implementation**:
```rust
// In src/client.rs - Need to add these JNI exports
#[jni_export]
fn submitClusterScan(cursor: Option<String>, match_pattern: Option<String>, count: Option<i64>) -> JniResult<u32> // Returns callback ID
#[jni_export]
fn createClusterScanCursor(cursor_data: String) -> JniResult<u64> // Returns cursor handle
#[jni_export]
fn releaseClusterScanCursor(cursor_handle: u64) -> JniResult<()>
```

**Reference Implementation**: `archive/java-old/client/src/main/java/glide/ffi/resolvers/ClusterScanCursorResolver.java`

**glide-core Integration**: Uses `glide_core::cluster_scan_container` for cursor management

### 4. **Data Structure Scan Commands** - HIGH PRIORITY
**Integration Tests Failing**: `hscan()`, `sscan()`, `zscan()` and binary versions

**Missing Implementation**:
```rust
// In src/client.rs - Need to add these JNI exports
#[jni_export]
fn hscan(key: String, cursor: String, match_pattern: Option<String>, count: Option<i64>) -> JniResult<Value>
#[jni_export]
fn sscan(key: String, cursor: String, match_pattern: Option<String>, count: Option<i64>) -> JniResult<Value>
#[jni_export]
fn zscan(key: String, cursor: String, match_pattern: Option<String>, count: Option<i64>) -> JniResult<Value>
```

### 5. **OpenTelemetry Integration** - MEDIUM PRIORITY
**Integration Tests Failing**: `OpenTelemetryTests.java` - span tracing, metrics collection

**Missing Implementation**:
```rust
// In src/client.rs - Need to add these JNI exports
#[jni_export]
fn initOpenTelemetry(config: OpenTelemetryConfig) -> JniResult<()>
#[jni_export]
fn createLeakedOtelSpan(name: String) -> JniResult<u64> // Returns span handle
#[jni_export]
fn dropOtelSpan(span_handle: u64) -> JniResult<()>
```

**Reference Implementation**: `archive/java-old/client/src/main/java/glide/ffi/resolvers/OpenTelemetryResolver.java`

**glide-core Integration**: Uses `glide_core::GlideOpenTelemetry` for traces and metrics

## Integration Test Status

### Current Test Results
```bash
# Running integration tests
./gradlew :integTest:test

# Expected failures due to missing features:
# - All script and function command tests
# - All cluster scan tests  
# - All data structure scan tests
# - OpenTelemetry integration tests
```

### Test Structure
- **Location**: `/home/ubuntu/valkey-glide/java/integTest/src/test/java/glide/`
- **Key files**: `SharedCommandTests.java`, `standalone/CommandTests.java`, `cluster/CommandTests.java`
- **Test runner**: JUnit 5 with custom `TestUtilities.java`

### Test Dependencies
- **Server setup**: Uses `TestConfiguration.java` for server management
- **Client creation**: Uses `TestUtilities.createClient()` with JNI client factory
- **Assertions**: Custom assertions for command responses

## Implementation Roadmap

### Phase 1: Script Management (Week 1)
1. **Add script storage**: Implement `storeScript()` with SHA1 hash generation
2. **Add script execution**: Implement `invokeScript()` with keys and args
3. **Add script management**: Implement `scriptShow()`, `scriptFlush()`, `scriptKill()`
4. **Test integration**: Verify script-related integration tests pass

### Phase 2: Function Commands (Week 2)
1. **Add function calls**: Implement `fcall()` and `fcallReadOnly()`
2. **Add function management**: Implement `functionList()`, `functionStats()`, `functionLoad()`
3. **Add function cleanup**: Implement `functionDelete()`, `functionFlush()`
4. **Test integration**: Verify function-related integration tests pass

### Phase 3: Scan Operations (Week 3)
1. **Add cluster scan**: Implement `ClusterScanCursor` and `submitClusterScan()`
2. **Add data structure scans**: Implement `hscan()`, `sscan()`, `zscan()`
3. **Add binary variants**: Implement binary versions of all scan commands
4. **Test integration**: Verify scan-related integration tests pass

### Phase 4: OpenTelemetry (Week 4)
1. **Add telemetry init**: Implement `initOpenTelemetry()` configuration
2. **Add span management**: Implement `createLeakedOtelSpan()`, `dropOtelSpan()`
3. **Add metrics**: Integrate with glide-core metrics collection
4. **Test integration**: Verify OpenTelemetry integration tests pass

## Key Implementation Notes

### 1. Callback Integration
All new features must integrate with the existing callback system:
```rust
// Example pattern for new commands
pub fn new_command_async(client: &JniClient, args: Vec<Value>) -> JniResult<u32> {
    let callback_id = client.next_callback_id.fetch_add(1, Ordering::SeqCst);
    let (tx, rx) = oneshot::channel();
    
    client.callback_registry.lock().unwrap().insert(callback_id, tx);
    
    client.runtime.spawn(async move {
        let result = client.core_client.new_command(args).await;
        tx.send(result).ok();
    });
    
    Ok(callback_id)
}
```

### 2. Per-Client Architecture
All features must work with the per-client architecture:
- Each client has its own script storage
- Each client has its own cursor management
- Each client has its own OpenTelemetry spans

### 3. Error Handling
Extend existing error handling for new features:
```rust
// In src/error.rs - Add new error types
#[derive(Debug)]
pub enum JniError {
    // ... existing errors
    ScriptNotFound(String),
    InvalidCursor(String),
    OpenTelemetryError(String),
}
```

## Success Criteria

### Integration Test Goals
```bash
# After all missing features are implemented:
./gradlew :integTest:test
# Should show: BUILD SUCCESSFUL with 0 failures
```

### Performance Maintenance
- **Maintain 1.8-2.9x improvement** over UDS implementation
- **No regression** in benchmark performance
- **Memory usage** should remain stable

### Feature Completeness
- **All script commands** working: `invokeScript()`, `scriptShow()`, etc.
- **All function commands** working: `fcall()`, `functionList()`, etc.
- **All scan operations** working: cluster scan, hscan, sscan, zscan
- **OpenTelemetry integration** working: spans, metrics, tracing

## Reference Files

### Old UDS Implementation
- **Scripts**: `archive/java-old/client/src/main/java/glide/ffi/resolvers/ScriptResolver.java`
- **Cluster Scan**: `archive/java-old/client/src/main/java/glide/ffi/resolvers/ClusterScanCursorResolver.java`
- **OpenTelemetry**: `archive/java-old/client/src/main/java/glide/ffi/resolvers/OpenTelemetryResolver.java`
- **JNI Bindings**: `archive/java-old/src/lib.rs`

### Current Implementation
- **Core Client**: `src/client.rs` (where new features need to be added)
- **Callback System**: `src/callback.rs` and `src/async_bridge.rs`
- **Runtime**: `src/runtime.rs`
- **Error Handling**: `src/error.rs`

## Next Steps

1. **Start with Script Management** - highest integration test impact
2. **Use existing callback architecture** - don't change the working foundation
3. **Reference old implementation** - adapt patterns to new per-client model
4. **Test incrementally** - verify each feature group before moving to next
5. **Maintain performance** - ensure no regressions in benchmark results

The core architecture is solid and performant. The task is to **restore missing features** from the old implementation, not to add new functionality. Focus on making integration tests pass while maintaining the excellent performance characteristics already achieved.