# Valkey-Glide Refactoring Technical Reference

## Core Architecture

### Direct Native Client
**File:** `/home/ubuntu/valkey-glide/java/src/main/java/io/valkey/glide/core/client/GlideClient.java`

**Key Features:**
- Direct JNI communication to Rust glide-core
- No protobuf dependencies
- Efficient memory management
- Type-safe command execution

**Core Methods:**
```java
public class GlideClient {
    // Configuration and lifecycle
    public GlideClient(Config config) throws Exception
    public void close()

    // Direct command execution
    public CompletableFuture<Object> executeCommand(Command command)

    // High-level operations
    public CompletableFuture<String> get(String key)
    public CompletableFuture<String> set(String key, String value)
    public CompletableFuture<String> ping()

    // Configuration class
    public static class Config {
        public Config(List<String> addresses)
        // addresses format: ["host:port", "host:port"]
    }
}
```

### Command System
**Files:**
- `/home/ubuntu/valkey-glide/java/src/main/java/io/valkey/glide/core/commands/CommandType.java`
- `/home/ubuntu/valkey-glide/java/src/main/java/io/valkey/glide/core/commands/Command.java`

**CommandType Enum:**
```java
public enum CommandType {
    // String operations
    GET, SET, MGET, MSET, GETSET, STRLEN, APPEND,

    // Key operations
    DEL, EXISTS, EXPIRE, EXPIREAT, TTL, PTTL, TYPE,

    // Hash operations
    HGET, HSET, HGETALL, HKEYS, HVALS, HDEL, HEXISTS,

    // List operations
    LPUSH, RPUSH, LPOP, RPOP, LLEN, LRANGE, LINDEX,

    // Set operations
    SADD, SREM, SMEMBERS, SISMEMBER, SCARD, SPOP,

    // Sorted set operations
    ZADD, ZREM, ZRANGE, ZCARD, ZSCORE, ZRANK,

    // Server operations
    PING, INFO, CONFIG_GET, CONFIG_SET, FLUSHDB, FLUSHALL,

    // Connection operations
    AUTH, SELECT, ECHO, QUIT, TIME,

    // Advanced operations
    EVAL, EVALSHA, SCRIPT_LOAD, SCRIPT_EXISTS, SCRIPT_FLUSH,

    // Pub/Sub operations
    PUBLISH, SUBSCRIBE, UNSUBSCRIBE, PSUBSCRIBE, PUNSUBSCRIBE,

    // Transaction operations
    MULTI, EXEC, DISCARD, WATCH, UNWATCH,

    // Cluster operations
    CLUSTER_INFO, CLUSTER_NODES, CLUSTER_SLOTS
}
```

**Command Class:**
```java
public class Command {
    private final CommandType type;
    private final String[] args;

    public Command(CommandType type, String... args)
    public CommandType getType()
    public String[] getArgs()

    // Factory methods
    public static Command get(String key)
    public static Command set(String key, String value)
    public static Command del(String... keys)
    // ... more factory methods
}
```

## Module System Configuration

### Core Module
**File:** `/home/ubuntu/valkey-glide/java/src/main/java/module-info.java`
```java
module io.valkey.glide.core {
    exports io.valkey.glide.core.client;
    exports io.valkey.glide.core.commands;

    requires java.base;
    requires java.logging;
}
```

### Client Module
**File:** `/home/ubuntu/valkey-glide/java/client/src/main/java/module-info.java`
```java
module glide.api {
    exports glide.api;
    exports glide.api.models;
    exports glide.api.models.configuration;
    exports glide.api.models.commands;
    // ... other exports

    requires io.valkey.glide.core;  // Dependency on core module
    requires static lombok;
    requires org.apache.commons.lang3;
}
```

## Build System

### Gradle Structure
```
valkey-glide/java/
├── build.gradle                 # Root build script
├── settings.gradle              # Module configuration
├── src/                         # Core module (io.valkey.glide.core)
├── client/                      # Client module (glide.api)
├── integTest/                   # Integration tests
└── java-jni/                    # JNI bindings
```

### Key Build Commands
```bash
# Build core module (works)
./gradlew compileJava

# Build client module (needs fixing)
./gradlew :client:compileJava

# Run integration tests (blocked)
./gradlew :integTest:test

# Build native library
./gradlew buildNative
```

