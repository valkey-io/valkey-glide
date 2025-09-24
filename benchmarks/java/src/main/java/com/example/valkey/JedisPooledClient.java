package com.example.valkey;

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisException;

public class JedisPooledClient extends RedisClient {
    private static volatile JedisPooled sharedJedisPooled;
    private static final Object lock = new Object();
    
    public JedisPooledClient(TestConfiguration config) {
        super(config);
    }
    
    @Override
    public void connect() throws Exception {
        try {
            if (config.isClusterMode()) {
                throw new Exception("JedisPooled is not supported in cluster mode");
            }
            
            // Create shared JedisPooled instance for all workers
            if (sharedJedisPooled == null) {
                synchronized (lock) {
                    if (sharedJedisPooled == null) {
                        sharedJedisPooled = new JedisPooled(config.getRedisHost(), config.getRedisPort());
                        
                        // Test connection
                        sharedJedisPooled.ping();
                    }
                }
            }
        } catch (Exception e) {
            throw new Exception("Failed to connect using JedisPooled: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void close() throws Exception {
        // Don't close the shared instance
    }
    
    @Override
    public boolean set(String key, String value) throws Exception {
        try {
            String result = sharedJedisPooled.set(key, value);
            return "OK".equals(result);
        } catch (JedisException e) {
            throw new Exception("SET operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String get(String key) throws Exception {
        try {
            return sharedJedisPooled.get(key);
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
            return sharedJedisPooled != null && "PONG".equals(sharedJedisPooled.ping());
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean ping() throws Exception {
        try {
            String result = sharedJedisPooled.ping();
            return "PONG".equals(result);
        } catch (JedisException e) {
            throw new Exception("PING operation failed: " + e.getMessage(), e);
        }
    }
}
