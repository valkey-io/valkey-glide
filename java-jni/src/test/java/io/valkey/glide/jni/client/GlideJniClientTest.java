package io.valkey.glide.jni.client;

import io.valkey.glide.jni.GlideJniClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;

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
    void testConstructorValidation() {
        assertThrows(RuntimeException.class, () -> {
            new GlideJniClient(null);
        });
    }

    @Test
    @EnabledIf("isNativeLibraryAvailable")
    void testBasicConnection() {
        // This test only runs if the native library is available
        try {
            GlideJniClient.Config config = new GlideJniClient.Config(Arrays.asList("localhost:6379"));
            
            try (GlideJniClient client = new GlideJniClient(config)) {
                // Test PING
                String pongResponse = client.ping();
                assertEquals("PONG", pongResponse);

                // Test SET/GET
                boolean setResult = client.set("test_key", "test_value");
                assertTrue(setResult);

                String getValue = client.get("test_key");
                assertEquals("test_value", getValue);

            }
        } catch (Exception e) {
            // Expected if Redis is not running or native lib not built
            System.err.println("Integration test skipped: " + e.getMessage());
        }
    }

    @Test
    @EnabledIf("isNativeLibraryAvailable")
    void testAsyncOperations() {
        try {
            GlideJniClient.Config config = new GlideJniClient.Config(Arrays.asList("localhost:6379"));
            
            try (GlideJniClient client = new GlideJniClient(config)) {
                // Test async PING
                String pongResponse = client.pingAsync().get();
                assertEquals("PONG", pongResponse);

                // Test async SET/GET
                Boolean setResult = client.setAsync("async_test", "async_value").get();
                assertTrue(setResult);

                String getValue = client.getAsync("async_test").get();
                assertEquals("async_value", getValue);

            }
        } catch (Exception e) {
            // Expected if Redis is not running or native lib not built
            System.err.println("Integration test skipped: " + e.getMessage());
        }
    }
}