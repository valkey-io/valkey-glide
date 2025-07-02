# Jedis Compatibility Wrapper for Valkey GLIDE

This document describes the Jedis compatibility wrapper that has been implemented to provide a Jedis-like API while using Valkey GLIDE underneath.

## Overview

The wrapper allows existing Jedis code to work with minimal changes by providing the same API surface while leveraging the performance and reliability benefits of Valkey GLIDE.

## Implementation

### Core Classes

1. **`redis.clients.jedis.Jedis`** - Main client class that mimics the Jedis API
2. **`redis.clients.jedis.JedisPool`** - Connection pool implementation
3. **`redis.clients.jedis.JedisException`** - Base exception class
4. **`redis.clients.jedis.JedisConnectionException`** - Connection-specific exception class

### Supported Functionality

The current implementation supports the basic operations needed for the example code:

- **Connection Management**: Direct connections and pooled connections
- **Basic Commands**: `SET`, `GET`, `PING`
- **Resource Management**: Proper connection closing and pool management

### Example Usage

The wrapper supports the exact code patterns you requested:

```java
// Simple connection
Jedis jedis = new Jedis("localhost", 6379);
jedis.set("key", "value");
jedis.close();

// Pool connection
JedisPool pool = new JedisPool("localhost", 6379);
try (Jedis jedis = pool.getResource()) {
    jedis.set("key", "value");
}
pool.close();
```

## Architecture

### Connection Management

- **Direct Connections**: Each `Jedis` instance creates its own `GlideClient`
- **Pooled Connections**: `JedisPool` manages a queue of reusable `Jedis` instances
- **Resource Cleanup**: Proper cleanup of underlying GLIDE clients when connections are closed

### Error Handling

- Wraps GLIDE exceptions in Jedis-compatible exception types
- Maintains the same error semantics as the original Jedis client

### Threading

- The pool implementation uses thread-safe collections
- Each connection is designed to be used by a single thread at a time
- Pool operations are thread-safe

## Files Created

1. `/client/src/main/java/redis/clients/jedis/Jedis.java` - Main client class
2. `/client/src/main/java/redis/clients/jedis/JedisPool.java` - Connection pool
3. `/client/src/main/java/redis/clients/jedis/JedisException.java` - Base exception
4. `/client/src/main/java/redis/clients/jedis/JedisConnectionException.java` - Connection exception
5. `/client/src/main/java/redis/clients/jedis/JedisWrapperTest.java` - Test class
6. `/client/src/main/java/redis/clients/jedis/JedisWrapperExample.java` - Example usage

## Module Configuration

The `module-info.java` has been updated to export the `redis.clients.jedis` package, making it available to client applications.

## Current Limitations

This is a minimal implementation focused on supporting the specific example code. To fully migrate from Jedis, you would need to:

1. **Expand Command Support**: Add support for all Redis/Valkey commands
2. **Advanced Features**: Implement transactions, pipelining, pub/sub, etc.
3. **Configuration Options**: Add support for all Jedis configuration options
4. **Cluster Support**: Add cluster-aware functionality
5. **Async Operations**: Consider async API compatibility

## Next Steps

To expand this wrapper for full Jedis compatibility:

1. **Command Implementation**: Systematically implement all Jedis commands
2. **Interface Compliance**: Implement all Jedis interfaces
3. **Configuration Mapping**: Map all Jedis configuration options to GLIDE equivalents
4. **Testing**: Comprehensive testing against Jedis test suites
5. **Performance Optimization**: Optimize the wrapper layer for minimal overhead

## Testing

The implementation has been tested with:
- Successful compilation with the existing GLIDE build system
- Basic functionality verification through example code
- Integration with the existing benchmark framework

To test with a running Redis/Valkey server, run the example classes provided.
