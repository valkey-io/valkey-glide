/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.exceptions.TimeoutException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration tests for client-side timeout behavior.
 *
 * <p>These tests verify the Java-side timeout enforcement introduced via AsyncRegistry, ensuring
 * requests are properly timed out and cleaned up.
 */
@Timeout(30) // seconds - test-level timeout to prevent hanging
public class TimeoutTests {

    /**
     * Test 1: Normal operation - request completes well before timeout.
     *
     * <p>Verifies that commands complete successfully when the server responds within the configured
     * timeout period.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void request_completes_before_timeout(boolean clusterMode) {
        BaseClient client =
                clusterMode
                        ? GlideClusterClient.createClient(
                                        commonClusterClientConfig().requestTimeout(5000).build())
                                .get()
                        : GlideClient.createClient(commonClientConfig().requestTimeout(5000).build()).get();

        try {
            String key = "timeout-test-" + UUID.randomUUID();
            assertEquals("OK", client.set(key, "value").get());
            assertEquals("value", client.get(key).get());
            assertEquals(1L, client.del(new String[] {key}).get());
        } finally {
            client.close();
        }
    }

    /**
     * Test 2: Request timeout - command exceeds configured timeout.
     *
     * <p>Verifies that TimeoutException is thrown when a slow command exceeds the client's request
     * timeout. Uses DEBUG SLEEP to simulate a slow server response.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void request_exceeds_timeout_throws_exception(boolean clusterMode) {
        // Short timeout (500ms) to ensure the DEBUG SLEEP command times out
        // Using 500ms instead of 100ms for test stability across different environments
        int shortTimeoutMs = 500;

        BaseClient client =
                clusterMode
                        ? GlideClusterClient.createClient(
                                        commonClusterClientConfig().requestTimeout(shortTimeoutMs).build())
                                .get()
                        : GlideClient.createClient(commonClientConfig().requestTimeout(shortTimeoutMs).build())
                                .get();

        try {
            long startTime = System.currentTimeMillis();

            // DEBUG SLEEP blocks the server for 3 seconds - well beyond our 500ms timeout
            ExecutionException ex =
                    assertThrows(
                            ExecutionException.class,
                            () -> {
                                if (clusterMode) {
                                    ((GlideClusterClient) client)
                                            .customCommand(new String[] {"DEBUG", "SLEEP", "3"})
                                            .get();
                                } else {
                                    ((GlideClient) client).customCommand(new String[] {"DEBUG", "SLEEP", "3"}).get();
                                }
                            });

            long elapsed = System.currentTimeMillis() - startTime;

            // Verify timeout occurred before DEBUG SLEEP would complete (3s)
            // Allow some margin for scheduling overhead
            assertTrue(elapsed < 2000, "Expected timeout within ~500ms but took " + elapsed + "ms");

            // Verify the exception is a TimeoutException
            assertInstanceOf(
                    TimeoutException.class,
                    ex.getCause(),
                    "Expected TimeoutException but got: " + ex.getCause().getClass().getName());
        } finally {
            // Wait briefly for DEBUG SLEEP to complete before closing
            Thread.sleep(3100);
            client.close();
        }
    }

    /**
     * Test 3: Zero timeout uses Rust default - no Java-side timeout enforcement.
     *
     * <p>Verifies that when requestTimeout is 0, the client relies on Rust-side timeout handling
     * (default behavior) and commands complete normally.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void zero_timeout_uses_rust_default(boolean clusterMode) {
        // requestTimeout = 0 means no Java-side timeout (use Rust default)
        BaseClient client =
                clusterMode
                        ? GlideClusterClient.createClient(commonClusterClientConfig().requestTimeout(0).build())
                                .get()
                        : GlideClient.createClient(commonClientConfig().requestTimeout(0).build()).get();

        try {
            String key = "zero-timeout-" + UUID.randomUUID();
            // Normal commands should still work with Rust-side timeout
            assertEquals("OK", client.set(key, "test-value").get());
            assertEquals("test-value", client.get(key).get());
            assertEquals(1L, client.del(new String[] {key}).get());
        } finally {
            client.close();
        }
    }

    /**
     * Test 4: Timeout task cancellation - timeout cancelled when request completes.
     *
     * <p>Verifies that scheduled timeout tasks are properly cancelled when requests complete
     * successfully before the timeout. Tests multiple rapid requests to ensure cleanup works.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void timeout_task_cancelled_on_normal_completion(boolean clusterMode) {
        BaseClient client =
                clusterMode
                        ? GlideClusterClient.createClient(
                                        commonClusterClientConfig().requestTimeout(5000).build())
                                .get()
                        : GlideClient.createClient(commonClientConfig().requestTimeout(5000).build()).get();

        try {
            // Execute many rapid requests to verify timeout tasks are cancelled properly
            // and don't accumulate or cause issues
            String keyPrefix = "rapid-" + UUID.randomUUID() + "-";
            for (int i = 0; i < 100; i++) {
                String key = keyPrefix + i;
                assertEquals("OK", client.set(key, "value" + i).get());
                assertEquals("value" + i, client.get(key).get());
            }

            // Cleanup
            for (int i = 0; i < 100; i++) {
                client.del(new String[] {keyPrefix + i}).get();
            }
        } finally {
            client.close();
        }
    }

    /**
     * Test 5: Different timeout configurations - clients with different timeouts behave correctly.
     *
     * <p>Verifies that multiple clients can have different timeout configurations and each behaves
     * according to its own setting.
     */
    @Test
    @SneakyThrows
    public void different_clients_different_timeouts() {
        // Client with long timeout
        GlideClient longTimeoutClient =
                GlideClient.createClient(commonClientConfig().requestTimeout(10000).build()).get();

        // Client with short timeout (500ms for stability)
        GlideClient shortTimeoutClient =
                GlideClient.createClient(commonClientConfig().requestTimeout(500).build()).get();

        try {
            String key = "multi-client-" + UUID.randomUUID();

            // Long timeout client should handle normal operations fine
            assertEquals("OK", longTimeoutClient.set(key, "test").get());
            assertEquals("test", longTimeoutClient.get(key).get());

            // Short timeout client should also handle fast operations
            assertEquals("OK", shortTimeoutClient.set(key, "test2").get());
            assertEquals("test2", shortTimeoutClient.get(key).get());

            // Short timeout client should fail on slow operations (DEBUG SLEEP 2s > 500ms timeout)
            long startTime = System.currentTimeMillis();
            ExecutionException ex =
                    assertThrows(
                            ExecutionException.class,
                            () -> shortTimeoutClient.customCommand(new String[] {"DEBUG", "SLEEP", "2"}).get());
            long elapsed = System.currentTimeMillis() - startTime;

            // Verify timeout occurred before DEBUG SLEEP would complete
            assertTrue(elapsed < 1500, "Expected timeout within ~500ms but took " + elapsed + "ms");
            assertInstanceOf(
                    TimeoutException.class,
                    ex.getCause(),
                    "Expected TimeoutException but got: " + ex.getCause().getClass().getName());

            // Wait for DEBUG SLEEP to complete
            Thread.sleep(2100);

            // Long timeout client can still operate normally after short timeout client failed
            assertEquals("OK", longTimeoutClient.set(key, "still-works").get());
            assertEquals("still-works", longTimeoutClient.get(key).get());

            // Cleanup
            longTimeoutClient.del(new String[] {key}).get();
        } finally {
            shortTimeoutClient.close();
            longTimeoutClient.close();
        }
    }
}
