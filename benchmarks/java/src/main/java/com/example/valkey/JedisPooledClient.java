package com.example.valkey;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class JedisPooledClient extends RedisClient {
    private JedisPool jedisPool;
    
    public JedisPooledClient(TestConfiguration config) {
        super(config);
    }
    
    @Override
    public void connect() throws Exception {
        try {
            if (config.isClusterMode()) {
                throw new Exception("JedisPooled is not supported in cluster mode");
            }
            
            // Simple JedisPool without complex configuration for compatibility layer
            jedisPool = new JedisPool(config.getRedisHost(), config.getRedisPort());
            
            // Test connection
            try (Jedis testJedis = jedisPool.getResource()) {
                testJedis.ping();
            }
        } catch (Exception e) {
            throw new Exception("Failed to connect using JedisPool: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void close() throws Exception {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
    
    @Override
    public boolean set(String key, String value) throws Exception {
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.set(key, value);
            return "OK".equals(result);
        } catch (JedisException e) {
            throw new Exception("SET operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String get(String key) throws Exception {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (JedisException e) {
            throw new Exception("GET operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getClientName() {
        return "JedisPooled";
    }
    
    @Override
    public boolean isConnected() {
        try {
            return jedisPool != null && !jedisPool.isClosed();
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean ping() throws Exception {
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.ping();
            return "PONG".equals(result);
        } catch (JedisException e) {
            throw new Exception("PING operation failed: " + e.getMessage(), e);
        }
    }
}
