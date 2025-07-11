# Implementation Plan for JNI-Based Valkey GLIDE Client

This plan outlines a step-by-step approach to replace the UDS (Unix Domain Socket) implementation with the JNI (Java Native Interface) implementation while preserving the existing API and test suite.

## Phase 1: Performance Baseline and Analysis

1. **Establish Performance Baseline**
   - Run benchmark tests on current UDS implementation
   - Measure throughput and latency metrics for key operations
   - Document performance characteristics under various loads
   - Create test scripts to replicate these measurements later

2. **Code Structure Analysis**
   - Identify components in the Java client that interact with UDS
   - Map the flow from Java API calls to UDS communication
   - Document the key interfaces and classes to preserve

## Phase 2: Core JNI Integration

3. **Expand Generic Command Execution in JNI**
   - Enhance `rust-jni/src/client.rs` with generic command execution
   - Add support for `send_command()` that accepts any Redis command
   - Implement command argument passing and result handling
   - This leverages glide-core's existing command execution system

4. **JNI Command Builder Implementation**
   - Implement a Java-side command builder matching `redis::Cmd`
   - Create JNI bindings for command creation and execution
   - Support both predefined and custom commands
   - Ensure proper memory management and resource cleanup

## Phase 3: Direct Replacement Implementation

5. **Command Manager Replacement**
   - Modify `glide.managers.CommandManager` to use JNI instead of UDS
   - Replace the submission logic to use JNI commands
   - Preserve exact method signatures and return types
   - Maintain error handling and exception mapping

6. **Connection Management Updates**
   - Update `glide.managers.ConnectionManager` to use JNI connections
   - Replace socket-based connection logic
   - Maintain connection lifecycle (init, reconnect, close)
   - Preserve cluster mode and TLS support

7. **Response Handling Adaptation**
   - Adapt `BaseResponseResolver` to work with JNI responses
   - Ensure consistent type conversion and error handling
   - Maintain binary/string response handling logic

## Phase 4: Cross-Platform Support

8. **Multi-Platform Native Libraries**
   - Create build pipeline for JNI libraries on all target platforms
   - Implement platform detection and library loading
   - Add support for Alpine Linux and Windows
   - Create platform-specific optimizations where needed

9. **JDK 8 Compatibility Layer**
   - Implement alternative to modern Java Cleaner API for JDK 8
   - Add fallback mechanisms for newer Java features
   - Ensure proper resource cleanup on all Java versions

## Phase 5: Testing and Validation

10. **Comprehensive Testing**
    - Run existing test suite against JNI implementation
    - Add JNI-specific test cases for edge conditions
    - Test resource management and cleanup
    - Validate consistent behavior between UDS and JNI

11. **Performance Validation**
    - Run benchmarks comparing UDS vs JNI implementation
    - Identify and address performance bottlenecks
    - Optimize memory management for large operations
    - Document performance improvements

## Phase 6: Finalization

12. **Remove UDS-Specific Code**
    - Remove socket listener components
    - Clean up UDS-specific protocol buffers
    - Remove now-redundant UDS communication layers

13. **Documentation and Release**
    - Update documentation to reflect JNI implementation
    - Document platform-specific considerations
    - Create migration notes if needed
    - Prepare release with performance metrics

## Implementation Approach Details

### Generic Command Execution

The core of this approach leverages glide-core's generic command execution mechanism:

1. For the Java side:
```java
// Java API remains unchanged
client.get(key);  // Behind the scenes uses JNI instead of UDS
```

2. For the JNI bridge:
```rust
// Generic command execution in JNI
pub extern "system" fn Java_glide_managers_CommandManager_executeCommand(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    command_name: jstring,
    args: jobjectArray
) -> jobject {
    // Convert Java arguments to Rust
    let cmd = cmd(command_name);
    for arg in args {
        cmd.arg(arg);
    }
    
    // Use glide-core's generic command execution
    let result = get_runtime().block_on(async {
        client.send_command(&cmd, None).await
    });
    
    // Convert result back to Java
    convert_result_to_java(env, result)
}
```

3. For error handling:
```rust
// Error mapping from Redis/Rust errors to Java exceptions
fn map_error_to_exception(env: &mut JNIEnv, error: &RedisError) {
    match error.kind() {
        redis::ErrorKind::IoError => throw_java_exception(env, "java.io.IOException", error.to_string()),
        redis::ErrorKind::AuthenticationFailed => throw_java_exception(env, "java.lang.SecurityException", error.to_string()),
        // Map other error types...
    }
}
```

This approach allows us to:
1. Minimize duplicated code
2. Support all Redis commands without individual implementation
3. Maintain consistent behavior with the UDS implementation
4. Leverage glide-core's existing command routing and execution