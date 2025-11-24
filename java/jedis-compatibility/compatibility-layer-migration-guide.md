# Valkey GLIDE Compatibility Layer Migration Guide

## Overview

The Valkey GLIDE compatibility layer enables seamless migration from Jedis to Valkey GLIDE with minimal code changes. This guide covers supported features, migration steps, and current limitations.

## Quick Migration

### Step 1: Update Dependencies

Replace your Jedis dependency with Valkey GLIDE:

**Before (Jedis):**
```gradle
dependencies {
    implementation 'redis.clients:jedis:5.1.5'
}
```

**After (Valkey GLIDE):**
```gradle
dependencies {
    implementation group: 'io.valkey', name: 'valkey-glide-jedis-compatibility', version: '2.+', classifier: 'osx-aarch_64'
}
```

### Step 2: No Code Changes Required

Your existing Jedis code works without modification:

```java
import redis.clients.jedis.Jedis;

public class JedisExample {
    public static void main(String[] args) {
        Jedis jedis = new Jedis();

        // Basic operations work unchanged
        String setResult = jedis.set("user:1001:name", "John Doe");
        String getValue = jedis.get("user:1001:name");
    }
}
```

### How to switch without a recompile?
Change the application's classpath such that it does not have the Jedis JAR and instead has Glide + the Jedis compatibility layer.

## Supported input parameters

### Configuration Mapping Overview

The compatibility layer supports the following Jedis configuration parameters:

#### Successfully Mapped
user, password, clientName, ssl, redisProtocol, connectionTimeoutMillis, socketTimeoutMillis, database

#### Partially Mapped
sslSocketFactory, sslParameters, hostnameVerifier

#### Not Mapped
blockingSocketTimeoutMillis

## Supported Features

### Core Commands
- ✅ Basic string operations (GET, SET, MGET, MSET)
- ✅ Hash operations (HGET, HSET, HMGET, HMSET)
- ✅ List operations (LPUSH, RPUSH, LPOP, RPOP)
- ⚠️ Set operations (SADD, SREM, SMEMBERS) - **Available via `sendCommand()` only**
- ⚠️ Sorted set operations (ZADD, ZREM, ZRANGE) - **Available via `sendCommand()` only**
- ✅ Key operations (DEL, EXISTS, EXPIRE, TTL)
- ✅ Connection commands (PING, SELECT)
- ✅ Generic commands via `sendCommand()` (Protocol.Command types only)

### Client Types
- ✅ Basic Jedis client
- ✅ Simple connection configurations
- ⚠️ JedisPool (limited support)
- ⚠️ JedisPooled (limited support)

### Configuration
- ✅ Host and port configuration
- ✅ Basic authentication
- ✅ Database selection
- ✅ Connection timeout
- ⚠️ SSL/TLS (partial support)

## Drawbacks and Unsupported Features

### Connection Management
- **JedisPool advanced configurations**: Complex pool settings not fully supported
- **JedisPooled**: Advanced pooled connection features unavailable
- **Connection pooling**: Native Jedis pooling mechanisms not implemented
- **Failover configurations**: Jedis-specific failover logic not supported

### Advanced Features
- **Transactions**: MULTI/EXEC transaction blocks not supported
- **Pipelining**: Jedis pipelining functionality unavailable
- **Pub/Sub**: Redis publish/subscribe not implemented
- **Lua scripting**: EVAL/EVALSHA commands not supported
- **Modules**: Redis module commands not available
- **Typed set/sorted set methods**: No dedicated methods like `sadd()`, `zadd()` - use `sendCommand()` instead

### Configuration Limitations
- **Complex SSL configurations**: Jedis `JedisClientConfig` SSL parameters cannot be mapped to Valkey GLIDE `GlideClientConfiguration`
- **Custom trust stores**: SSL trust store configurations require manual migration
- **Client certificates**: SSL client certificate authentication not supported in compatibility layer
- **SSL protocols and cipher suites**: Advanced SSL protocol settings cannot be automatically converted
- **Custom serializers**: Jedis serialization options not supported
- **Connection validation**: Jedis connection health checks unavailable
- **Retry mechanisms**: Jedis-specific retry logic not implemented

