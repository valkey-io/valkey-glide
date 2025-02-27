# Introducing the Valkey GLIDE Go Wrapper: Now in Public Preview!

The Valkey-Glide project is pleased to announce the public preview release of the GLIDE Go wrapper. This exciting addition to the Valkey ecosystem brings the power and reliability of Valkey to Go developers with a clean, intuitive API designed for performance and developer productivity.

## Getting Started in Minutes

The Valkey GLIDE Go wrapper provides a seamless experience for Go developers to interact with Valkey servers. With support for both standalone and cluster deployments, it's designed to be easy to integrate into your existing Go applications.

### Installation is straightforward:

Add Valkey GLIDE to your project with just two commands:

```bash
go get github.com/valkey-io/valkey-glide/go
go mod tidy
```

Then, connect to your Valkey server:

### Standalone Mode Connection Setup

For single-server deployments, the setup is simple:

```go
package main

import (
    "fmt"
    "github.com/valkey-io/valkey-glide/go/api"
)

func main() {
    // Connect to a standalone Valkey server
    config := api.NewGlideClientConfiguration().
        WithAddress(&api.NodeAddress{Host: "localhost", Port: 6379})
    
    client, err := api.NewGlideClient(config)
    if err != nil {
        fmt.Println("Error:", err)
        return
    }
    defer client.Close()
    
    // Test the connection
    result, err := client.Ping()
    if err != nil {
        fmt.Println("Error:", err)
        return
    }
    fmt.Println(result) // PONG
    
    // Store and retrieve a value
    client.Set("hello", "valkey")
    value, _ := client.Get("hello")
    fmt.Println(value) // valkey
}
```

### Cluster Mode Connection Setup

Need to work with a Valkey cluster?

Just as easy! The Go wrapper automatically discovers your entire cluster topology from a single seed node:

```go
package main

import (
    "fmt"
    "github.com/valkey-io/valkey-glide/go/api"
)

func main() {
    host := "localhost"
    port := 7001
    
    // Connect to a Valkey cluster through any node
    config := api.NewGlideClusterClientConfiguration().
        WithAddress(&api.NodeAddress{Host: host, Port: port})
    
    client, err := api.NewGlideClusterClient(config)
    if err != nil {
        fmt.Println("There was an error: ", err)
        return
    }
    
    res, err := client.Ping()
    if err != nil {
        fmt.Println("There was an error: ", err)
        return
    }
    fmt.Println(res) // PONG
    client.Close()
}
```

## Why You Should Be Excited

The Go wrapper extends Valkey GLIDE (General Language Independent Driver for the Enterprise) to the Go community, offering a robust, production-ready client that's built on the battle-tested Rust core. This client library is a thoughtfully designed experience for Go developers who need reliable, high-performance data access.

## Key Features You'll Love

### Smart Cluster Support with Automatic Topology Discovery

Connect to your Valkey cluster with minimal configuration. The client automatically discovers the entire cluster topology - no need to manually track every node!

```go
config := api.NewGlideClusterClientConfiguration().
    WithAddress(&api.NodeAddress{Host: "localhost", Port: 6379})

client, err := api.NewGlideClusterClient(config)
```

Just provide a single seed node, and the client handles the rest:

- **Automatic Discovery**: Finds all nodes in the cluster without manual configuration
- **Intelligent Request Routing**: Routes commands to the correct shard based on key distribution
- **Response Aggregation**: Efficiently combines responses from multiple nodes when needed
- **Dynamic Topology Updates**: Automatically detects when slots move between nodes and updates its routing information

### Multi-Connection Support

Need to work with multiple databases simultaneously? The Go wrapper makes it easy to create and manage multiple client connections:

```go
// Create a client for the first database
config1 := api.NewGlideClientConfiguration().
    WithAddress(&api.NodeAddress{Host: "localhost", Port: 6379}).
    WithDatabaseId(0) // Connect to database 0
client1, err := api.NewGlideClient(config1)

// Create another client for a different database
config2 := api.NewGlideClientConfiguration().
    WithAddress(&api.NodeAddress{Host: "localhost", Port: 6379}).
    WithDatabaseId(1) // Connect to database 1
client2, err := api.NewGlideClient(config2)

// Now you can work with both databases
client1.Set("user:123", "data in db 0")
client2.Set("user:123", "different data in db 1")
```

This flexibility allows you to efficiently work with different databases for various application needs, such as separating cache data from persistent data, or isolating different environments.

### Developer-Friendly Error Handling

We've implemented standardized error handling to make debugging and error management straightforward:

```go
result, err := client.Get("mykey")
if err != nil {
    log.Fatal("Glide example failed with an error: ", err.Msg)
}
```

