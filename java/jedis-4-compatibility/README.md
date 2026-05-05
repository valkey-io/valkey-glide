# Jedis 4.x Compatibility Layer

This sub-module provides a Jedis 4.x-compatible API layer for Valkey GLIDE, allowing existing Jedis 4.x applications to migrate to GLIDE with minimal code changes.

## Why a Separate Jedis 4.x Layer?

This artifact exists because **upstream Jedis 5** changed several public types (notably `JedisPooled` pool generics and optional RESP3 / `RedisProtocol` APIs). Applications that still compile against **Jedis 4.x** signatures need this module; they are not choosing between “Jedis 4” and “Jedis 5” in one classpath—they pick **one** GLIDE compatibility artifact that matches their existing API.

| Topic | Jedis 4.x–style apps | Notes for this module |
|-------|----------------------|------------------------|
| `JedisPooled` pool type | `GenericObjectPoolConfig<Connection>` | Supported here; matches Jedis 4 compile-time types |
| `JedisPool` | `GenericObjectPoolConfig<Jedis>` | Same as typical Jedis 4 usage |
| Protocol | RESP2-only surface | Matches Jedis 4 defaults; no `getRedisProtocol()` on this layer |

If you are already on **Jedis 5.x** APIs, use the separate **`jedis-compatibility`** module instead.

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

## Choosing a compatibility artifact

### Use `jedis-4-compatibility` when:
- The application was built against **Jedis 4.x** (e.g. 4.0.x–4.4.x), especially `JedisPooled` with `GenericObjectPoolConfig<Connection>`.
- You do not need Jedis 5–only configuration such as `getRedisProtocol()` / RESP3 toggles on the Jedis config surface.

### Use `jedis-compatibility` (5.x-oriented) when:
- The codebase already targets **Jedis 5.x** types and APIs (`GenericObjectPoolConfig<Object>` on `JedisPooled`, RESP3 options, etc.).

### Either module may fit when:
- You only use `JedisPool`, `UnifiedJedis`, `JedisCluster`, or standalone `Jedis` with simple constructors—pick the artifact that matches your **existing** dependency’s major API shape.

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
| RedisProtocol / `getRedisProtocol()` | Not in this layer | Available in `jedis-compatibility` |
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

## Contributor review checklist

Use UTF-8 explicitly (`StandardCharsets.UTF_8`) for `String`/`byte[]` conversions in Jedis-style APIs unless returning raw bytes from `GlideString`. Avoid decorative Unicode in comments. Cluster `SCAN` must keep `ClusterScanCursor` state across Jedis cursor tokens (registry + `WeakReference`, release handles per GLIDE docs). `MigrateParams` must not emit duplicate `COPY`/`REPLACE` or overlapping `AUTH`/`AUTH2`. Unsupported pool/factory constructors should throw `UnsupportedOperationException` with guidance, not silently use `localhost`. Use `java.util.logging` (or the project logger), not `System.err`, for library warnings. For shared maintainer guidance in Cursor, mirror these points in a local rule under `.cursor/rules/` (that directory is often gitignored). Release workflow: `create-uber-jar` publishes **no-classifier** artifacts for both `jedis-compatibility` and `jedis-4-compatibility` when `JEDIS_NO_CLASSIFIER_BUILD=true` (see `java-cd.yml`).

## License

Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
