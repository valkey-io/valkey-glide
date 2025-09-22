package com.example.valkey;

import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.ConnectionPoolConfig;

public class UnifiedJedisClient extends RedisClient {
    private UnifiedJedis unifiedJedis;
    
    public UnifiedJedisClient(TestConfiguration config) {
        super(config);
    }
    
    @Override
    public void connect() throws Exception {
        try {
            ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
            poolConfig.setMaxTotal(config.getConcurrentConnections() * 2);
            poolConfig.setMaxIdle(config.getConcurrentConnections());
            poolConfig.setMinIdle(5);
            
            unifiedJedis = new JedisPooled(poolConfig, config.getRedisHost(), config.getRedisPort());
            unifiedJedis.ping();
        } catch (Exception e) {
            throw new Exception("Failed to connect using UnifiedJedis: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void close() throws Exception {
        if (unifiedJedis != null) {
            unifiedJedis.close();
        }
    }
    
    @Override
    public boolean set(String key, String value) throws Exception {
        return "OK".equals(unifiedJedis.set(key, value));
    }
    
    @Override
    public String get(String key) throws Exception {
        return unifiedJedis.get(key);
    }
    
    @Override
    public String getClientName() {
        return "UnifiedJedis";
    }
    
    @Override
    public boolean isConnected() {
        return unifiedJedis != null;
    }
    
    @Override
    public boolean ping() throws Exception {
        return "PONG".equals(unifiedJedis.ping());
    }
}
