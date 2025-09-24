package com.example.valkey;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.exceptions.JedisException;
import java.util.HashSet;
import java.util.Set;

public class JedisClient extends RedisClient {
    private Jedis jedis;
    private JedisCluster jedisCluster;
    
    public JedisClient(TestConfiguration config) {
        super(config);
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
        // Simple direct Jedis connection
        jedis = new Jedis(config.getRedisHost(), config.getRedisPort());
        jedis.connect();
        
        // Test connection
        jedis.ping();
    }
    
    private void connectCluster() throws Exception {
        Set<HostAndPort> nodes = new HashSet<>();
        nodes.add(new HostAndPort(config.getRedisHost(), config.getRedisPort()));
        
        jedisCluster = new JedisCluster(nodes);
        
        // Test connection
        jedisCluster.ping();
    }
    
    @Override
    public void close() throws Exception {
        try {
            if (config.isClusterMode() && jedisCluster != null) {
                jedisCluster.close();
            } else if (jedis != null) {
                jedis.close();
            }
        } catch (Exception e) {
            // Log but don't throw on close
            System.err.println("Error closing Jedis connection: " + e.getMessage());
        }
    }
    
    @Override
    public boolean set(String key, String value) throws Exception {
        try {
            if (config.isClusterMode()) {
                String result = jedisCluster.set(key, value);
                return "OK".equals(result);
            } else {
                String result = jedis.set(key, value);
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
            } else {
                return jedis.get(key);
            }
        } catch (JedisException e) {
            throw new Exception("GET operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getClientName() {
        String mode = config.isClusterMode() ? "Cluster" : "Standalone";
        return String.format("Jedis (%s)", mode);
    }
    
    @Override
    public boolean isConnected() {
        try {
            if (config.isClusterMode()) {
                return jedisCluster != null;
            } else {
                return jedis != null;
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
            } else {
                String result = jedis.ping();
                return "PONG".equals(result);
            }
        } catch (JedisException e) {
            throw new Exception("PING operation failed: " + e.getMessage(), e);
        }
    }
}
