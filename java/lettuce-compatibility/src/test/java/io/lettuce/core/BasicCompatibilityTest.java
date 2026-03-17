/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

import static org.junit.jupiter.api.Assertions.*;

import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.Test;

/**
 * Basic compatibility tests for Lettuce compatibility layer. Verifies API contracts and method
 * signatures exist.
 */
public class BasicCompatibilityTest {

    @Test
    public void testRedisURIConstructorSignatures() {
        assertDoesNotThrow(
                () -> {
                    Class<RedisURI> uriClass = RedisURI.class;

                    // Verify static factory methods exist
                    uriClass.getMethod("create", String.class);
                    uriClass.getMethod("builder");

                    // Verify Builder class exists
                    Class<?> builderClass = RedisURI.Builder.class;
                    builderClass.getMethod("redis", String.class, int.class);
                    builderClass.getMethod("withHost", String.class);
                    builderClass.getMethod("withPort", int.class);
                    builderClass.getMethod("withSsl", boolean.class);
                    builderClass.getMethod("build");
                });
    }

    @Test
    public void testRedisClientConstructorSignatures() {
        assertDoesNotThrow(
                () -> {
                    Class<RedisClient> clientClass = RedisClient.class;

                    // Verify factory methods exist
                    clientClass.getMethod("create", RedisURI.class);
                    clientClass.getMethod("create", String.class);
                    clientClass.getMethod("connect");
                    clientClass.getMethod("shutdown");
                });
    }

    @Test
    public void testStatefulRedisConnectionInterface() {
        assertDoesNotThrow(
                () -> {
                    Class<StatefulRedisConnection> connClass = StatefulRedisConnection.class;

                    // Verify required methods exist
                    connClass.getMethod("sync");
                    connClass.getMethod("async");
                    connClass.getMethod("setTimeout", java.time.Duration.class);
                    connClass.getMethod("close");
                    connClass.getMethod("isOpen");
                });
    }

    @Test
    public void testRedisStringAsyncCommandsInterface() {
        assertDoesNotThrow(
                () -> {
                    Class<?> asyncCommandsClass =
                            Class.forName("io.lettuce.core.api.async.RedisStringAsyncCommands");

                    asyncCommandsClass.getMethod("set", Object.class, Object.class);
                    asyncCommandsClass.getMethod("get", Object.class);
                });
    }

    @Test
    public void testRedisFutureInterface() {
        assertDoesNotThrow(
                () -> {
                    Class<?> futureClass = Class.forName("io.lettuce.core.RedisFuture");

                    futureClass.getMethod("await", long.class, java.util.concurrent.TimeUnit.class);
                    futureClass.getMethod("await");
                });
    }

    @Test
    public void testRedisURIGetters() {
        RedisURI uri =
                RedisURI.Builder.redis("testhost", 1234).withPassword("testpass").withDatabase(5).build();

        assertNotNull(uri);
        assertEquals("testhost", uri.getHost());
        assertEquals(1234, uri.getPort());
        assertEquals("testpass", uri.getPassword());
        assertEquals(5, uri.getDatabase());
    }

    @Test
    public void testExceptionHierarchy() {
        RedisException baseEx = new RedisException("test");
        assertNotNull(baseEx);
        assertTrue(baseEx instanceof RuntimeException);

        RedisConnectionException connEx = new RedisConnectionException("test");
        assertNotNull(connEx);
        assertTrue(connEx instanceof RedisException);
    }
}
