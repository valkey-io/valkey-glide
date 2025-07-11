# Key Interfaces and Classes to Preserve

This document identifies the key interfaces and classes that must be preserved when implementing the JNI-based alternative to ensure API compatibility with existing code.

## Core Client Interfaces

### `glide.api.GlideClient`
- Main standalone client interface
- Methods to implement:
  - `get`/`set` and other Redis commands
  - `close()` for resource management
  - Configuration methods
  - Connection state methods (isConnected, etc.)
  - Batch operation methods

### `glide.api.GlideClusterClient`
- Main cluster mode client interface
- Extends functionality of standalone client
- Additional cluster-specific routing methods

### Command Interfaces
The command interfaces define the Redis operations that must be implemented:

| Interface | Description | Priority |
|-----------|-------------|----------|
| `StringBaseCommands` | Basic string operations (GET, SET, etc.) | High |
| `GenericBaseCommands` | Core Redis functionality | High |
| `HashBaseCommands` | Hash operations | Medium |
| `ListBaseCommands` | List operations | Medium |
| `SetBaseCommands` | Set operations | Medium |
| `SortedSetBaseCommands` | Sorted set operations | Medium |
| `PubSubBaseCommands` | Pub/Sub functionality | Low |
| `ServerManagementCommands` | Server control operations | Low |
| `TransactionsBaseCommands` | Transaction support | Low |

## Configuration Classes

### `BaseClientConfiguration`
- Common configuration for all client types
- Fields:
  - Server addresses
  - Credentials
  - TLS settings
  - Timeouts
  - Connection parameters

### `GlideClientConfiguration` / `GlideClusterClientConfiguration`
- Specialized configurations for standalone/cluster clients
- Must maintain all configuration options

## Response Types

### `GlideString`
- Wrapper for binary data
- Used for keys and values in Redis operations

### `ClusterValue` 
- Cluster-specific value wrapper
- Contains node information for routing

## API Principles to Maintain

1. **Asynchronous API**
   - All operations must return `CompletableFuture<T>`
   - Maintain non-blocking behavior

2. **Error Handling**
   - Consistent exception types and error mapping
   - Preserve exception hierarchies:
     - `GlideException` (base)
     - `ConnectionException`
     - `TimeoutException`
     - `RequestException`
     - `ConfigurationError`
     - `ClosingException`

3. **Resource Management**
   - Support AutoCloseable pattern
   - Proper cleanup of native resources
   - Thread safety in all operations

4. **Configuration Flexibility**
   - Builder pattern for configuration
   - Support for all connection parameters
   - TLS/SSL support

## Implementation Strategy

### Phase 1: Core Implementation
1. Implement `GlideJniClient` with base operations
   - GET, SET, PING as foundation
   - Direct JNI calls to glide-core

### Phase 2: API Compatibility Layer
1. Create adapter classes implementing full interfaces
   - Use `GlideJniClient` internally
   - Expose complete API surface

### Phase 3: Advanced Features
1. Add cluster support
2. Implement transactions
3. Add PubSub functionality
4. Support all Redis command types

### Phase 4: Replace Interface
1. Modify `BaseClient` implementation to use JNI internally
2. Maintain same public API
3. Remove UDS components gradually

## Performance Considerations

When implementing these interfaces with JNI, focus on:

1. **Minimizing Copies**
   - Direct access to Java byte arrays where possible
   - Zero-copy operations for large values

2. **Resource Management**
   - Proper cleanup of native resources
   - Use of Java's Cleaner API (instead of finalize)

3. **Command Batching**
   - Efficient implementation of batch operations
   - Minimizing JNI crossing overhead

4. **Thread Safety**
   - Safe access to shared native resources
   - Proper synchronization between Java and native code