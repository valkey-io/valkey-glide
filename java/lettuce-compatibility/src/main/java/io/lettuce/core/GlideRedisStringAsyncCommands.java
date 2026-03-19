/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

import glide.api.GlideClient;
import io.lettuce.core.api.async.RedisStringAsyncCommands;

/**
 * Implementation of RedisStringAsyncCommands backed by GLIDE client. Returns {@link RedisFuture}
 * for non-blocking execution.
 */
public class GlideRedisStringAsyncCommands implements RedisStringAsyncCommands<String, String> {

    private final GlideClient glideClient;

    public GlideRedisStringAsyncCommands(GlideClient glideClient) {
        this.glideClient = glideClient;
    }

    @Override
    public RedisFuture<String> set(String key, String value) {
        return new CompletableFutureAdapter<>(glideClient.set(key, value));
    }

    @Override
    public RedisFuture<String> get(String key) {
        return new CompletableFutureAdapter<>(glideClient.get(key));
    }
}
