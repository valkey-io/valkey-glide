# Node.js Socket Reference Implementation Context

## Executive Summary

This document provides comprehensive context for implementing socket reference counting in the Node.js client of Valkey GLIDE, bringing it to feature parity with the Python implementation. The solution leverages the existing Rust core `SocketReference` implementation through NAPI bindings to provide automatic socket lifecycle management.

## Current State Analysis

### Node.js Client Socket Management

The Node.js client currently uses a basic socket management approach:

1. **Socket Creation**: Uses `StartSocketConnection()` which returns a raw socket path string
2. **No Reference Counting**: Multiple clients can share sockets but there's no coordination
3. **Manual Cleanup**: Socket cleanup happens only when explicitly called
4. **Potential Leaks**: Abnormal termination can leave socket files orphaned

#### Current Flow
```typescript
// Current implementation in BaseClient.ts
const socketPath = await StartSocketConnection();
const socket = await this.GetSocket(socketPath);
// No reference tracking - just raw path
```

### Python Implementation (Reference)

The Python client successfully implements socket reference counting:

1. **Uses `SocketReference`**: Wraps socket with reference counting
2. **Automatic Cleanup**: Socket files cleaned up when last reference drops
3. **Thread-Safe**: Arc-based reference counting in Rust core
4. **PyO3 Integration**: Clean exposure of Rust types to Python

```python
# Python implementation
socket_ref = start_socket_listener_with_reference()
# Automatic cleanup when socket_ref is garbage collected
```

### Rust Core Implementation

The Rust core provides a complete socket reference counting system:

```rust
pub struct SocketReference {
    data: Arc<SocketData>,
}

impl SocketReference {
    pub fn get_or_create(socket_path: String) -> Self
    pub fn path(&self) -> &str
    pub fn is_active(&self) -> bool
    pub fn reference_count(&self) -> usize
}
```

Key features:
- Arc-based reference counting
- Automatic cleanup via Drop trait
- Thread-safe operations
- Deadlock prevention with try_lock()
- Tokio blocking pool for deferred cleanup

## Technical Analysis

### NAPI-RS Integration

**Current Setup**:
- NAPI-RS v2.18.4 (in package.json)
- Uses `napi` and `napi-derive` crates
- Supports custom Rust structs as JavaScript objects
- Promise-based async operations

**Capabilities**:
- Can wrap Rust structs with `#[napi]` attribute
- Supports getters/methods exposure
- Handles async operations through promises
- Automatic TypeScript definition generation

**Limitations**:
- NAPI v2 References are not Send (thread limitations)
- Limited lifetime management compared to v3
- Requires careful memory management

### Implementation Approach

#### 1. NAPI SocketReference Wrapper

```rust
// In node/rust-client/src/lib.rs
#[napi]
pub struct SocketReference {
    inner: glide_core::socket_reference::SocketReference,
}

#[napi]
impl SocketReference {
    #[napi(getter)]
    pub fn path(&self) -> String {
        self.inner.path().to_string()
    }

    #[napi(getter)]
    pub fn is_active(&self) -> bool {
        self.inner.is_active()
    }

    #[napi(getter)]
    pub fn reference_count(&self) -> u32 {
        self.inner.reference_count() as u32
    }
}
```

#### 2. Socket Listener Function

```rust
#[napi(js_name = "StartSocketConnectionWithReference")]
pub fn start_socket_listener_with_reference(env: Env) -> Result<JsObject> {
    let (deferred, promise) = env.create_deferred()?;

    glide_core::start_socket_listener_ref(move |result| {
        match result {
            Ok(socket_ref) => {
                let js_socket_ref = SocketReference::from_core(socket_ref);
                deferred.resolve(move |_| Ok(js_socket_ref))
            }
            Err(error) => {
                deferred.reject(napi::Error::new(Status::Unknown, error))
            }
        }
    });

    Ok(promise)
}
```

#### 3. TypeScript Integration

```typescript
// In BaseClient.ts
import { SocketReference, StartSocketConnectionWithReference } from "./native";

class BaseClient {
    private socketRef?: SocketReference;

    static async createClientInternal<TConnection>(
        options: BaseClientConfiguration | undefined,
        createClientFn: (socket: net.Socket, options?: BaseClientConfiguration) => TConnection,
    ): Promise<TConnection> {
        // New implementation with reference counting
        const socketRef = await StartSocketConnectionWithReference();
        const socket = await this.GetSocket(socketRef.path);

        const client = await this.__createClientInternal<TConnection>(
            options,
            socket,
            createClientFn,
        );

        // Store reference for proper cleanup
        (client as any).socketRef = socketRef;
        return client;
    }

    public async close(): Promise<void> {
        // Existing close logic...
        // SocketReference cleanup happens automatically
        this.socketRef = undefined;
    }
}
```

## Memory Management

### JavaScript GC Integration

1. **Reference Holding**: JavaScript holds the NAPI `SocketReference` object
2. **GC Trigger**: When JS object is garbage collected, finalizer runs
3. **Drop Chain**: NAPI finalizer → Rust Drop → Arc decrement → Cleanup

