package com.example.valkey;

import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.HostAndPort;
import java.util.HashSet;
import java.util.Set;

public class UnifiedJedisClient extends RedisClient {
    private UnifiedJedis unifiedJedis;
    
    public UnifiedJedisClient(TestConfiguration config) {
        super(config);
    }
    
    @Override
    public void connect() throws Exception {
        try {
            if (config.isClusterMode()) {
                // UnifiedJedis interface implemented by JedisCluster
                Set<HostAndPort> nodes = new HashSet<>();
                nodes.add(new HostAndPort(config.getRedisHost(), config.getRedisPort()));
                unifiedJedis = new JedisCluster(nodes);
            } else {
                // UnifiedJedis interface implemented by JedisPooled  
                unifiedJedis = new JedisPooled(config.getRedisHost(), config.getRedisPort());
            }
            
            // Test connection using UnifiedJedis interface
            String pingResult = unifiedJedis.ping();
            if (!"PONG".equals(pingResult)) {
                throw new Exception("Connection test failed - PING returned: " + pingResult);
            }
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
        try {
            // Using UnifiedJedis interface method
            String result = unifiedJedis.set(key, value);
            return "OK".equals(result);
        } catch (Exception e) {
            throw new Exception("SET operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String get(String key) throws Exception {
        try {
            // Using UnifiedJedis interface method
            return unifiedJedis.get(key);
        } catch (Exception e) {
            throw new Exception("GET operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getClientName() {
        String mode = config.isClusterMode() ? "Cluster" : "Standalone";
        return String.format("UnifiedJedis (%s)", mode);
    }
    
    @Override
    public boolean isConnected() {
        try {
            return unifiedJedis != null && "PONG".equals(unifiedJedis.ping());
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean ping() throws Exception {
        try {
            String result = unifiedJedis.ping();
            return "PONG".equals(result);
        } catch (Exception e) {
            throw new Exception("PING operation failed: " + e.getMessage(), e);
        }
    }
}
