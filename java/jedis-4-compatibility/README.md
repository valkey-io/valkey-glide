# Jedis 4.x Compatibility Layer

This sub-module provides a Jedis 4.x-compatible API layer for Valkey GLIDE, allowing existing Jedis 4.x applications to migrate to GLIDE with minimal code changes.

## Why a Separate Jedis 4.x Layer?

Jedis 5.x introduced breaking API changes that are incompatible with Jedis 4.x applications:

| Feature | Jedis 4.x | Jedis 5.x | Impact |
|---------|-----------|-----------|--------|
| Pool Config Generic Type | `GenericObjectPoolConfig<Connection>` | `GenericObjectPoolConfig<Object>` | Method signature mismatch |
| Pool Config for JedisPool | `GenericObjectPoolConfig<Jedis>` | `GenericObjectPoolConfig<Jedis>` | ✅ Compatible |
| RedisProtocol API | Not present | `getRedisProtocol()` in config | New method in interface |
| Protocol Version | Always RESP2 | RESP2/RESP3 configurable | Behavioral difference |

Apps using Jedis 4.x that rely on `JedisPooled` with `GenericObjectPoolConfig<Connection>` will fail to compile against the Jedis 5.x-compatible layer.

## Architecture

The Jedis 4.x compatibility layer is implemented as a separate Gradle sub-module that:

- **Depends on**: The main `client` module containing GLIDE core functionality
- **Provides**: Jedis 4.x-compatible classes and interfaces
- **Enables**: Drop-in replacement for Jedis 4.x in existing applications
- **Protocol**: Uses RESP2 by default (Jedis 4.x behavior)

## Key Components

### Core Classes
- `Jedis` - Main client class compatible with Jedis 4.x API
- `JedisCluster` - Cluster client compatible with Jedis 4.x cluster API
- `UnifiedJedis` - Unified interface for both standalone and cluster operations
- `JedisPool` - Connection pooling with `GenericObjectPoolConfig<Jedis>`
- `JedisPooled` - Pooled client with `GenericObjectPoolConfig<Connection>` (Jedis 4.x style)

### Configuration
- `JedisClientConfig` - Client configuration interface (without `getRedisProtocol()`)
- `DefaultJedisClientConfig` - Default configuration implementation (RESP2 only)
- `ConfigurationMapper` - Maps Jedis 4.x config to GLIDE config
- `ClusterConfigurationMapper` - Maps Jedis 4.x cluster config to GLIDE cluster config

### Protocol Support
- Always uses RESP2 protocol (Jedis 4.x default)
- Various parameter classes for command options

## Choosing Between Jedis 4.x and 5.x Layers

### Use `jedis-4-compatibility` if:
- ✅ Your application uses Jedis 4.x (versions 4.0.x - 4.4.x)
- ✅ You use `JedisPooled` with `GenericObjectPoolConfig<Connection>`
- ✅ Your code doesn't use RedisProtocol APIs
- ✅ You want RESP2-only behavior (Jedis 4.x default)

### Use `jedis-compatibility` (5.x) if:
- ✅ Your application uses Jedis 5.x (versions 5.0.x - 5.2.x)
- ✅ You use `JedisPooled` with `GenericObjectPoolConfig<Object>`
- ✅ You want RESP3 protocol support
- ✅ Your code uses newer Jedis 5.x APIs

### Either works if:
- ✅ You only use `JedisPool` (uses `GenericObjectPoolConfig<Jedis>` in both)
- ✅ You use simple constructors without pool configuration
- ✅ You use `UnifiedJedis`, `JedisCluster`, or standalone `Jedis`

## Usage

### Gradle Dependency

```gradle
dependencies {
    implementation group: 'io.valkey', name: 'valkey-glide-jedis-4-compatibility', version: '2.1.0', classifier: 'osx-aarch_64'
}
```

### Maven Dependency

```xml
<dependency>
    <groupId>io.valkey</groupId>
    <artifactId>valkey-glide-jedis-4-compatibility</artifactId>
    <version>2.1.0</version>
    <classifier>osx-aarch_64</classifier>
</dependency>
```

### Basic Example with JedisPool

```java
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

// Jedis 4.x style - GenericObjectPoolConfig<Jedis>
GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
poolConfig.setMaxTotal(8);

try (JedisPool pool = new JedisPool(poolConfig, "localhost", 6379)) {
    try (Jedis jedis = pool.getResource()) {
        jedis.set("key", "value");
        String value = jedis.get("key");
        System.out.println(value); // prints: value
    }
}
```

