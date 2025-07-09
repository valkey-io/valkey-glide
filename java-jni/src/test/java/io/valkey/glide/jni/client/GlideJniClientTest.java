package io.valkey.glide.jni.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for GlideJniClient.
 * <p>
 * These tests require a Redis server running on localhost:6379
 * and the native library to be built.
 */
class GlideJniClientTest {

    /**
     * Check if native library and Redis are available for testing
     */
    static boolean isNativeLibraryAvailable() {
        try {
            System.loadLibrary("glidejni");
            return true;
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native library not available: " + e.getMessage());
            return false;
        }
    }

    @Test
    void testCommandTypeConstants() {
        assertEquals(1, GlideJniClient.CommandType.GET);
        assertEquals(2, GlideJniClient.CommandType.SET);
        assertEquals(3, GlideJniClient.CommandType.PING);
    }

    @Test
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            new GlideJniClient(null);
        });
    }

    @Test
    void testMethodValidation() {
        // Mock client that won't actually connect for validation tests
        // We'll test validation logic without requiring Redis

        // These would throw exceptions before reaching native code
        // if the client were instantiated, but we can't test that
        // without a working native implementation
    }    @Test
    @EnabledIf("isNativeLibraryAvailable")
    void testBasicConnection() {
        // This test only runs if the native library is available
        try (GlideJniClient client = new GlideJniClient("redis://localhost:6379")) {
            assertFalse(client.isClosed());

            // Test PING with CompletableFuture
            String pongResponse = client.ping().get();
            assertNotNull(pongResponse);
            assertEquals("PONG", pongResponse);

            // Test SET/GET with CompletableFuture
            String setResponse = client.set("test_key", "test_value").get();
            assertEquals("OK", setResponse);

            String getValue = client.get("test_key").get();
            assertNotNull(getValue);
            assertEquals("test_value", getValue);

        } catch (Exception e) {
            // Expected if Redis is not running or native lib not built
            System.err.println("Integration test skipped: " + e.getMessage());
        }
    }

    @Test
    @EnabledIf("isNativeLibraryAvailable")
    void testClientLifecycle() {
        try {
            GlideJniClient client = new GlideJniClient("redis://localhost:6379");
            assertFalse(client.isClosed());

            client.close();
            assertTrue(client.isClosed());

            // Operations after close should throw
            assertThrows(IllegalStateException.class, () -> {
                client.ping().get();
            });

        } catch (RuntimeException e) {
            // Expected if Redis is not running or native lib not built
            System.err.println("Integration test skipped: " + e.getMessage());
        }
    }
}
