package io.valkey.glide.jni;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import io.valkey.glide.jni.client.GlideJniClient;

/**
 * Basic JNI client tests - will only run if a local Valkey server is available
 */
public class SimpleJniTest {

    private static final int PORT = 6379;
    private static final String HOST = "localhost";

    /**
     * Check if a Valkey server is running and accessible
     */
    public static boolean isValkeServerRunning() {
        try (GlideJniClient client = new GlideJniClient(
                new GlideJniClient.Config(Arrays.asList(HOST + ":" + PORT)))) {

            // Try to ping the server
            CompletableFuture<String> pingFuture = client.ping();
            String pingResult = pingFuture.get();
            return "PONG".equals(pingResult);

        } catch (Exception e) {
            System.out.println("Valkey server not available: " + e.getMessage());
            return false;
        }
    }

    @Test
    @EnabledIf("isValkeServerRunning")
    public void testBasicOperations() throws Exception {
        try (GlideJniClient client = new GlideJniClient(
                new GlideJniClient.Config(Arrays.asList(HOST + ":" + PORT)))) {

            String key = "test:key:" + UUID.randomUUID();
            String value = "test value " + UUID.randomUUID();

            // Test set
            CompletableFuture<String> setFuture = client.set(key, value);
            String setResult = setFuture.get();
            assertEquals("OK", setResult);

            // Test get
            CompletableFuture<String> getFuture = client.get(key);
            String getResult = getFuture.get();
            assertEquals(value, getResult);

            // Test get non-existent key
            String nonExistentKey = "test:nonexistent:" + UUID.randomUUID();
            CompletableFuture<String> getNonExistentFuture = client.get(nonExistentKey);
            String getNonExistentResult = getNonExistentFuture.get();
            assertNull(getNonExistentResult);

            // Test ping
            CompletableFuture<String> pingFuture = client.ping();
            String pingResult = pingFuture.get();
            assertEquals("PONG", pingResult);
        }
    }

    @Test
    @EnabledIf("isValkeServerRunning")
    public void testLargeValue() throws Exception {
        try (GlideJniClient client = new GlideJniClient(
                new GlideJniClient.Config(Arrays.asList(HOST + ":" + PORT)))) {

            String key = "test:large:" + UUID.randomUUID();
            String value = "0".repeat(4000);

            CompletableFuture<String> setFuture = client.set(key, value);
            String setResult = setFuture.get();
            assertEquals("OK", setResult);

            CompletableFuture<String> getFuture = client.get(key);
            String getResult = getFuture.get();
            assertEquals(value, getResult);
            assertEquals(4000, getResult.length());
        }
    }

    @Test
    public void testConfigBuilder() {
        GlideJniClient.Config config = new GlideJniClient.Config(Arrays.asList("localhost:6379"))
                .useTls(true)
                .clusterMode(true)
                .requestTimeout(10000)
                .connectionTimeout(5000)
                .credentials("user", "pass")
                .databaseId(1);

        // Cannot test private getters directly, but we can test that the builder methods work
        // by checking the returned config object is the same instance (fluent API)
        GlideJniClient.Config result = config.useTls(true);
        assertSame(config, result);

        result = config.clusterMode(true);
        assertSame(config, result);

        result = config.requestTimeout(10000);
        assertSame(config, result);

        result = config.connectionTimeout(5000);
        assertSame(config, result);

        result = config.credentials("user", "pass");
        assertSame(config, result);

        result = config.databaseId(1);
        assertSame(config, result);
    }
}
