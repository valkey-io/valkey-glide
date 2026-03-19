/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import io.lettuce.core.api.StatefulRedisConnection;
import java.util.concurrent.ExecutionException;

/**
 * Redis client for Lettuce compatibility layer. Provides factory methods to create connections to
 * Redis servers.
 */
public class RedisClient {

    private final RedisURI redisURI;
    private volatile boolean shutdown = false;

    private RedisClient(RedisURI redisURI) {
        this.redisURI = redisURI;
    }

    /**
     * Create a new RedisClient with the given URI.
     *
     * @param uri the Redis URI configuration
     * @return a new RedisClient instance
     */
    public static RedisClient create(RedisURI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("RedisURI cannot be null");
        }
        return new RedisClient(uri);
    }

    /**
     * Create a new RedisClient with the given URI string.
     *
     * @param uri the Redis URI string (e.g., "redis://localhost:6379")
     * @return a new RedisClient instance
     */
    public static RedisClient create(String uri) {
        return create(RedisURI.create(uri));
    }

    /**
     * Connect to the Redis server and return a stateful connection.
     *
     * @return a StatefulRedisConnection
     * @throws RedisConnectionException if connection fails
     */
    public StatefulRedisConnection<String, String> connect() {
        if (shutdown) {
            throw new RedisException("Client has been shutdown");
        }

        try {
            // Map Lettuce URI to GLIDE configuration
            GlideClientConfiguration glideConfig = LettuceConfigurationMapper.mapToGlideConfig(redisURI);

            // Create GLIDE client
            GlideClient glideClient = GlideClient.createClient(glideConfig).get();

            // Wrap in Lettuce-compatible connection
            return new GlideStatefulRedisConnection(glideClient, redisURI.getTimeout());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedisConnectionException("Connection interrupted", e);
        } catch (ExecutionException e) {
            throw new RedisConnectionException(
                    "Failed to create connection to " + redisURI.getHost() + ":" + redisURI.getPort(),
                    e.getCause());
        } catch (Exception e) {
            throw new RedisConnectionException("Failed to create connection", e);
        }
    }

    /**
     * Shutdown the client. Prevents new connections from being created. Existing connections should
     * be closed before calling this method.
     */
    public void shutdown() {
        shutdown = true;
    }

    /**
     * Check if the client has been shutdown.
     *
     * @return true if shutdown
     */
    public boolean isShutdown() {
        return shutdown;
    }
}
