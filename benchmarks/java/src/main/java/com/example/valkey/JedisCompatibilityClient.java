package com.example.valkey;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisCompatibilityClient extends RedisClient {
    private JedisPool jedisPool;
    private Jedis jedis;
    
    public JedisCompatibilityClient(TestConfiguration config) {
        super(config);
    }
    
    @Override
    public void connect() throws Exception {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(config.getConcurrentConnections() * 2);
            poolConfig.setMaxIdle(config.getConcurrentConnections());
            poolConfig.setMinIdle(5);
            
            jedisPool = new JedisPool(poolConfig, config.getRedisHost(), config.getRedisPort());
            jedis = jedisPool.getResource();
            jedis.ping();
        } catch (Exception e) {
            throw new Exception("Failed to connect using Jedis Compatibility Layer: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void close() throws Exception {
        if (jedis != null) {
            jedis.close();
        }
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
    
    @Override
    public boolean set(String key, String value) throws Exception {
        try (Jedis resource = jedisPool.getResource()) {
            return "OK".equals(resource.set(key, value));
        }
    }
    
    @Override
    public String get(String key) throws Exception {
        try (Jedis resource = jedisPool.getResource()) {
            return resource.get(key);
        }
    }
    
    @Override
    public String getClientName() {
        return "Jedis Compatibility Layer";
    }
    
    @Override
    public boolean isConnected() {
        return jedisPool != null && !jedisPool.isClosed();
    }
    
    @Override
    public boolean ping() throws Exception {
        try (Jedis resource = jedisPool.getResource()) {
            return "PONG".equals(resource.ping());
        }
    }
}
