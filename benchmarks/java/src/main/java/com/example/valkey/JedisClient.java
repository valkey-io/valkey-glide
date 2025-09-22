package com.example.valkey;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.exceptions.JedisException;
import java.util.HashSet;
import java.util.Set;

public class JedisClient extends RedisClient {
    private JedisPool jedisPool;
    private ThreadLocal<Jedis> threadLocalJedis;
    private JedisCluster jedisCluster;
    private final boolean usePool;
    
    public JedisClient(TestConfiguration config, boolean usePool) {
        super(config);
        this.usePool = usePool;
    }
    
    public JedisClient(TestConfiguration config) {
        this(config, false); // Default to single connection per thread
    }
    
    @Override
    public void connect() throws Exception {
        try {
            if (config.isClusterMode()) {
                connectCluster();
            } else {
                connectStandalone();
            }
        } catch (Exception e) {
            throw new Exception("Failed to connect to Redis: " + e.getMessage(), e);
        }
    }
    
    private void connectStandalone() throws Exception {
        if (usePool) {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(config.getConcurrentConnections() * 2);
            poolConfig.setMaxIdle(config.getConcurrentConnections());
            poolConfig.setMinIdle(5);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);
            
            if (config.isTlsEnabled()) {
                jedisPool = new JedisPool(poolConfig, config.getRedisHost(), config.getRedisPort(), true);
            } else {
                jedisPool = new JedisPool(poolConfig, config.getRedisHost(), config.getRedisPort());
            }
            
            // Test the pool
            try (Jedis testJedis = jedisPool.getResource()) {
                testJedis.ping();
            }
        } else {
            // Initialize ThreadLocal for per-thread connections
            threadLocalJedis = ThreadLocal.withInitial(() -> {
                try {
                    Jedis jedis = config.isTlsEnabled() ? 
                        new Jedis(config.getRedisHost(), config.getRedisPort(), true) :
                        new Jedis(config.getRedisHost(), config.getRedisPort());
                    jedis.connect();
                    return jedis;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create thread-local Jedis connection", e);
                }
            });
            
            // Test connection
            threadLocalJedis.get().ping();
        }
    }
    
    private void connectCluster() throws Exception {
        Set<HostAndPort> clusterNodes = new HashSet<>();
        clusterNodes.add(new HostAndPort(config.getRedisHost(), config.getRedisPort()));
        
        if (config.isTlsEnabled()) {
            // Configure TLS for cluster connections with proper timeouts for AWS ElastiCache
            redis.clients.jedis.DefaultJedisClientConfig.Builder configBuilder = 
                redis.clients.jedis.DefaultJedisClientConfig.builder()
                    .ssl(true)
                    .socketTimeoutMillis(10000)
                    .connectionTimeoutMillis(10000);
            
            configBuilder.sslSocketFactory((javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault());
            
            redis.clients.jedis.DefaultJedisClientConfig clientConfig = configBuilder.build();
            jedisCluster = new JedisCluster(clusterNodes, clientConfig);
        } else {
            // Non-TLS cluster configuration with timeouts
            redis.clients.jedis.DefaultJedisClientConfig clientConfig = 
                redis.clients.jedis.DefaultJedisClientConfig.builder()
                    .socketTimeoutMillis(10000)
                    .connectionTimeoutMillis(10000)
                    .build();
            
            jedisCluster = new JedisCluster(clusterNodes, clientConfig);
        }
        
        // Test the cluster connection
        jedisCluster.ping();
    }
    
    @Override
    public void close() throws Exception {
        try {
            if (config.isClusterMode() && jedisCluster != null) {
                jedisCluster.close();
            } else if (usePool && jedisPool != null) {
                jedisPool.close();
            } else if (threadLocalJedis != null) {
                threadLocalJedis.remove(); // Clean up ThreadLocal
            }
        } catch (Exception e) {
            throw new Exception("Failed to close Jedis connection: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean set(String key, String value) throws Exception {
        try {
            if (config.isClusterMode()) {
                String result = jedisCluster.set(key, value);
                return "OK".equals(result);
            } else if (usePool) {
                try (Jedis pooledJedis = jedisPool.getResource()) {
                    String result = pooledJedis.set(key, value);
                    return "OK".equals(result);
                }
            } else {
                String result = threadLocalJedis.get().set(key, value);
                return "OK".equals(result);
            }
        } catch (JedisException e) {
            throw new Exception("SET operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String get(String key) throws Exception {
        try {
            if (config.isClusterMode()) {
                return jedisCluster.get(key);
            } else if (usePool) {
                try (Jedis pooledJedis = jedisPool.getResource()) {
                    return pooledJedis.get(key);
                }
            } else {
                return threadLocalJedis.get().get(key);
            }
        } catch (JedisException e) {
            throw new Exception("GET operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getClientName() {
        String mode = config.isClusterMode() ? "Cluster" : "Standalone";
        String pool = usePool ? "Pooled" : "Direct";
        String tls = config.isTlsEnabled() ? "TLS" : "Plain";
        return String.format("Jedis (%s, %s, %s)", mode, pool, tls);
    }
    
    @Override
    public boolean isConnected() {
        try {
            if (config.isClusterMode()) {
                return jedisCluster != null;
            } else if (usePool) {
                return jedisPool != null && !jedisPool.isClosed();
            } else {
                return threadLocalJedis != null && threadLocalJedis.get() != null && threadLocalJedis.get().isConnected();
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean ping() throws Exception {
        try {
            if (config.isClusterMode()) {
                String result = jedisCluster.ping();
                return "PONG".equals(result);
            } else if (usePool) {
                try (Jedis pooledJedis = jedisPool.getResource()) {
                    String result = pooledJedis.ping();
                    return "PONG".equals(result);
                }
            } else {
                String result = threadLocalJedis.get().ping();
                return "PONG".equals(result);
            }
        } catch (JedisException e) {
            throw new Exception("PING failed: " + e.getMessage(), e);
        }
    }
}
