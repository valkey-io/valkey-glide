# Valkey GLIDE Jedis Compatibility Layer

This subproject provides a compatibility layer that allows existing Jedis applications to work with Valkey GLIDE with minimal code changes.

## Overview

The Jedis compatibility layer implements the Jedis client interface using Valkey GLIDE as the underlying client. This enables:

- **Drop-in replacement**: Change only the dependency, keep existing code
- **Gradual migration**: Migrate incrementally from Jedis to native GLIDE APIs
- **Legacy support**: Support existing applications using Jedis

## Usage

### Gradle
```gradle
dependencies {
    implementation 'io.valkey:valkey-glide-jedis-compatibility:1.+'
}
```

### Maven
```xml
<dependency>
    <groupId>io.valkey</groupId>
    <artifactId>valkey-glide-jedis-compatibility</artifactId>
    <version>[1.0.0,)</version>
</dependency>
```

### Code Example
```java
import redis.clients.jedis.Jedis;

// Existing Jedis code works unchanged
Jedis jedis = new Jedis();
jedis.set("key", "value");
String value = jedis.get("key");
```

## Documentation

- [Migration Guide](src/main/java/redis/clients/jedis/compatibility-layer-migration-guide.md)
- [Demo Usage](src/main/java/redis/clients/jedis/demo.txt)

## Architecture

This compatibility layer:
- Implements Jedis interfaces using GLIDE client internally
- Provides `sendCommand()` support for Protocol.Command types
- Maps Jedis configuration to GLIDE configuration where possible
- Maintains compatibility with existing Jedis applications

## Limitations

See the [Migration Guide](src/main/java/redis/clients/jedis/compatibility-layer-migration-guide.md) for detailed information about supported features and limitations.
