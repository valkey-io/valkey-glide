/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for RedisClient class. These tests verify API contracts without requiring a live
 * server.
 */
public class RedisClientTest {

    @Test
    public void testCreateWithURI() {
        RedisURI uri = RedisURI.Builder.redis("localhost", 6379).build();
        RedisClient client = RedisClient.create(uri);

        assertNotNull(client);
        assertFalse(client.isShutdown());
    }

    @Test
    public void testCreateWithString() {
        RedisClient client = RedisClient.create("redis://localhost:6379");

        assertNotNull(client);
        assertFalse(client.isShutdown());
    }

    @Test
    public void testCreateWithNullURIThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    RedisClient.create((RedisURI) null);
                });
    }

    @Test
    public void testShutdown() {
        RedisURI uri = RedisURI.Builder.redis("localhost", 6379).build();
        RedisClient client = RedisClient.create(uri);

        assertFalse(client.isShutdown());

        client.shutdown();

        assertTrue(client.isShutdown());
    }

    @Test
    public void testConnectAfterShutdownThrows() {
        RedisURI uri = RedisURI.Builder.redis("localhost", 6379).build();
        RedisClient client = RedisClient.create(uri);

        client.shutdown();

        assertThrows(
                RedisException.class,
                () -> {
                    client.connect();
                });
    }
}
