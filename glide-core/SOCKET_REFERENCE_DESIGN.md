# Socket Reference Counting Design

## Overview

This document describes the reference counting solution for socket management in Valkey GLIDE Rust core. The new system replaces the simple `HashSet`-based socket tracking with a robust reference counting mechanism that ensures sockets are automatically cleaned up when no longer in use.

## Problem Statement

### Current Implementation Issues

The current socket management implementation has several limitations:

1. **No Reference Counting**: Uses `INITIALIZED_SOCKETS: HashSet<String>` to track socket paths, but doesn't count how many clients are using each socket.

2. **Manual Cleanup**: Socket cleanup happens only when the listener task ends, not when clients disconnect.

3. **Resource Leaks**: Sockets may persist even when no clients are connected, leading to potential resource leaks.

4. **No Client Lifecycle Binding**: Socket lifecycle is not tied to client instances, making proper cleanup difficult.

## Solution Design

### Core Components

#### 1. SocketReference

```rust
#[derive(Debug, Clone)]
pub struct SocketReference {
    data: Arc<SocketData>,
}
```

- **Purpose**: A reference-counted handle to a socket
- **Behavior**: When all `SocketReference` instances for a socket are dropped, automatic cleanup occurs
- **Thread Safety**: Uses `Arc` for safe sharing across threads

#### 2. SocketData

```rust
#[derive(Debug)]
struct SocketData {
    path: String,
    runtime_handle: Handle,
    cleanup_initiated: bool,
}
```

- **Purpose**: Internal data structure that holds socket information
- **Cleanup**: Implements `Drop` trait for automatic cleanup when reference count reaches zero

#### 3. SocketManager

```rust
#[derive(Debug)]
struct SocketManager {
    sockets: HashMap<String, Weak<SocketData>>,
}
```

- **Purpose**: Global registry that tracks active sockets using weak references
- **Benefits**: Prevents circular references while allowing automatic cleanup detection
- **Thread Safety**: Protected by `Mutex` for concurrent access

### Key Features

#### Automatic Reference Counting

- Each client holds a `SocketReference`
- Reference count managed automatically by `Arc<SocketData>`
- When count reaches zero, cleanup occurs immediately

#### Weak Reference Tracking

- `SocketManager` uses `Weak<SocketData>` to avoid circular references
- Allows detection of when sockets are no longer referenced
- Expired weak references are automatically cleaned up

#### Thread-Safe Operations

- All operations use proper synchronization primitives
- Deadlock prevention using `try_lock()` in Drop handler
- Graceful error handling for lock failures including poisoned lock recovery
- Performance optimized using Tokio's blocking thread pool for deferred cleanup

## API Design

### Public Interface

#### Creating Socket References

```rust
// Atomic get or create operation for socket references
pub fn get_or_create(socket_path: String) -> SocketReference

// For new sockets (after successful binding) - legacy API
pub fn register_new_socket(socket_path: String) -> SocketReference

// For getting existing socket references
pub fn get_existing(socket_path: &str) -> Option<SocketReference>
```

#### Socket Listener Integration

```rust
// New API that returns SocketReference instead of just the path
pub fn start_socket_listener_with_reference<InitCallback>(
    init_callback: InitCallback,
    socket_path: Option<String>,
) where InitCallback: FnOnce(Result<SocketReference, String>) + Send + 'static

// Convenience API for new code
pub fn start_socket_listener_ref<InitCallback>(init_callback: InitCallback)
```

#### Utility Functions

```rust
// Check if a socket path is currently active
pub fn is_socket_active(socket_path: &str) -> bool

// Get count of active sockets (for monitoring)
pub fn active_socket_count() -> usize

// Force cleanup of all sockets (for testing)
pub fn cleanup_all_sockets()
```

### SocketReference Methods

```rust
impl SocketReference {
    // Get the socket file path
    pub fn path(&self) -> &str

    // Check if socket is still active
    pub fn is_active(&self) -> bool

    // Get current reference count (for debugging)
    pub fn reference_count(&self) -> usize
}
```

## Implementation Details

### Reference Counting Lifecycle

1. **Socket Creation**:
   - Socket listener binds to filesystem path
   - `SocketReference::register_new_socket()` creates initial reference
   - `SocketManager` stores weak reference for tracking

2. **Client Connection**:
   - Client calls `SocketReference::get_existing()` to get existing reference
   - If socket exists with active references, returns new `SocketReference`
   - Reference count increments automatically via `Arc::clone()`

3. **Client Disconnection**:
   - Client drops `SocketReference`
   - Reference count decrements automatically
   - When count reaches zero, `SocketData::drop()` is called

