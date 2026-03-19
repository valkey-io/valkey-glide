/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

import glide.api.GlideClient;
import io.lettuce.core.api.sync.RedisStringCommands;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implementation of RedisStringCommands backed by GLIDE client. Provides synchronous (blocking)
 * execution of Redis string commands.
 */
public class GlideRedisStringCommands implements RedisStringCommands<String, String> {

    private final GlideClient glideClient;
    private volatile Duration timeout;

    public GlideRedisStringCommands(GlideClient glideClient, Duration timeout) {
        this.glideClient = glideClient;
        this.timeout = timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public String set(String key, String value) {
        try {
            if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
                return glideClient.set(key, value).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                return glideClient.set(key, value).get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedisConnectionException("Command interrupted", e);
        } catch (ExecutionException e) {
            throw new RedisException("Command execution failed", e.getCause());
        } catch (TimeoutException e) {
            throw new RedisConnectionException("Command timed out", e);
        }
    }

    @Override
    public String get(String key) {
        try {
            if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
                return glideClient.get(key).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                return glideClient.get(key).get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedisConnectionException("Command interrupted", e);
        } catch (ExecutionException e) {
            throw new RedisException("Command execution failed", e.getCause());
        } catch (TimeoutException e) {
            throw new RedisConnectionException("Command timed out", e);
        }
    }
}
