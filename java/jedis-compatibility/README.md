# Jedis Compatibility Layer

This sub-module provides a Jedis-compatible API layer for Valkey GLIDE, allowing existing Jedis applications to migrate to GLIDE with minimal code changes.

## Architecture

The Jedis compatibility layer is implemented as a separate Gradle sub-module that:

- **Depends on**: The main `client` module containing GLIDE core functionality
- **Provides**: Jedis-compatible classes and interfaces
- **Enables**: Drop-in replacement for Jedis in existing applications

## Key Components

### Core Classes
- `Jedis` - Main client class compatible with Jedis API
- `JedisCluster` - Cluster client compatible with Jedis cluster API
- `UnifiedJedis` - Unified interface for both standalone and cluster operations
- `JedisPool` / `JedisPooled` - Connection pooling implementations

### Configuration
- `JedisClientConfig` - Client configuration interface
- `DefaultJedisClientConfig` - Default configuration implementation
- `ConfigurationMapper` - Maps Jedis config to GLIDE config
- `ClusterConfigurationMapper` - Maps Jedis cluster config to GLIDE cluster config

### Protocol Support
- `Protocol` - Redis protocol constants and commands
- `RedisProtocol` - Protocol version handling
- Various parameter classes for command options

## Usage

### Gradle Dependency

```gradle
dependencies {
    implementation group: 'io.valkey', name: 'valkey-glide-jedis-compatibility', version: '2.1.0', classifier: 'osx-aarch_64'
}
```

### Maven Dependency

```xml
<dependency>
    <groupId>io.valkey</groupId>
    <artifactId>valkey-glide-jedis-compatibility</artifactId>
    <version>2.1.0</version>
    <classifier>osx-aarch_64</classifier>
</dependency>
```

### Basic Example

```java
import redis.clients.jedis.Jedis;

// Drop-in replacement for Jedis
try (Jedis jedis = new Jedis("localhost", 6379)) {
    jedis.set("key", "value");
    String value = jedis.get("key");
    System.out.println(value); // prints: value
}
```

## Migration Guide

See the [compatibility layer migration guide](./compatibility-layer-migration-guide.md) for detailed migration instructions.

## Build Commands

```bash
# Compile the compatibility layer
./gradlew :jedis-compatibility:compileJava

# Run tests
./gradlew :jedis-compatibility:test

# Build JAR
./gradlew :jedis-compatibility:jar

# Publish to local repository
./gradlew :jedis-compatibility:publishToMavenLocal
```

## Module Dependencies

```
jedis-compatibility
├── client (GLIDE core client)
│   ├── protobuf-java
│   ├── netty-handler
│   └── native libraries (Rust FFI)
└── commons-pool2 (connection pooling)
```
