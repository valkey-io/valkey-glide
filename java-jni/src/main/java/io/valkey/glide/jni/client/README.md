# Valkey GLIDE JNI Client

This is a high-performance JNI-based client for Valkey that directly integrates with the Rust glide-core library. It provides significant performance improvements over the standard Unix Domain Socket (UDS) implementation by eliminating inter-process communication overhead.

## Key Features

- **Direct Integration**: Uses JNI to communicate directly with the Rust glide-core library
- **High Performance**: 1.8-2.9x faster than UDS implementation in benchmarks
- **Async API**: Modern CompletableFuture-based asynchronous API
- **Safe Resource Management**: Uses Java 11+ Cleaner API for proper resource cleanup
- **Configuration Options**: Flexible configuration matching glide-core design
- **Thread Safety**: Properly synchronized for concurrent usage

## Usage Example

```java
// Create a client
try (GlideJniClient client = new GlideJniClient(
        new GlideJniClient.Config(Arrays.asList("localhost:6379"))
                .useTls(false)
                .requestTimeout(5000)
                .connectionTimeout(2000))) {
    
    // Perform operations asynchronously
    CompletableFuture<String> setFuture = client.set("key", "value");
    CompletableFuture<String> getFuture = client.get("key");
    
    // Wait for results
    String setResult = setFuture.get();
    String getValue = getFuture.get();
    
    System.out.println("SET result: " + setResult);
    System.out.println("GET result: " + getValue);
}
```

## Configuration Options

The `Config` class provides the following options:

- **addresses**: List of server addresses in "host:port" format (required)
- **useTls**: Whether to use TLS for secure connections (default: false)
- **clusterMode**: Whether to use cluster mode (default: false)
- **requestTimeout**: Timeout for individual requests in milliseconds (default: 5000)
- **connectionTimeout**: Timeout for connection establishment in milliseconds (default: 5000)
- **credentials**: Username and password for authentication (default: none)
- **databaseId**: Database ID to select (default: 0)

## Resource Management

The client implements `AutoCloseable` for proper resource management with try-with-resources. Additionally, it uses the Java 11+ Cleaner API to ensure resources are released even if `close()` is not explicitly called.

## Thread Safety

The client is thread-safe and can be safely used from multiple threads concurrently. Each command execution is isolated, and the underlying connection management ensures proper synchronization.

## Performance

Benchmarks show significant performance improvements over the standard UDS implementation:

- **Throughput**: 1.8-2.0x higher operations per second
- **Latency**: 1.6-2.9x lower latency across various operations
- **Concurrency**: Consistent performance improvements across different concurrency levels
- **Data Size**: Efficient handling of different payload sizes

## Current Limitations

This is an early implementation with some limitations:

- Limited command support (currently GET, SET, PING)
- No cluster topology updates
- Basic error handling
- No binary data support

These limitations will be addressed in future updates as outlined in the roadmap document.