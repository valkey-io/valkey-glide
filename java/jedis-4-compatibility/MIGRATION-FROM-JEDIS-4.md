# Migration Guide: Jedis 4.x to GLIDE with Jedis 4.x Compatibility Layer

This guide helps you migrate existing Jedis 4.x applications to Valkey GLIDE using the Jedis 4.x compatibility layer.

## Overview

The `jedis-4-compatibility` layer provides a drop-in replacement for Jedis 4.x, allowing you to benefit from GLIDE's performance and features without changing your application code.

## Quick Start

### Step 1: Update Dependencies

**Gradle:**

```gradle
dependencies {
    // Remove or comment out old Jedis dependency
    // implementation 'redis.clients:jedis:4.4.8'
    
    // Add GLIDE Jedis 4.x compatibility layer
    implementation group: 'io.valkey', name: 'valkey-glide-jedis-4-compatibility', version: '2.1.0', classifier: 'osx-aarch_64'
}
```

**Maven:**

```xml
<dependencies>
    <!-- Remove or comment out old Jedis dependency -->
    <!-- <dependency>
        <groupId>redis.clients</groupId>
        <artifactId>jedis</artifactId>
        <version>4.4.8</version>
    </dependency> -->
    
    <!-- Add GLIDE Jedis 4.x compatibility layer -->
    <dependency>
        <groupId>io.valkey</groupId>
        <artifactId>valkey-glide-jedis-4-compatibility</artifactId>
        <version>2.1.0</version>
        <classifier>osx-aarch_64</classifier>
    </dependency>
</dependencies>
```

**Platform Classifiers:**
- macOS ARM64: `osx-aarch_64`
- macOS x86_64: `osx-x86_64`
- Linux ARM64: `linux-aarch_64`
- Linux x86_64: `linux-x86_64`
- Windows x86_64: `win32-x86-64`

### Step 2: Rebuild Your Application

```bash
# Gradle
./gradlew clean build

# Maven
mvn clean package
```

### Step 3: Test Your Application

Run your existing test suite. The Jedis 4.x compatibility layer maintains full API compatibility, so your tests should pass without modification.

## Common Migration Scenarios

### Scenario 1: JedisPool with Generic Configuration

**Jedis 4.x Code:**
```java
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
poolConfig.setMaxTotal(16);
poolConfig.setMaxIdle(8);
poolConfig.setMinIdle(2);

JedisPool pool = new JedisPool(poolConfig, "localhost", 6379, 2000, "password");

try (Jedis jedis = pool.getResource()) {
    jedis.set("key", "value");
    String value = jedis.get("key");
}
```

**With GLIDE (No Changes Required):**
```java
// Same code works unchanged!
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
poolConfig.setMaxTotal(16);
poolConfig.setMaxIdle(8);
poolConfig.setMinIdle(2);

// Pool config values are ignored by GLIDE (it manages pooling internally)
JedisPool pool = new JedisPool(poolConfig, "localhost", 6379, 2000, "password");

try (Jedis jedis = pool.getResource()) {
    jedis.set("key", "value");
    String value = jedis.get("key");
}
```

### Scenario 2: JedisPooled (Jedis 4.x Style)

**Jedis 4.x Code:**
```java
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
poolConfig.setMaxTotal(16);

JedisPooled jedis = new JedisPooled(poolConfig, "localhost", 6379, 2000, "password");

jedis.set("key", "value");
String value = jedis.get("key");
```

**With GLIDE (No Changes Required):**
```java
// Same code works unchanged!
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
poolConfig.setMaxTotal(16);

JedisPooled jedis = new JedisPooled(poolConfig, "localhost", 6379, 2000, "password");

jedis.set("key", "value");
String value = jedis.get("key");
```

### Scenario 3: UnifiedJedis

