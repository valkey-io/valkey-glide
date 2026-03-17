/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Unit tests for RedisURI class. */
public class RedisURITest {

    @Test
    public void testBuilderWithHostAndPort() {
        RedisURI uri = RedisURI.Builder.redis("localhost", 6379).build();

        assertEquals("localhost", uri.getHost());
        assertEquals(6379, uri.getPort());
        assertFalse(uri.isSsl());
        assertEquals(0, uri.getDatabase());
    }

    @Test
    public void testBuilderWithSsl() {
        RedisURI uri = RedisURI.Builder.redis("localhost", 6380).withSsl(true).build();

        assertEquals("localhost", uri.getHost());
        assertEquals(6380, uri.getPort());
        assertTrue(uri.isSsl());
    }

    @Test
    public void testBuilderWithPassword() {
        RedisURI uri = RedisURI.Builder.redis("localhost", 6379).withPassword("secret").build();

        assertEquals("secret", uri.getPassword());
        assertNull(uri.getUsername());
    }

    @Test
    public void testBuilderWithAuthentication() {
        RedisURI uri =
                RedisURI.Builder.redis("localhost", 6379).withAuthentication("user1", "pass1").build();

        assertEquals("user1", uri.getUsername());
        assertEquals("pass1", uri.getPassword());
    }

    @Test
    public void testBuilderWithDatabase() {
        RedisURI uri = RedisURI.Builder.redis("localhost", 6379).withDatabase(5).build();

        assertEquals(5, uri.getDatabase());
    }

    @Test
    public void testBuilderWithTimeout() {
        Duration timeout = Duration.ofSeconds(10);
        RedisURI uri = RedisURI.Builder.redis("localhost", 6379).withTimeout(timeout).build();

        assertEquals(timeout, uri.getTimeout());
    }

    @Test
    public void testBuilderChaining() {
        RedisURI uri =
                RedisURI.builder()
                        .withHost("redis.example.com")
                        .withPort(6380)
                        .withSsl(true)
                        .withPassword("mypassword")
                        .withDatabase(3)
                        .withTimeout(Duration.ofSeconds(5))
                        .build();

        assertEquals("redis.example.com", uri.getHost());
        assertEquals(6380, uri.getPort());
        assertTrue(uri.isSsl());
        assertEquals("mypassword", uri.getPassword());
        assertEquals(3, uri.getDatabase());
        assertEquals(Duration.ofSeconds(5), uri.getTimeout());
    }

    @Test
    public void testCreateFromString() {
        RedisURI uri = RedisURI.create("redis://localhost:6379");

        assertEquals("localhost", uri.getHost());
        assertEquals(6379, uri.getPort());
        assertFalse(uri.isSsl());
    }

    @Test
    public void testCreateFromStringWithSsl() {
        RedisURI uri = RedisURI.create("rediss://secure-host:6380");

        assertEquals("secure-host", uri.getHost());
        assertEquals(6380, uri.getPort());
        assertTrue(uri.isSsl());
    }

    @Test
    public void testCreateFromStringWithPassword() {
        RedisURI uri = RedisURI.create("redis://:mypassword@localhost:6379");

        assertEquals("localhost", uri.getHost());
        assertEquals(6379, uri.getPort());
        assertEquals("mypassword", uri.getPassword());
    }

    @Test
    public void testCreateFromStringWithUsernameAndPassword() {
        RedisURI uri = RedisURI.create("redis://username:password@localhost:6379");

        assertEquals("localhost", uri.getHost());
        assertEquals(6379, uri.getPort());
        assertEquals("username", uri.getUsername());
        assertEquals("password", uri.getPassword());
    }

    @Test
    public void testCreateFromStringWithDatabase() {
        RedisURI uri = RedisURI.create("redis://localhost:6379/3");

        assertEquals("localhost", uri.getHost());
        assertEquals(6379, uri.getPort());
        assertEquals(3, uri.getDatabase());
    }

    @Test
    public void testDefaultValues() {
        RedisURI uri = RedisURI.builder().build();

        assertEquals("localhost", uri.getHost());
        assertEquals(6379, uri.getPort());
        assertFalse(uri.isSsl());
        assertEquals(0, uri.getDatabase());
        assertNull(uri.getUsername());
        assertNull(uri.getPassword());
        assertNull(uri.getTimeout());
    }
}
