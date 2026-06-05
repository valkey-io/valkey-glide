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
- ✅ Basic string operations (GET, SET, MGET, MSET, MSETNX, SETRANGE, GETRANGE, LCS, LCSLEN, LCSIDX)
- ✅ Hash operations (HGET, HSET, HMGET, HMSET, HRANDFIELD with count and values)
- ✅ List operations (LPUSH, RPUSH, LPOP with count, RPOP with count, LPOS with count, LMPOP, BLMPOP)
- ✅ Set operations (SADD, SREM, SMEMBERS, SCARD, SISMEMBER, SMISMEMBER, SPOP with count, SRANDMEMBER with count, SMOVE, SINTER, SINTERCARD, SINTERSTORE, SUNION, SUNIONSTORE, SDIFF, SDIFFSTORE, SSCAN) via type-safe methods
- ⚠️ Sorted set operations (ZADD, ZREM, ZRANGE) - **Available via `sendCommand()` only**
- ✅ Stream operations (XADD, XLEN, XDEL, XRANGE, XREVRANGE, XREAD, XTRIM, XGROUP CREATE/DESTROY/SETID, XREADGROUP, XACK, XPENDING, XCLAIM, XAUTOCLAIM, XINFO STREAM/GROUPS/CONSUMERS)
  - **Note:** `XSETID` is not supported (only `XGROUP SETID` is available)
