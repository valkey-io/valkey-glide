/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

import glide.api.GlideClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.lettuce.core.api.sync.RedisStringCommands;
import java.time.Duration;

/**
 * Implementation of StatefulRedisConnection backed by GLIDE client. Manages the lifecycle of a
 * connection to a Redis server.
 */
public class GlideStatefulRedisConnection implements StatefulRedisConnection<String, String> {

    private final GlideClient glideClient;
    private final GlideRedisStringCommands syncCommands;
    private final GlideRedisStringAsyncCommands asyncCommands;
    private final String resourceId;
    private volatile boolean closed = false;

    public GlideStatefulRedisConnection(GlideClient glideClient, Duration timeout) {
        this.glideClient = glideClient;
        this.syncCommands = new GlideRedisStringCommands(glideClient, timeout);
        this.asyncCommands = new GlideRedisStringAsyncCommands(glideClient);
        this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
    }

    @Override
    public RedisStringCommands<String, String> sync() {
        if (closed) {
            throw new RedisException("Connection is closed");
        }
        return syncCommands;
    }

    @Override
    public RedisStringAsyncCommands<String, String> async() {
        if (closed) {
            throw new RedisException("Connection is closed");
        }
        return asyncCommands;
    }

    @Override
    public void setTimeout(Duration timeout) {
        syncCommands.setTimeout(timeout);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            ResourceLifecycleManager.getInstance().unregisterResource(resourceId);
            try {
                glideClient.close();
            } catch (Exception e) {
                throw new RedisException("Failed to close connection", e);
            }
        }
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }
}
