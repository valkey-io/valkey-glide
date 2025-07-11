# Flow Mapping: Java API to Communication Layer

This document maps the flow from Java API calls through the UDS communication layer in the current implementation, and contrasts it with the JNI approach.

## Current UDS Implementation Flow

### Initialization Flow

1. **Client Creation**
   - Java: `GlideClient` constructor takes a `GlideClientConfiguration`
   - Creates a `ConnectionManager` instance which manages connection to the Rust process
   - Creates a `ChannelHandler` which maintains the UDS socket connection
   - Calls `ConnectionManager.connectToValkey(configuration)` to establish connection

2. **UDS Socket Connection**
   - `ConnectionManager` prepares a `ConnectionRequest` protobuf message
   - `ChannelHandler` creates a UDS socket connection to the Rust process
   - Writes the `ConnectionRequest` to the socket
   - Waits for a response from the socket

### Command Execution Flow

1. **Command Construction**
   - Java: `client.get(key)` or other method called
   - Converted to `CommandManager.submitNewCommand()`
   - Creates a `CommandRequest` protobuf message
   - Adds command type, arguments, and routing information

2. **UDS Communication**
   - `CommandManager.submitCommandToChannel(command, responseHandler)`
   - `ChannelHandler.write(request, flush)` serializes and sends protobuf request over UDS
   - Request includes a callback ID for asynchronous result handling
   - `CallbackDispatcher` registers a `CompletableFuture` for that callback ID

3. **Response Handling**
   - Rust process executes the command and sends response over UDS
   - Socket channel `ReadHandler` reads response and passes to `CallbackDispatcher`
   - `CallbackDispatcher` finds the corresponding future and completes it
   - Java client receives the completed `CompletableFuture` result

4. **Type Conversion**
   - Response is processed by the registered `responseHandler`
   - Converts protobuf `Response` to expected Java types 
   - Error handling for various failure cases

## JNI Implementation Flow

### Initialization Flow

1. **Client Creation**
   - Java: `GlideJniClient` constructor takes a `Config`
   - Directly calls `createClient` JNI method
   - JNI creates a `glide_core::Client` in the same process
   - Uses modern Java Cleaner API for resource management

2. **JNI Connection**
   - JNI method constructs a `ConnectionRequest` for `glide_core`
   - Calls `Client::new()` directly in the same process
   - No socket communication or serialization needed
   - Returns a pointer to the native client as a Java long

### Command Execution Flow

1. **Command Construction**
   - Java: `jniClient.get(key)` called
   - Directly calls the corresponding JNI method
   - JNI constructs a Redis command using `redis::cmd()`

2. **Direct Execution**
   - JNI accesses the native client pointer
   - Calls `client.send_command(&cmd, None)` directly
   - Command execution happens in the same process
   - No serialization or IPC overhead

3. **Response Handling**
   - JNI receives the Rust `redis::Value` directly
   - Converts directly to Java types (String, boolean, etc.)
   - Returns as completed `CompletableFuture` (for API compatibility)
   - Error handling maps Rust errors to Java exceptions

## Key Performance Differences

1. **Eliminated IPC**
   - UDS requires inter-process communication
   - JNI performs everything in the same process

2. **Reduced Serialization**
   - UDS requires serialization to/from protobuf
   - JNI converts directly between Java/Rust types

3. **Fewer Memory Copies**
   - UDS implementation has multiple buffer copies
   - JNI implementation can minimize data copies

4. **Protocol Simplification**
   - UDS implementation uses custom protobuf protocol
   - JNI directly accesses glide-core's Redis protocol handling

5. **Overhead Elimination**
   - UDS channel setup, maintenance, reconnection logic eliminated
   - Socket I/O and error handling simplified

## Benchmark Results

Performance comparison between UDS and JNI implementations:

| Metric | UDS Implementation | JNI Implementation | Improvement |
|--------|-------------------|-------------------|------------|
| Throughput | 74,261 TPS | 124,521 TPS | 1.68x |
| GET Avg Latency | 1.342 ms | 0.796 ms | 1.69x |
| GET P99 Latency | 5.45 ms | 1.955 ms | 2.79x |
| SET Avg Latency | 1.346 ms | 0.801 ms | 1.68x |
| SET P99 Latency | 5.535 ms | 1.995 ms | 2.77x |

The JNI implementation provides significant performance improvements across all metrics, with particularly notable improvement in tail latencies (P99).