4. **Automatic Cleanup**:
   - `SocketData::drop()` removes socket file from filesystem
   - `SocketManager` removes expired weak reference
   - Resources are freed completely

### Thread Safety Guarantees

- **Lock Ordering**: Single global lock for `SocketManager` prevents deadlocks
- **Non-blocking Reads**: Most operations don't require long-held locks
- **Graceful Failures**: Lock failures are handled gracefully without panics
- **Atomic Operations**: Reference counting uses `Arc` for atomic operations

### Error Handling

- **Lock Failures**: Logged and handled gracefully
- **File System Errors**: Cleanup failures are logged but don't crash
- **Missing Sockets**: Handled gracefully with `Option` returns
- **Race Conditions**: Protected by proper synchronization

## Migration Strategy

### Backward Compatibility

The new system maintains backward compatibility:

- Existing `start_socket_listener()` function remains unchanged
- New `start_socket_listener_with_reference()` provides enhanced functionality
- Gradual migration path for existing code

### Client Integration

Clients can be updated to use `SocketReference`:

```rust
// Old approach: just socket path
let socket_path: String = get_socket_path();

// New approach: reference-counted socket
let socket_ref: SocketReference = get_socket_reference();
// Automatic cleanup when socket_ref is dropped
```

## Testing Strategy

### Unit Tests

- **Reference Counting**: Verify correct increment/decrement behavior
- **Automatic Cleanup**: Test cleanup when last reference is dropped
- **Concurrent Access**: Test thread safety with multiple threads
- **Edge Cases**: Test error conditions and race conditions

### Integration Tests

- **Multiple Clients**: Test with multiple clients sharing same socket
- **Client Lifecycle**: Test client creation/destruction patterns
- **Socket Reuse**: Test socket reuse across client sessions
- **Error Recovery**: Test behavior under error conditions

### Performance Tests

- **Memory Usage**: Verify no memory leaks in long-running scenarios
- **Lock Contention**: Measure performance under high concurrency
- **Cleanup Latency**: Measure time between reference drop and cleanup
- **Thread Pool Efficiency**: Verify Tokio's blocking pool usage reduces thread spawning overhead

## Performance Optimizations

### Cleanup Thread Pool

The implementation uses Tokio's blocking thread pool for deferred cleanup operations:

- **Problem**: Spawning new OS threads for each cleanup operation has significant overhead
- **Solution**: Reuse existing threads from Tokio's blocking pool when available
- **Fallback**: Only spawn new threads when not in a Tokio runtime context
- **Result**: Reduced thread creation overhead under high contention scenarios

### Lock Optimization

- **`try_lock()` in Drop**: Prevents blocking in destructors, avoiding deadlocks
- **Deferred Cleanup**: When lock is contended, cleanup is deferred to background task
- **`retain()` for Cleanup**: In-place filtering reduces memory allocations
- **`drain()` for Bulk Removal**: Efficient clearing of HashMap without extra clones

## Benefits

### Resource Management

- **Automatic Cleanup**: No manual socket cleanup required
- **Leak Prevention**: Impossible to leak sockets with proper reference handling
- **Predictable Lifecycle**: Socket lifetime tied directly to client usage

### Performance

- **Efficient Sharing**: Multiple clients can share same socket efficiently
- **Minimal Overhead**: Reference counting adds minimal performance overhead
- **Lock-free Reads**: Most operations don't require exclusive locks

### Reliability

- **Thread Safety**: All operations are thread-safe by design
- **Error Resilience**: Graceful handling of error conditions
- **Deterministic Behavior**: Predictable cleanup behavior

### Observability

- **Reference Counting**: Can monitor how many clients use each socket
- **Active Socket Tracking**: Real-time view of active sockets
- **Debug Support**: Rich debugging information available

## Future Enhancements

### Monitoring Integration

- **Metrics Collection**: Export socket usage metrics
- **Health Checks**: Monitor socket health and connectivity
- **Performance Tracking**: Track socket performance metrics

### Advanced Features

- **Socket Pooling**: Pool sockets for better resource utilization
- **Load Balancing**: Distribute clients across multiple sockets
- **Graceful Shutdown**: Coordinated shutdown with proper client notification

### Configuration

- **Cleanup Timeouts**: Configurable delays for socket cleanup
- **Reference Limits**: Configurable limits on references per socket
- **Resource Quotas**: Limits on total number of active sockets

## Conclusion

The reference counting solution provides a robust, thread-safe, and efficient approach to socket management. It addresses the limitations of the current implementation while maintaining backward compatibility and providing a clear migration path. The design ensures automatic resource cleanup, prevents leaks, and provides excellent observability into socket usage patterns.