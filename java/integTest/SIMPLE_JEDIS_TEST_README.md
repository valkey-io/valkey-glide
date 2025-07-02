# Simple Jedis Compatibility Test

This document describes the `SimpleJedisCompatibilityTest` class, which demonstrates how to use the Jedis compatibility layer with Valkey GLIDE.

## Overview

The `SimpleJedisCompatibilityTest` is a simple test class that follows the same pattern as `SharedCommandTests.java` but focuses specifically on testing the Jedis compatibility layer. It demonstrates how end users can migrate from Jedis to Valkey GLIDE with minimal code changes.

## Test Structure

The test class includes the following test methods:

1. **`testSimpleSetAndGet()`** - Tests basic SET and GET operations using direct Jedis connection
2. **`testSetAndGetWithPool()`** - Tests SET and GET operations using JedisPool
3. **`testPing()`** - Tests the PING command
4. **`testPingWithMessage()`** - Tests PING with a custom message
5. **`testMultipleOperations()`** - Tests multiple SET/GET operations
6. **`testNonExistentKey()`** - Tests behavior when getting a non-existent key
7. **`testConnectionIsWorking()`** - Basic connectivity test
8. **`testBasicJedisUsagePattern()`** - Demonstrates typical Jedis usage patterns

## Usage Examples

The test demonstrates two main usage patterns that end users would follow:

### Direct Connection (like original Jedis)
```java
Jedis jedis = new Jedis("localhost", 6379);
jedis.set("key", "value");
String value = jedis.get("key");
jedis.close();
```

### Pool Connection (like original Jedis)
```java
JedisPool pool = new JedisPool("localhost", 6379);
try (Jedis jedis = pool.getResource()) {
    jedis.set("key", "value");
    String value = jedis.get("key");
}
pool.close();
```

## Prerequisites

1. **Running Redis/Valkey Server**: The test assumes a Redis or Valkey server is running on `localhost:6379`
2. **Compiled Project**: The project must be compiled with the Jedis compatibility layer

## Running the Test

### Compile the Test
```bash
cd /path/to/valkey-glide/java
./gradlew :integTest:compileTestJava
```

### Run the Specific Test
```bash
./gradlew :integTest:test --tests "glide.SimpleJedisCompatibilityTest"
```

### Run All Integration Tests
```bash
./gradlew :integTest:test
```

## What This Test Validates

1. **API Compatibility**: Ensures the Jedis compatibility layer provides the same API as original Jedis
2. **Basic Operations**: Validates that SET, GET, and PING operations work correctly
3. **Connection Management**: Tests both direct connections and pooled connections
4. **Resource Management**: Ensures proper cleanup of connections and pools
5. **Error Handling**: Validates that operations behave as expected (e.g., null for non-existent keys)

## Supported Operations

Currently, the Jedis compatibility layer supports:
- `set(String key, String value)` - Set a string value
- `get(String key)` - Get a string value
- `ping()` - Test server connectivity
- `ping(String message)` - Test server connectivity with custom message
- `close()` - Close the connection
- Connection pooling via `JedisPool`

## Limitations

- The `del()` method is not yet implemented in the compatibility layer
- This is a minimal implementation focused on basic operations
- Advanced Jedis features (transactions, pipelining, pub/sub) are not yet supported

## Integration with Existing Tests

This test follows the same patterns as other integration tests in the project:
- Uses JUnit 5 annotations
- Follows the same naming conventions
- Uses similar setup/teardown patterns
- Can be run alongside other integration tests

## Future Enhancements

To expand this test for full Jedis compatibility:
1. Add tests for additional Redis commands as they're implemented
2. Add tests for error conditions and exception handling
3. Add tests for advanced features like transactions and pipelining
4. Add performance comparison tests
5. Add tests for cluster mode compatibility