**Jedis 4.x Code:**
```java
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;

JedisClientConfig config = DefaultJedisClientConfig.builder()
    .connectionTimeoutMillis(2000)
    .socketTimeoutMillis(2000)
    .user("myuser")
    .password("mypassword")
    .database(0)
    .build();

UnifiedJedis jedis = new UnifiedJedis("localhost", 6379);

jedis.set("key", "value");
String value = jedis.get("key");
```

**With GLIDE (No Changes Required):**
```java
// Same code works unchanged!
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;

JedisClientConfig config = DefaultJedisClientConfig.builder()
    .connectionTimeoutMillis(2000)
    .socketTimeoutMillis(2000)
    .user("myuser")
    .password("mypassword")
    .database(0)
    .build();

UnifiedJedis jedis = new UnifiedJedis("localhost", 6379);

jedis.set("key", "value");
String value = jedis.get("key");
```

### Scenario 4: Cluster Mode

**Jedis 4.x Code:**
```java
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.HostAndPort;
import java.util.HashSet;
import java.util.Set;

Set<HostAndPort> nodes = new HashSet<>();
nodes.add(new HostAndPort("localhost", 7000));
nodes.add(new HostAndPort("localhost", 7001));
nodes.add(new HostAndPort("localhost", 7002));

JedisCluster jedisCluster = new JedisCluster(nodes, 2000, 2000, 5, "password", "default");

jedisCluster.set("key", "value");
String value = jedisCluster.get("key");
```

**With GLIDE (No Changes Required):**
```java
// Same code works unchanged!
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.HostAndPort;
import java.util.HashSet;
import java.util.Set;

Set<HostAndPort> nodes = new HashSet<>();
nodes.add(new HostAndPort("localhost", 7000));
nodes.add(new HostAndPort("localhost", 7001));
nodes.add(new HostAndPort("localhost", 7002));

JedisCluster jedisCluster = new JedisCluster(nodes, 2000, 2000, 5, "password", "default");

jedisCluster.set("key", "value");
String value = jedisCluster.get("key");
```

## Important Differences

### Connection Pooling

**Jedis 4.x:**
- Uses Apache Commons Pool 2 for connection pooling
- Pool configuration controls min/max connections, eviction policy, etc.

**GLIDE Compatibility Layer:**
- GLIDE manages connection pooling internally
- Pool configuration parameters are **ignored** (but accepted for API compatibility)
- GLIDE's internal pooling is optimized for performance and reliability

**Impact:** Your pool configuration won't affect GLIDE's behavior, but this is usually fine as GLIDE's defaults are well-tuned.

### Protocol Version

**Jedis 4.x:**
- Always uses RESP2 protocol
- No configuration option for protocol version

**GLIDE Compatibility Layer:**
- Always uses RESP2 protocol (matching Jedis 4.x behavior)
- For RESP3 support, use the Jedis 5.x compatibility layer and upgrade your app to Jedis 5.x

### Performance Improvements

After migration, you should see:
- **Lower latency** for most operations
- **Better connection management** with automatic reconnection
- **Improved cluster failover** handling
- **Reduced memory overhead** from connection pooling

## Testing Your Migration

### Unit Tests

Your existing Jedis 4.x unit tests should work unchanged:

```java
@Test
public void testRedisOperations() {
    try (JedisPool pool = new JedisPool("localhost", 6379)) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set("test-key", "test-value");
            assertEquals("test-value", jedis.get("test-key"));
        }
    }
}
```

### Integration Tests

Run your integration tests against both:
1. **Redis/Valkey with Jedis 4.x** (original)
2. **Redis/Valkey with GLIDE compatibility layer** (new)

Both should produce identical results.

### Performance Testing

Compare performance before and after:

```java
// Benchmark example
long startTime = System.nanoTime();
for (int i = 0; i < 10000; i++) {
    jedis.set("key" + i, "value" + i);
}
long endTime = System.nanoTime();
System.out.println("Duration: " + (endTime - startTime) / 1_000_000 + " ms");
```

Expected results: GLIDE should be as fast or faster than native Jedis 4.x.

## Rollback Plan