### Preventing Memory Leaks

```rust
impl Drop for SocketReference {
    fn drop(&mut self) {
        // Inner CoreSocketReference Drop handles cleanup
        log_debug("SocketReference",
            format!("JavaScript reference dropped for {}", self.inner.path()));
    }
}
```

## Benefits

### Automatic Socket Cleanup
- No manual socket file removal needed
- Cleanup guaranteed even on abnormal termination
- Shared sockets properly managed

### Thread Safety
- Arc-based reference counting is thread-safe
- No race conditions in socket lifecycle
- Safe across Node.js worker threads

### Performance Improvements
- Eliminates HashSet lookups
- Reduces lock contention
- More predictable cleanup timing

### Consistency
- Same behavior as Python client
- Unified socket management across languages
- Shared Rust core implementation

## Migration Strategy

### Phase 1: Add New API (Backward Compatible)
1. Implement NAPI `SocketReference` wrapper
2. Add `StartSocketConnectionWithReference` function
3. Keep existing `StartSocketConnection` for compatibility
4. Add feature flag for gradual rollout

### Phase 2: Update TypeScript Layer
1. Add TypeScript definitions for `SocketReference`
2. Update `BaseClient` to use new API when available
3. Add tests for reference counting behavior
4. Document migration path for users

### Phase 3: Deprecation
1. Mark old API as deprecated
2. Update all examples to use new API
3. Remove old implementation in major version

## Technical Challenges

### NAPI Version Limitations
- **Challenge**: NAPI v2 has limited lifetime management
- **Solution**: Careful wrapper design with explicit cleanup
- **Future**: Consider NAPI v3 migration for better support

### Thread Safety
- **Challenge**: Node.js event loop and worker threads
- **Solution**: Arc-based sharing, same as Python
- **Verification**: Extensive testing with worker threads

### Error Handling
- **Challenge**: Graceful degradation on failures
- **Solution**: Multiple fallback strategies in Rust core
- **Monitoring**: Add telemetry for socket lifecycle events

## Testing Strategy

### Unit Tests
- Reference counting verification
- Automatic cleanup behavior
- Multiple client scenarios
- Error conditions

### Integration Tests
- Socket sharing between clients
- Cleanup on abnormal termination
- Worker thread compatibility
- Memory leak detection

### Performance Tests
- Benchmark vs current implementation
- Lock contention under load
- Cleanup latency measurements
- Memory usage comparison

## Implementation Checklist

- [ ] Add NAPI SocketReference wrapper class
- [ ] Implement StartSocketConnectionWithReference function
- [ ] Update TypeScript definitions
- [ ] Modify BaseClient to store socket reference
- [ ] Add reference counting tests
- [ ] Update documentation
- [ ] Add migration guide
- [ ] Performance benchmarking
- [ ] Memory leak testing
- [ ] Worker thread compatibility testing

## Code Locations

### Files to Modify

1. **Rust NAPI Bindings**:
   - `/home/ubuntu/valkey-glide/node/rust-client/src/lib.rs` - Add SocketReference wrapper

2. **TypeScript Client**:
   - `/home/ubuntu/valkey-glide/node/src/BaseClient.ts` - Update socket management
   - `/home/ubuntu/valkey-glide/node/src/GlideClient.ts` - Update if needed
   - `/home/ubuntu/valkey-glide/node/src/GlideClusterClient.ts` - Update if needed

3. **Type Definitions**:
   - `/home/ubuntu/valkey-glide/node/native.d.ts` - Add SocketReference types

4. **Tests**:
   - `/home/ubuntu/valkey-glide/node/tests/` - Add reference counting tests

### Existing Reference Implementations

1. **Python Implementation**:
   - `/home/ubuntu/valkey-glide/python/glide-async/src/lib.rs` - PyO3 wrapper pattern

2. **Rust Core**:
   - `/home/ubuntu/valkey-glide/glide-core/src/socket_reference.rs` - Core implementation
   - `/home/ubuntu/valkey-glide/glide-core/src/socket_listener.rs` - Socket listener integration

## Success Criteria

1. **Functional Requirements**:
   - Automatic socket cleanup when last client closes
   - No socket file leaks on abnormal termination
   - Thread-safe reference counting
   - Backward compatibility maintained

2. **Performance Requirements**:
   - No performance regression vs current implementation
   - Reduced lock contention under load
   - Predictable cleanup timing

3. **Quality Requirements**:
   - 100% test coverage for new code
   - No memory leaks detected
   - Documentation complete and accurate
   - Migration path clearly defined

## Conclusion

Implementing socket reference counting for the Node.js client is technically feasible and will bring significant benefits in terms of resource management, reliability, and consistency with the Python implementation. The approach leverages the existing Rust core implementation through NAPI bindings, ensuring a robust and maintainable solution.

The main challenges revolve around NAPI v2 limitations and ensuring proper memory management between JavaScript and Rust, but these can be addressed with careful implementation and testing. The phased migration strategy ensures backward compatibility while allowing gradual adoption of the new functionality.