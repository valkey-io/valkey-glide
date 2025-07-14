package io.valkey.glide.jni.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the GlideJniClient class.
 *
 * Note: These tests don't connect to a real server, but validate basic
 * functionality and error handling.
 */
@Disabled("Need running Valkey server")
public class GlideJniClientTest {

    @Test
    public void testConfigBuilder() {
        GlideJniClient.Config config = new GlideJniClient.Config(Arrays.asList("localhost:6379"))
                .useTls(true)
                .clusterMode(true)
                .requestTimeout(10000)
                .connectionTimeout(5000)
                .credentials("user", "pass")
                .databaseId(1);

        // Verify config values
        assertEquals(true, config.getUseTls());
        assertEquals(true, config.getClusterMode());
        assertEquals(10000, config.getRequestTimeoutMs());
        assertEquals(5000, config.getConnectionTimeoutMs());
        assertEquals("user", config.getUsername());
        assertEquals("pass", config.getPassword());
        assertEquals(1, config.getDatabaseId());

        // Check array conversion
        String[] addresses = config.getAddresses();
        assertEquals(1, addresses.length);
        assertEquals("localhost:6379", addresses[0]);
    }

    @Test
    public void testEmptyAddressList() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            try (GlideJniClient client = new GlideJniClient(new GlideJniClient.Config(Collections.emptyList()))) {
            }
        });

        assertTrue(exception.getMessage().contains("address"));
    }

    @Test
    public void testNullConfig() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            try (GlideJniClient client = new GlideJniClient((GlideJniClient.Config) null)) {
            }
        });

        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    public void testConvenienceConstructor() {
        assertDoesNotThrow(() -> {
            try (GlideJniClient client = new GlideJniClient("localhost", 6379)) {
            }
        });
    }

    @Test
    public void testNullKey() {
        try (GlideJniClient client = new GlideJniClient("localhost", 6379)) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> {
                client.get(null);
            });

            assertTrue(exception.getMessage().contains("null"));
        }
    }

    @Test
    public void testNullValueInSet() {
        try (GlideJniClient client = new GlideJniClient("localhost", 6379)) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> {
                client.set("key", null);
            });

            assertTrue(exception.getMessage().contains("null"));
        }
    }

    @Test
    public void testOperationsAfterClose() {
        GlideJniClient client = new GlideJniClient("localhost", 6379);
        client.close();

        Exception getException = assertThrows(IllegalStateException.class, () -> {
            client.get("key");
        });

        Exception setException = assertThrows(IllegalStateException.class, () -> {
            client.set("key", "value");
        });

        Exception pingException = assertThrows(IllegalStateException.class, () -> {
            client.ping();
        });

        assertTrue(getException.getMessage().contains("closed"));
        assertTrue(setException.getMessage().contains("closed"));
        assertTrue(pingException.getMessage().contains("closed"));
    }

    @Test
    public void testMultipleClose() {
        GlideJniClient client = new GlideJniClient("localhost", 6379);

        // These should not throw exceptions
        client.close();
        client.close();
        client.close();

        assertTrue(client.isClosed());
    }

    @Test
    public void testExceptionHandlingInAsync() {
        try (GlideJniClient client = new GlideJniClient("localhost", 6379)) {
            // This should complete exceptionally since we're not actually connecting to Redis
            CompletableFuture<String> future = client.get("key");

            ExecutionException exception = assertThrows(ExecutionException.class, () -> {
                future.get();
            });

            // The cause should be from the native side
            assertNotNull(exception.getCause());
        }
    }

    @Test
    public void testIsClosed() {
        GlideJniClient client = new GlideJniClient("localhost", 6379);

        assertFalse(client.isClosed());

        client.close();

        assertTrue(client.isClosed());
    }

    @Test
    public void testCloseInTryWithResources() {
        GlideJniClient client = null;

        try {
            client = new GlideJniClient("localhost", 6379);
        } finally {
            if (client != null) {
                client.close();
                assertTrue(client.isClosed());
            }
        }
    }

    @Test
    public void testTryWithResourcesAutoClose() {
        GlideJniClient clientRef;

        try (GlideJniClient client = new GlideJniClient("localhost", 6379)) {
            clientRef = client;
            assertFalse(client.isClosed());
        }

        assertTrue(clientRef.isClosed());
        // Explicitly close to satisfy resource leak detection
        clientRef.close();
    }
}