No matter what kind of error occurs—whether it's a connection timeout, authentication failure, or invalid argument—the wrapper provides a consistent error interface. Under the hood, the wrapper categorizes errors into specific types like ConnectionError, TimeoutError, or RequestError, but you can handle them uniformly with a clean, consistent API.

### Built for Performance

The Go wrapper is designed from the ground up with performance in mind:

#### Concurrent Command Execution

The Go wrapper provides a synchronous API for simplicity and compatibility with existing Go key-value store clients. While each individual command is blocking (following the familiar patterns in the ecosystem), the client is fully thread-safe and designed for concurrent usage:

```go
// Example of concurrent execution using goroutines
func performConcurrentOperations(client *api.GlideClient) {
    var wg sync.WaitGroup
    
    // Launch 10 concurrent operations
    for i := 0; i < 10; i++ {
        wg.Add(1)
        go func(idx int) {
            defer wg.Done()
            key := fmt.Sprintf("key:%d", idx)
            value := fmt.Sprintf("value:%d", idx)
            
            // Each command blocks within its goroutine, but all 10 run concurrently
            _, err := client.Set(key, value)
            if err != nil {
                fmt.Printf("Error setting %s: %v\n", key, err)
                return
            }
            
            result, err := client.Get(key)
            if err != nil {
                fmt.Printf("Error getting %s: %v\n", key, err)
                return
            }
            
            fmt.Printf("Result for %s: %s\n", key, result)
        }(i)
    }
    
    wg.Wait()
}
```

Under the hood, the client efficiently handles these concurrent requests by:

1. Managing a connection pool for optimal resource usage
2. Implementing thread-safe command execution
3. Efficiently routing concurrent commands to the appropriate server nodes

While the current API is synchronous (matching other popular Go clients for similar data stores), the implementation is specifically optimized for concurrent usage through Go's native goroutines. The team is monitoring user feedback to assess whether to add async/channel-based APIs in future releases.

### Memory Efficiency

The Go wrapper leverages Rust's efficient memory management under the hood, utilizing a carefully designed FFI (Foreign Function Interface) layer to minimize overhead when transferring data between Rust and Go. This approach:

- Reduces unnecessary memory copying between languages
- Optimizes buffer management for large datasets
- Ensures proper memory cleanup to avoid leaks

### Graceful Connection Management

Robust connection handling with automatic reconnection strategies ensures your application remains resilient even during network instability:

```go
// Configure a custom reconnection strategy with exponential backoff
config := api.NewGlideClientConfiguration().
    WithAddress(&api.NodeAddress{Host: "localhost", Port: 6379}).
    WithReconnectStrategy(api.NewBackoffStrategy(
        5, // Initial delay in milliseconds
        10, // Maximum attempts
        50 // Maximum delay in milliseconds
    ))
```

## Advanced Configuration Options

### Read Strategies for Optimized Performance

Balance consistency and throughput with flexible read strategies:

```go
// Configure to prefer replicas for read operations
config := api.NewGlideClusterClientConfiguration().
    WithAddress(&api.NodeAddress{Host: "cluster.example.com", Port: 6379}).
    WithReadFrom(api.PreferReplica)

client, err := api.NewGlideClusterClient(config)

// Write to primary
client.Set("key1", "value1")

// Automatically reads from a replica (round-robin)
result, err := client.Get("key1")
```

Available strategies:

- **PRIMARY**: Always read from primary nodes for the freshest data
- **PREFER_REPLICA**: Distribute reads across replicas in round-robin fashion, falling back to primary when needed
- **AZ_AFFINITY**: (Coming soon) Prefer replicas in the same availability zone as the client

### Security-First Configuration

Secure your connections with built-in authentication and TLS support:

```go
// Configure with authentication
config := api.NewGlideClientConfiguration().
    WithAddress(&api.NodeAddress{Host: "localhost", Port: 6379}).
    WithCredentials(api.NewServerCredentials("username", "password")).
    WithUseTLS(true) // Enable TLS for encrypted connections
```

### Request Timeout and Handling

Fine-tune timeout settings for different workloads:

```go
// Set a longer timeout for operations that may take more time
config := api.NewGlideClientConfiguration().
    WithAddress(&api.NodeAddress{Host: "localhost", Port: 6379}).
    WithRequestTimeout(500) // 500ms timeout
```

## NEW in 1.3: Resource Tracking API

Monitor your Valkey resources with the new Info API that provides comprehensive server statistics and client information:

```go
// Get detailed information about the server and client
info, err := client.Info()
fmt.Println(info) // Prints detailed server information including:
// - Memory usage
// - Client connections
// - Persistence status
// - Cluster information (when applicable)
// - Replication details
```