- ✅ Key operations (DEL, EXISTS, EXPIRE, TTL)
- ✅ Sort operations (SORT_RO, SORT with STORE)
- ✅ Wait operations (WAIT, WAITAOF)
- ✅ Object introspection (OBJECT ENCODING, OBJECT FREQ, OBJECT IDLETIME, OBJECT REFCOUNT)
- ✅ Geospatial operations (GEOADD, GEOPOS, GEODIST, GEOHASH, GEOSEARCH, GEOSEARCHSTORE)
- ✅ Pub/Sub (PUBLISH, PUBSUB CHANNELS, PUBSUB NUMSUB, PUBSUB NUMPAT). `publish()` returns `0` instead of the actual subscriber count due to GLIDE API limitations (see [issue #5354](https://github.com/valkey-io/valkey-glide/issues/5354)). For subscriptions and message delivery, use the GLIDE Java client directly (`GlideClient` / `GlideClusterClient`): `subscribe`, `psubscribe`, `ssubscribe`, and the matching `unsubscribe` / `punsubscribe` / `sunsubscribe` methods declared on `glide.api.commands.PubSubBaseCommands` and `glide.api.commands.PubSubClusterCommands`.
- ✅ Connection commands (PING, ECHO, SELECT, CLIENT ID, CLIENT GETNAME)
- ✅ Server management commands (INFO, CONFIG GET/SET/REWRITE/RESETSTAT, DBSIZE, FLUSHDB, FLUSHALL, TIME, LASTSAVE, LOLWUT)
- ✅ ACL commands (ACL LIST, ACL GETUSER, ACL SETUSER, ACL DELUSER, ACL CAT, ACL GENPASS, ACL LOG, ACL LOG RESET, ACL WHOAMI, ACL USERS, ACL SAVE, ACL LOAD, ACL DRYRUN)
- ✅ Transaction commands (WATCH, UNWATCH, MULTI, EXEC, DISCARD) - **See transaction limitations below**
- ✅ Scripting commands (EVAL, EVALSHA, SCRIPT LOAD, SCRIPT EXISTS, SCRIPT FLUSH, SCRIPT KILL, SCRIPT DEBUG)
- ✅ Function commands (FCALL, FUNCTION LOAD, FUNCTION LIST, FUNCTION DELETE, FUNCTION FLUSH, FUNCTION DUMP, FUNCTION RESTORE, FUNCTION STATS)
- ✅ Custom commands via `customCommand()` for any Valkey command
- ✅ Generic commands via `sendCommand()` (Protocol.Command types only)

### Client Types
- ✅ Basic Jedis client
- ✅ Simple connection configurations
- ✅ **JedisPool** (standalone): Core Jedis-style pooling — `getResource()`, try-with-resources / `close()`, `Pool#returnBrokenResource`, broken vs healthy return, `GenericObjectPoolConfig` honored via Commons Pool 2. Each pooled `Jedis` uses its own GLIDE `GlideClient` (not a shared TCP connection like classic Jedis).
- ⚠️ **JedisPooled** (limited support): API compatibility; internal GLIDE connection management differs from upstream Jedis.

### Configuration
- ✅ Host and port configuration
- ✅ Basic authentication
- ✅ Database selection
- ✅ Connection timeout
- ⚠️ SSL/TLS (partial support)

## Drawbacks and Unsupported Features

### Connection Management
- **JedisSentinelPool / Sentinel**: Not provided by this compatibility layer (use GLIDE topology configuration outside Jedis APIs if needed).
- **JedisPooled**: Advanced pooled-connection features from upstream Jedis may differ or be no-ops where GLIDE owns connection lifecycle.
- **Architecture note**: Standalone `JedisPool` maps each pool slot to one `GlideClient`; this matches Jedis call patterns but not necessarily the same resource usage as socket-per-connection Jedis.
- **Failover configurations**: Jedis-specific failover logic not supported

### Advanced Features
- **Pub/Sub with JedisPubSub callbacks**: Jedis-style `JedisPubSub` callback listeners are not supported (see [issue #5469](https://github.com/valkey-io/valkey-glide/issues/5469) for planned support). For message handling, use `GlideClient` / `GlideClusterClient` with the Pub/Sub command APIs in `PubSubBaseCommands` / `PubSubClusterCommands` and the client's documented subscription and message-delivery options (callbacks or queued messages). The compatibility layer provides `publish()` and `pubsub*()` introspection commands only.
- ⚠️ **Transactions**: Basic MULTI/EXEC/DISCARD/WATCH/UNWATCH supported, but with limitations:
  - After `multi()`, you must use the returned **Transaction** object to queue commands (e.g. `t.set()`, `t.get()`). Calling `jedis.set()` or other Jedis methods directly does **not** queue to the transaction.
  - For commands not yet exposed on `Transaction`, or for pipeline (non-atomic) batching, use the native GLIDE Batch API with the same connection.
  - `watch()` and `unwatch()` work as expected for conditional execution.

  **Transaction usage (compat layer):**

  ```java
  Transaction t = jedis.multi();
  Response<String> r1 = t.set("key", "value");
  Response<String> r2 = t.get("key");
  List<Object> results = t.exec();
  String value = r2.get(); // retrieve after exec()
  ```

  **Native GLIDE Batch API** (for commands not on Transaction, or non-atomic pipeline; use a `GlideClient` instance):

  ```java
  Batch batch = new Batch(true)  // true = atomic transaction
      .set("key1", "value1")
      .get("key1");
  Object[] results = glideClient.exec(batch, false).get();
  ```

- **Pipelining**: Jedis pipelining functionality unavailable (use GLIDE Batch API instead)
- **Pub/Sub**: Redis publish/subscribe not implemented
- ✅ **Lua scripting**: Full support for EVAL/EVALSHA, SCRIPT management (LOAD, EXISTS, FLUSH, KILL, DEBUG), and Valkey Functions (FCALL/FUNCTION *)
- **Modules**: Redis module commands not available

### Configuration Limitations
- **Complex SSL configurations**: Jedis `JedisClientConfig` SSL parameters cannot be mapped to Valkey GLIDE `GlideClientConfiguration`
- **Custom trust stores**: SSL trust store configurations require manual migration
- **Client certificates**: SSL client certificate authentication not supported in compatibility layer
- **SSL protocols and cipher suites**: Advanced SSL protocol settings cannot be automatically converted
- **Custom serializers**: Jedis serialization options not supported
- **Connection validation**: When Commons Pool validation is enabled (e.g. `testOnBorrow`), `GlideJedisFactory#validateObject` uses `PING`; tune pool config as needed.
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
6. **Transaction behavior differs**: Commands after `multi()` are NOT automatically queued in the compatibility layer - use GLIDE Batch API for full transaction support

### Recommended Testing Strategy
1. **Start with simple operations** to verify basic compatibility
2. **Test all code paths** - don't rely on successful compilation
3. **Monitor for runtime exceptions** from stub implementations
4. **Have rollback plan** ready for incompatible features
5. **Test transaction code carefully** - behavior differs from standard Jedis

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