## JNI Integration

### Native Method Signatures
```java
// In GlideClient.java
private native long createClient(String[] addresses) throws Exception;
private native void closeClient(long clientPtr);
private native String executeCommand(long clientPtr, String command, String[] args);
```

### Native Library Loading
```java
static {
    try {
        System.loadLibrary("glide_java"); // Loads libglide_java.so
    } catch (UnsatisfiedLinkError e) {
        // Fallback loading logic
    }
}
```

## Error Handling

### Exception Hierarchy
```java
// Core exceptions
public class GlideException extends Exception
public class ConnectionException extends GlideException
public class CommandException extends GlideException
public class TimeoutException extends GlideException

// Integration test exceptions (existing)
public class RequestException extends RuntimeException
public class ClosingException extends RuntimeException
```

### Error Propagation
```java
// Convert native errors to Java exceptions
public CompletableFuture<Object> executeCommand(Command command) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            return executeCommandNative(clientPtr, command.getType().name(), command.getArgs());
        } catch (Exception e) {
            throw new RuntimeException("Command execution failed", e);
        }
    });
}
```

## Memory Management

### Resource Lifecycle
```java
public class GlideClient implements AutoCloseable {
    private final long clientPtr;  // Native client pointer
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeClientNative(clientPtr);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close(); // Cleanup safety net
        super.finalize();
    }
}
```

### Connection Pooling
```java
// In Config class
public static class Config {
    private final List<String> addresses;
    private final int maxConnections;
    private final int connectionTimeout;
    private final boolean useTLS;

    // Connection management handled in native code
}
```

## Performance Optimizations

### Object Reuse
```java
// Command object reuse
private static final Command PING_COMMAND = new Command(CommandType.PING);
private static final Command INFO_COMMAND = new Command(CommandType.INFO);

// String interning for common values
public static final String OK = "OK";
public static final String PONG = "PONG";
```

### Batch Operations
```java
// Efficient batch command execution
public CompletableFuture<Object[]> executeBatch(List<Command> commands) {
    return CompletableFuture.supplyAsync(() -> {
        String[][] batchArgs = commands.stream()
            .map(cmd -> new String[]{cmd.getType().name(), ...cmd.getArgs()})
            .toArray(String[][]::new);
        return executeBatchNative(clientPtr, batchArgs);
    });
}
```

### Memory-Efficient Type Conversion
```java
// Avoid unnecessary object creation
public CompletableFuture<Long> del(String... keys) {
    return executeCommand(CommandType.DEL, keys)
        .thenApply(result -> {
            // Parse long directly without string conversion
            if (result instanceof Number) {
                return ((Number) result).longValue();
            }
            return Long.parseLong(result.toString());
        });
}
```

## Debugging and Diagnostics

### Logging Integration
```java
private static final Logger logger = Logger.getLogger(GlideClient.class.getName());

public CompletableFuture<Object> executeCommand(Command command) {
    logger.fine("Executing command: " + command.getType() + " with args: " + Arrays.toString(command.getArgs()));
    // ... execution logic
}
```

### Statistics Collection
```java
public Map<String, Object> getStatistics() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("connections", getConnectionCount());
    stats.put("requests_sent", getRequestCount());
    stats.put("responses_received", getResponseCount());
    stats.put("errors", getErrorCount());
    return stats;
}
```

### Health Checks
```java
public CompletableFuture<Boolean> isHealthy() {
    return ping()
        .thenApply(response -> "PONG".equals(response))
        .exceptionally(throwable -> false);
}
```

## Threading Model

### Async Execution
```java
// All operations return CompletableFuture
private final ExecutorService executor = ForkJoinPool.commonPool();

public CompletableFuture<Object> executeCommand(Command command) {
    return CompletableFuture.supplyAsync(() -> {
        // Native call on background thread
        return executeCommandNative(clientPtr, command.getType().name(), command.getArgs());
    }, executor);
}
```

### Thread Safety
```java
// Client is thread-safe
private final Object commandLock = new Object();

public CompletableFuture<Object> executeCommand(Command command) {
    // Native client handles concurrency internally
    // No Java-level locking needed for command execution
    return CompletableFuture.supplyAsync(...);
}
```
