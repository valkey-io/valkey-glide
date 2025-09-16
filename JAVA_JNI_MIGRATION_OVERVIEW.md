# Java JNI Migration Overview

This document describes the changes in PR #4742 that migrate the Java client from Unix Domain Sockets (UDS) to JNI-based communication.

## Summary

PR #4742 replaces UDS-based communication with direct JNI calls to the Rust glide-core library. This enables Windows support and potentially improves performance by eliminating socket layer overhead.

## File Changes Analysis

### Dependencies Removed (java/client/build.gradle)
The following Netty dependencies were removed:
- `netty-handler` v4.1.121.Final
- `netty-transport-native-epoll` (linux-x86_64, linux-aarch_64)  
- `netty-transport-native-kqueue` (osx-aarch_64, osx-x86_64)
- `shadow` plugin for JAR shading

### Dependencies Added (java/Cargo.toml)
New Rust dependencies for JNI implementation:
- `jni = "0.21.1"`
- `anyhow = "1.0"`
- `dashmap = "6.1.0"`
- `parking_lot = "0.12"`
- `scopeguard = "1.2"`
- `telemetrylib` path dependency

### Classes Removed
These UDS/Netty-related classes were deleted:
- `glide.connectors.handlers.CallbackDispatcher`
- `glide.connectors.handlers.ChannelHandler` 
- `glide.connectors.handlers.ProtobufSocketChannelInitializer`
- `glide.connectors.handlers.ReadHandler`
- `glide.connectors.resources.EpollResource`
- `glide.connectors.resources.KQueuePoolResource`
- `glide.connectors.resources.Platform`
- `glide.connectors.resources.ThreadPoolResource`
- `glide.connectors.resources.ThreadPoolResourceAllocator`
- `glide.ffi.resolvers.SocketListenerResolver`

### Classes Added
New JNI infrastructure:
- `glide.internal.GlideCoreClient` - Main JNI client interface
- `glide.internal.GlideNativeBridge` - JNI method declarations
- `glide.internal.AsyncRegistry` - Callback correlation for async operations
- `glide.internal.protocol.CommandRequest` - Command serialization
- `glide.internal.protocol.BinaryCommandRequest` - Binary command support
- `glide.internal.protocol.BatchRequest` - Batch operation support
- `glide.internal.protocol.RouteInfo` - Cluster routing information

### Modified Classes

#### BaseClient.java
- Added native library loading in static initializer
- Modified client creation to use JNI instead of UDS
- Added `DirectByteBuffer` handling for large responses
- Removed UDS-specific builder methods
- Added PubSub message enqueueing method `__enqueuePubSubMessage`

#### GlideClient.java and GlideClusterClient.java  
- Updated client creation to use `BaseClient.createClient`
- Removed duplicate script command implementations (moved to BaseClient)
- Added Windows DLL support in native library copying

#### Build Configuration
- Relaxed Protobuf version requirement from 29.0+ to 3.0+
- Simplified JAR packaging (removed shadow JAR)
- Updated native library copying to include `*.dll` files
- Modified Rust build profile to use `panic = "abort"`

## Windows Support

The key Windows compatibility change is replacing UDS (not natively supported on Windows) with JNI calls that work across all platforms. Native library copying now includes `*.dll` files alongside `*.so` and `*.dylib`.

## API Compatibility

Public APIs remain unchanged. `GlideClient.createClient()` and `GlideClusterClient.createClient()` have the same signatures and behavior. Existing user code should continue to work without modifications.

## Technical Changes

1. **Communication Layer**: Direct JNI calls replace UDS socket communication
2. **Memory Management**: Uses Java `Cleaner` API instead of deprecated `finalize()`
3. **Error Handling**: Native error codes mapped to existing Java exception types
4. **Concurrency**: `AsyncRegistry` provides thread-safe callback correlation

## Build Changes

- Removed shadow plugin and Netty shading
- Simplified JAR configuration  
- Updated Rust dependencies for JNI support
- Cross-platform native library support

This migration maintains API compatibility while replacing the underlying communication mechanism from socket-based to JNI-based interaction with the Rust core.