If you encounter issues, rolling back is simple:

**Gradle:**
```gradle
dependencies {
    // Comment out GLIDE
    // implementation 'io.valkey:valkey-glide-jedis-4-compatibility:2.1.0:osx-aarch_64'
    
    // Re-enable Jedis 4.x
    implementation 'redis.clients:jedis:4.4.8'
}
```

Rebuild and redeploy your application.

## Advanced Topics

### Custom Connection Factories

Jedis 4.x allows custom `JedisSocketFactory` implementations. GLIDE compatibility layer accepts these but doesn't use them (GLIDE manages connections internally).

**If you need custom connection behavior**, you may need to:
1. Use GLIDE's native APIs instead of the compatibility layer
2. Configure GLIDE through its native configuration APIs

### Pipelining and Transactions

Pipelining and transactions work the same in the compatibility layer:

```java
try (Jedis jedis = pool.getResource()) {
    Transaction t = jedis.multi();
    t.set("key1", "value1");
    t.set("key2", "value2");
    t.exec();
}
```

### SSL/TLS Configuration

SSL configuration is supported:

```java
JedisClientConfig config = DefaultJedisClientConfig.builder()
    .ssl(true)
    .sslSocketFactory(sslSocketFactory)
    .sslParameters(sslParameters)
    .hostnameVerifier(hostnameVerifier)
    .build();
```

GLIDE maps these settings to its internal SSL configuration.

## Troubleshooting

### Issue: Compilation errors about `GenericObjectPoolConfig<Connection>`

**Symptom:**
```
error: cannot find symbol GenericObjectPoolConfig<Connection>
```

**Solution:** Make sure you're using `jedis-4-compatibility`, not `jedis-compatibility` (5.x).

### Issue: Tests fail with connection errors

**Symptom:** Tests that worked with Jedis 4.x fail with connection errors.

**Solution:** 
- Check that your Redis/Valkey server is running and accessible
- Verify connection parameters (host, port, password)
- Review GLIDE logs for connection errors

### Issue: Performance degradation

**Symptom:** Operations are slower than with native Jedis 4.x.

**Solution:**
- Check network configuration (latency, bandwidth)
- Review GLIDE configuration (timeout settings)
- Ensure you're comparing like-for-like (same Redis version, same workload)
- Open an issue on GitHub with performance metrics

## Getting Help

- **Documentation**: [README.md](./README.md)
- **Examples**: [../examples/](../examples/)
- **Issues**: [GitHub Issues](https://github.com/valkey-io/valkey-glide/issues)
- **Discussions**: [GitHub Discussions](https://github.com/valkey-io/valkey-glide/discussions)

## Next Steps

After successful migration:

1. **Monitor production performance** for 1-2 weeks
2. **Review GLIDE's native APIs** for potential optimizations
3. **Consider upgrading to Jedis 5.x compatibility layer** for RESP3 support (optional)
4. **Share your experience** in GitHub Discussions

## FAQ

**Q: Do I need to change any code?**  
A: No, the Jedis 4.x compatibility layer is a drop-in replacement.

**Q: Will my pool configuration still work?**  
A: The API accepts pool configuration, but GLIDE manages pooling internally. Your settings won't affect GLIDE's behavior.

**Q: Can I use RESP3?**  
A: Not with the Jedis 4.x layer (Jedis 4.x doesn't support RESP3). Upgrade to Jedis 5.x and use `jedis-compatibility` for RESP3.

**Q: Is cluster mode supported?**  
A: Yes, `JedisCluster` is fully supported.

**Q: Can I mix GLIDE native APIs with Jedis APIs?**  
A: The compatibility layer wraps GLIDE's native client, but accessing GLIDE APIs directly from the compatibility layer is not recommended. Choose one or the other.

**Q: What about Jedis modules (RedisJSON, RedisBloom, etc.)?**  
A: Basic module support is available through the compatibility layer. For advanced module features, consider using GLIDE's native APIs.

## License

Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
