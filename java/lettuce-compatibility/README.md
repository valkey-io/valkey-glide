# Lettuce Compatibility Layer

This sub-module provides a Lettuce-compatible API layer for Valkey GLIDE, allowing existing Lettuce applications to migrate to GLIDE with minimal code changes.

## Architecture

The Lettuce compatibility layer is implemented as a separate Gradle sub-module that:

- **Depends on**: The main `client` module containing GLIDE core functionality
- **Provides**: Lettuce-compatible classes and interfaces
- **Enables**: Drop-in replacement for Lettuce in existing applications

## Key Components

### Core Classes
- `RedisClient` - Main client class compatible with Lettuce API
- `StatefulRedisConnection` - Stateful connection interface for Redis operations
- `RedisURI` - URI configuration for Redis connections

### Configuration
- `RedisURI.Builder` - Fluent builder for creating Redis URIs
- `LettuceConfigurationMapper` - Maps Lettuce config to GLIDE config

### API Support
- `RedisStringCommands` - Synchronous String command interface (`set`, `get`)
- `RedisStringAsyncCommands` - Asynchronous String command interface (`set`, `get` returning `RedisFuture`)
- `RedisFuture` - Async result type (extends `CompletionStage` and `Future`; supports `await()`, composition)

## Usage

### Gradle Dependency

```gradle
dependencies {
    implementation group: 'io.valkey', name: 'valkey-glide-lettuce-compatibility', version: '2.1.0', classifier: 'osx-aarch_64'
}
```

### Maven Dependency

```xml
<dependency>
    <groupId>io.valkey</groupId>
    <artifactId>valkey-glide-lettuce-compatibility</artifactId>
    <version>2.1.0</version>
    <classifier>osx-aarch_64</classifier>
</dependency>
```

### Basic Example

```java
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStringCommands;

// Create URI
RedisURI uri = RedisURI.Builder.redis("localhost", 6379).build();

// Create client and connect
RedisClient client = RedisClient.create(uri);
StatefulRedisConnection<String, String> connection = client.connect();

// Synchronous commands
RedisStringCommands<String, String> sync = connection.sync();
sync.set("key", "value");
String value = sync.get("key");

// Asynchronous commands
RedisStringAsyncCommands<String, String> async = connection.async();
RedisFuture<String> setFuture = async.set("key2", "value2");
RedisFuture<String> getFuture = async.get("key2");
// use setFuture.get(5, TimeUnit.SECONDS) or thenApply() etc.

// Cleanup
connection.close();
client.shutdown();
```

### URI Builder Options

```java
RedisURI uri = RedisURI.builder()
    .withHost("redis.example.com")
    .withPort(6380)
    .withSsl(true)
    .withPassword("mypassword")
    .withAuthentication("username", "password")
    .withDatabase(3)
    .withTimeout(Duration.ofSeconds(5))
    .build();
```

### Create from URI String

```java
RedisURI uri = RedisURI.create("redis://localhost:6379");
RedisURI sslUri = RedisURI.create("rediss://user:pass@secure-host:6380/3");
```

## Current Limitations

This is an initial implementation focused on connection and basic String operations:

- **Commands**: Only `SET` and `GET` are currently supported (sync and async)
- **Cluster mode**: Not yet supported (standalone only)
- **Reactive**: Not yet supported (sync and async only; no Project Reactor)
- **Connection pooling**: Not yet implemented

More features will be added in future releases.

## Migration from Lettuce

For applications currently using Lettuce:

1. Replace the `io.lettuce:lettuce-core` dependency with `io.valkey:valkey-glide-lettuce-compatibility`
2. Your existing code using `RedisClient`, `RedisURI`, and `StatefulRedisConnection` should work with minimal or no changes
3. Note the current limitations above - ensure your application only uses supported features

## Building

```bash
cd java
./gradlew :lettuce-compatibility:build
./gradlew :lettuce-compatibility:test
./gradlew :lettuce-compatibility:publishToMavenLocal
```

## Testing

```bash
# Unit tests
./gradlew :lettuce-compatibility:test

# Integration tests (requires running Valkey/Redis server)
./gradlew :integTest:test --tests 'compatibility.lettuce.*'
```

## Contributing

When adding new features to this compatibility layer:

1. Follow the existing patterns from the main Lettuce API
2. Add unit tests in `src/test/java`
3. Add integration tests in the `integTest` module
4. Update this README with new capabilities
5. Ensure all tests pass before submitting

## License

Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
