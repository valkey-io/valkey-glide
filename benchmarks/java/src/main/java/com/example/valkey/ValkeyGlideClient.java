package com.example.valkey;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ValkeyGlideClient extends RedisClient {
    private GlideClient glideClient;
    private GlideClusterClient glideClusterClient;
    
    public ValkeyGlideClient(TestConfiguration config) {
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
            
            // Test the connection
            ping();
        } catch (Exception e) {
            throw new Exception("Failed to connect to Redis with Valkey-Glide: " + e.getMessage(), e);
        }
    }
    
    private void connectStandalone() throws Exception {
        GlideClientConfiguration clientConfig = GlideClientConfiguration.builder()
                .address(NodeAddress.builder()
                    .host(config.getRedisHost())
                    .port(config.getRedisPort())
                    .build())
                .useTLS(config.isTlsEnabled())
                .requestTimeout(1000) // 1 second request timeout
                .build();
        
        CompletableFuture<GlideClient> clientFuture = GlideClient.createClient(clientConfig);
        glideClient = clientFuture.get(2, TimeUnit.SECONDS); // 2 second connection timeout
    }
    
    private void connectCluster() throws Exception {
        GlideClusterClientConfiguration glideClusterClientConfig = GlideClusterClientConfiguration.builder()
                .address(NodeAddress.builder()
                    .host(config.getRedisHost())
                    .port(config.getRedisPort())
                    .build())
                .useTLS(config.isTlsEnabled())
                .requestTimeout(1000) // 1 second request timeout
                .build();
        
        glideClusterClient = GlideClusterClient.createClient(glideClusterClientConfig).get(2, TimeUnit.SECONDS);
    }
    
    @Override
    public void close() throws Exception {
        try {
            if (config.isClusterMode() && glideClusterClient != null) {
                glideClusterClient.close();
            } else if (glideClient != null) {
                glideClient.close();
            }
        } catch (Exception e) {
            throw new Exception("Failed to close Valkey-Glide connection: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean set(String key, String value) throws Exception {
        try {
            CompletableFuture<String> future;
            if (config.isClusterMode()) {
                future = glideClusterClient.set(key, value);
            } else {
                future = glideClient.set(key, value);
            }
            String result = future.get(500, TimeUnit.MILLISECONDS);
            return "OK".equals(result);
        } catch (Exception e) {
            throw new Exception("SET operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String get(String key) throws Exception {
        try {
            CompletableFuture<String> future;
            if (config.isClusterMode()) {
                future = glideClusterClient.get(key);
            } else {
                future = glideClient.get(key);
            }
            return future.get(500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new Exception("GET operation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getClientName() {
        String mode = config.isClusterMode() ? "Cluster" : "Standalone";
        String tls = config.isTlsEnabled() ? "TLS" : "Plain";
        return String.format("Valkey-Glide (%s, %s)", mode, tls);
    }
    
    @Override
    public boolean isConnected() {
        if (config.isClusterMode()) {
            return glideClusterClient != null;
        } else {
            return glideClient != null;
        }
    }
    
    @Override
    public boolean ping() throws Exception {
        try {
            CompletableFuture<String> future;
            if (config.isClusterMode()) {
                future = glideClusterClient.ping();
            } else {
                future = glideClient.ping();
            }
            String result = future.get(500, TimeUnit.MILLISECONDS);
            return "PONG".equals(result);
        } catch (Exception e) {
            throw new Exception("PING failed: " + e.getMessage(), e);
        }
    }
}