This powerful API gives you visibility into your Valkey instances, helping with monitoring, debugging, and performance optimization.

## Full Configuration Example

Here's a comprehensive example showing all available configuration options:

```go
import (
    "fmt"
    "github.com/valkey-io/valkey-glide/go/api"
)

func main() {
    // Standalone client with full configuration
    standaloneConfig := api.NewGlideClientConfiguration().
        // Basic connection settings
        WithAddress(&api.NodeAddress{Host: "primary.example.com", Port: 6379}).
        WithClientName("my-app-cache-client"). // Identify this client in server logs
        WithDatabaseId(1). // Connect to database 1
        
        // Security settings
        WithCredentials(api.NewServerCredentials("user1", "password123")).
        WithUseTLS(true). // Enable encryption
        
        // Performance and reliability settings
        WithReadFrom(api.PreferReplica). // Read from replicas when possible
        WithRequestTimeout(500). // 500ms timeout for requests
        WithReconnectStrategy(api.NewBackoffStrategy(5, 10, 50)) // Custom reconnection strategy
    
    standalone, err := api.NewGlideClient(standaloneConfig)
    if err != nil {
        fmt.Println("Connection error:", err)
        return
    }
    defer standalone.Close()
    
    // Cluster client with full configuration
    clusterConfig := api.NewGlideClusterClientConfiguration().
        // Only need to specify one node - it will discover the rest
        WithAddress(&api.NodeAddress{Host: "cluster.example.com", Port: 6379}).
        WithClientName("my-app-cluster-client").
        
        // Security settings
        WithCredentials(api.NewServerCredentials("user1", "password123")).
        WithUseTLS(true).
        
        // Performance and reliability settings
        WithReadFrom(api.PreferReplica).
        WithRequestTimeout(500)
    
    cluster, err := api.NewGlideClusterClient(clusterConfig)
    if err != nil {
        fmt.Println("Cluster connection error:", err)
        return
    }
    defer cluster.Close()
    
    // Now you can use both clients
    // ...
}
```

## Behind the Scenes: Technical Architecture

```ascii
┌──────────┐      ┌─────┐      ┌──────────┐      ┌──────────┐      ┌──────────┐
│          │      │     │      │          │      │          │      │          │
│   Go     │─────▶│     │─────▶│ C Header │─────▶│   Rust   │─────▶│  Valkey  │
│ Wrapper  │      │ CGO │      │ cbindgen │      │   Core   │      │  Server  │
│          │◀─────│     │◀─────│          │◀─────│          │◀─────│          │
│          │      │     │      │          │      │          │      │          │
└──────────┘      └─────┘      └──────────┘      └──────────┘      └──────────┘
```

Valkey GLIDE's Go wrapper brings the power of Valkey to Go developers through a sophisticated multi-language architecture.

### Technical Components

- **Go Wrapper**: The language-specific interface for Go developers
- **CGO**: Allows Go code to call C functions
- **Cbindgen**: Automates the generation of C header files from Rust public APIs
- **Rust Core**: High-performance framework that connects to and communicates with Valkey servers
- **Rust FFI Library**: Enables cross-language function calls between Rust and other languages

The implementation workflow is straightforward: The core framework is written in Rust (lib.rs), which exposes public functions. These functions are converted to a C header file using Cbindgen. The Go wrapper then uses CGO to call these C functions, providing Go developers with an idiomatic interface while leveraging Rust's performance advantages.

This architecture ensures consistent behavior across all Valkey GLIDE language implementations (Java, Python, Node.js, and Go) while maintaining performance and reliability.

## Join the Journey

This public preview is just the beginning. We're actively developing and enhancing the Go wrapper, and we'd love your feedback and contributions. Try it out in your projects, share your experiences, and help us make it even better!

## Looking Forward

As we move toward general availability, we'll be expanding command support, enhancing performance, and adding even more features to make the Valkey GLIDE Go wrapper the best possible choice for Go developers.

Start building with Valkey GLIDE for Go today, and experience the difference that a thoughtfully designed, high-performance client can make in your applications!

## Contributors

A huge thank you to all the contributors who have made this possible - your dedication and expertise have created something truly special for the Go community.

[Janhavi Gupta](https://github.com/janhavigupta007)  
[Niharika Bhavaraju](https://github.com/niharikabhavaraju)  
[Edric Cuartero](https://github.com/EdricCua)  
[Omkar Mestry](https://github.com/omangesg)  
[Yury Fridlyand](https://github.com/Yury-Fridlyand)  
[Prateek Kumar](https://github.com/prateek-kumar-improving)