### Example with JedisPooled

```java
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

// Jedis 4.x style - GenericObjectPoolConfig<Connection>
GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
poolConfig.setMaxTotal(8);

try (JedisPooled jedis = new JedisPooled(poolConfig, "localhost", 6379)) {
    jedis.set("key", "value");
    String value = jedis.get("key");
    System.out.println(value); // prints: value
}
```

### Example with UnifiedJedis

```java
import redis.clients.jedis.UnifiedJedis;

// Works the same in both Jedis 4.x and 5.x
try (UnifiedJedis jedis = new UnifiedJedis("localhost", 6379)) {
    jedis.set("key", "value");
    String value = jedis.get("key");
    System.out.println(value); // prints: value
}
```

## Migration from Jedis 4.x

1. **Replace your Jedis 4.x dependency** with `valkey-glide-jedis-4-compatibility`
2. **No code changes required** - The API is fully compatible with Jedis 4.x
3. **Benefit from GLIDE's performance** - Improved connection management, automatic failover, better error handling

## Key Differences from Jedis 5.x Layer

| Feature | jedis-4-compatibility | jedis-compatibility (5.x) |
|---------|----------------------|--------------------------|
| JedisPooled generic type | `GenericObjectPoolConfig<Connection>` | `GenericObjectPoolConfig<Object>` |
| RedisProtocol support | ❌ Not available | ✅ Available (`getRedisProtocol()`) |
| Default protocol | RESP2 only | RESP2 (default) or RESP3 |
| Protocol configuration | Not configurable | Configurable via `DefaultJedisClientConfig.builder().protocol()` |
| Target Jedis version | 4.0.x - 4.4.x | 5.0.x - 5.2.x |

## Build Commands

```bash
# Compile the compatibility layer
./gradlew :jedis-4-compatibility:compileJava

# Run tests
./gradlew :jedis-4-compatibility:test

# Build JAR
./gradlew :jedis-4-compatibility:jar

# Publish to local repository
./gradlew :jedis-4-compatibility:publishToMavenLocal
```

## Module Dependencies

```
jedis-4-compatibility
├── client (GLIDE core client)
│   ├── protobuf-java
│   ├── netty-handler
│   └── native libraries (Rust FFI)
└── commons-pool2 (connection pooling)
```

## Implementation Notes

- **Pool configuration is ignored**: GLIDE handles connection pooling internally, so Apache Commons Pool settings have no effect
- **RESP2 only**: The Jedis 4.x layer always uses RESP2 protocol (Jedis 4.x default behavior)
- **Connection type**: The `Connection` class is a compatibility shim; actual connection management is handled by GLIDE
- **No RedisProtocol API**: The `getRedisProtocol()` method and `RedisProtocol` enum are not present (Jedis 4.x behavior)

## Support and Compatibility

- **Jedis versions supported**: 4.0.x, 4.1.x, 4.2.x, 4.3.x, 4.4.x
- **Valkey/Redis versions**: All versions supported by GLIDE
- **Protocol**: RESP2 only
- **Deployment modes**: Standalone and Cluster

## Related Documentation

- [Jedis 5.x Compatibility Layer](../jedis-compatibility/README.md) - For Jedis 5.x applications
- [Migration Guide](../jedis-compatibility/compatibility-layer-migration-guide.md) - General migration guide from Jedis to GLIDE
- [GLIDE Documentation](../../README.md) - Main GLIDE documentation

## Troubleshooting

### Compilation Error: Cannot find symbol GenericObjectPoolConfig<Connection>

**Solution**: You're using the Jedis 5.x layer. Switch to `jedis-4-compatibility`:

```gradle
// Change from:
implementation 'io.valkey:valkey-glide-jedis-compatibility:2.1.0:osx-aarch_64'

// To:
implementation 'io.valkey:valkey-glide-jedis-4-compatibility:2.1.0:osx-aarch_64'
```

### Compilation Error: Cannot find symbol getRedisProtocol()

**Solution**: You're using the Jedis 5.x layer with Jedis 4.x code. Switch to `jedis-4-compatibility`.

### Need RESP3 support

**Solution**: Upgrade your application to Jedis 5.x and use the `jedis-compatibility` layer instead. RESP3 is not available in Jedis 4.x.

## License

Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
