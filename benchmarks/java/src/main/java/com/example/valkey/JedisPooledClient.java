package com.example.valkey;

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisException;

public class JedisPooledClient extends RedisClient {
    private JedisPooled jedisPooled;
    
    public JedisPooledClient(TestConfiguration config) {
        super(config);
    }
    
    @Override
    public void connect() throws Exception {
        try {
            if (config.isClusterMode()) {
                throw new Exception("JedisPooled is not supported in cluster mode");
            }
            
            // Use JedisPooled directly - it handles pooling internally in the compatibility layer
            jedisPooled = new JedisPooled(config.getRedisHost(), config.getRedisPort());
            
            // Test connection
            jedisPooled.ping();
        } catch (Exception e) {
            throw new Exception("Failed to connect using JedisPooled: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void close() throws Exception {
        if (jedisPooled != null) {
            jedisPooled.close();
        }
    }
    
    @Override
    public boolean set(String key, String value) throws Exception {
        try {
            String result = jedisPooled.set(key, value);
            return "OK".equals(result);
        } catch (JedisException e) {
            throw new Exception("SET operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String get(String key) throws Exception {
        try {
            return jedisPooled.get(key);
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
            return jedisPooled != null && "PONG".equals(jedisPooled.ping());
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean ping() throws Exception {
        try {
            String result = jedisPooled.ping();
            return "PONG".equals(result);
        } catch (JedisException e) {
            throw new Exception("PING operation failed: " + e.getMessage(), e);
        }
    }
}