### Cluster Support
- ✅ **JedisCluster**: Basic cluster client supported via compatibility layer
- ✅ **Cluster operations**: Standard Redis commands work in cluster mode
- ✅ **Multiple node configuration**: Supports Set<HostAndPort> initialization
- ⚠️ **Advanced cluster features**: Some Jedis-specific cluster management features may have limitations

### Performance Features
- **Async operations**: Jedis async methods not implemented
- **Batch operations**: Bulk operation optimizations unavailable
- **Custom protocols**: Protocol customization not supported

## Migration Considerations

### Before Migration
1. **Audit your codebase** for unsupported features listed above
2. **Test thoroughly** in a development environment
3. **Review connection configurations** for compatibility
4. **Plan for feature gaps** that may require code changes

### Recommended Approach
1. Start with simple applications using basic commands
2. Gradually migrate complex features to native Valkey GLIDE APIs
3. Consider hybrid approach for applications with unsupported features
4. Monitor performance and behavior differences

### Alternative Migration Path
For applications heavily using unsupported features, consider migrating directly to native Valkey GLIDE APIs:

```java
import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;

GlideClientConfiguration config = GlideClientConfiguration.builder()
    .address(NodeAddress.builder().host("localhost").port(6379).build())
    .build();

try (GlideClient client = GlideClient.createClient(config).get()) {
    client.set(gs("key"), gs("value")).get();
}
```

## Getting Help

- Review the [main README](https://github.com/valkey-io/valkey-glide/blob/main/README.md) for native Valkey GLIDE usage
- Check [integration tests](./integTest/src/test/java/glide) for examples
- Report compatibility issues through the project's issue tracker

## Migration Warnings

### Before You Start
1. **Check your Jedis version**: Compatibility layer may not support older Jedis versions
2. **Verify command types**: `sendCommand()` only supports `Protocol.Command` types, not custom `ProtocolCommand` implementations
3. **Test thoroughly**: Classes may exist but lack implementation
4. **Expect runtime failures**: Successful compilation doesn't guarantee runtime success
5. **Review SSL/TLS configurations**: Advanced SSL settings require manual migration to native Valkey GLIDE APIs

### Recommended Testing Strategy
1. **Start with simple operations** to verify basic compatibility
2. **Test all code paths** - don't rely on successful compilation
3. **Monitor for runtime exceptions** from stub implementations
4. **Have rollback plan** ready for incompatible features

## Appendix: Detailed Configuration Mapping

###  Mapped Parameters
- `user` → `ServerCredentials.username`
- `password` → `ServerCredentials.password`
- `clientName` → `BaseClientConfiguration.clientName`
- `ssl` → `BaseClientConfiguration.useTLS`
- `redisProtocol` → `BaseClientConfiguration.protocol`
- `connectionTimeoutMillis` → `AdvancedBaseClientConfiguration.connectionTimeout`
- `socketTimeoutMillis` → `BaseClientConfiguration.requestTimeout`
- `database` → Handled via SELECT command after connection

### Partially Mapped Parameters
- `sslSocketFactory` → Requires SSL/TLS migration to system certificate store
- `sslParameters` → Limited mapping; custom protocols/ciphers not supported
- `hostnameVerifier` → Standard verification works; custom verifiers require `useInsecureTLS`

### Unsupported Parameters
- `blockingSocketTimeoutMillis` → No equivalent (GLIDE uses async I/O model)

### Key Migration Insights

1. **GLIDE Architecture Shift**: From application-managed SSL to system-managed SSL with secure defaults
2. **Certificate Management**: Custom keystores/truststores require migration to system certificate store
3. **Protocol Selection**: GLIDE auto-selects TLS 1.2+ and secure cipher suites
4. **Client Authentication**: Client certificates not supported; use username/password authentication
