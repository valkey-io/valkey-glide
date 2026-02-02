/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.exceptions.TimeoutException;
import glide.internal.AsyncRegistry;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
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
     * Test 3: Blocking commands skip Java-side timeout.
     *
     * <p>Verifies that blocking commands (BLPOP, etc.) use server-side timeout from command
     * arguments, NOT the Java-side client timeout. A BLPOP with 1s server timeout should NOT throw
     * Java TimeoutException even when client has 200ms timeout.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void blocking_command_uses_server_timeout_not_java_timeout(boolean clusterMode) {
        // Client with short 200ms Java-side timeout
        int shortJavaTimeoutMs = 200;

        BaseClient client =
                clusterMode
                        ? GlideClusterClient.createClient(
                                        commonClusterClientConfig().requestTimeout(shortJavaTimeoutMs).build())
                                .get()
                        : GlideClient.createClient(
                                        commonClientConfig().requestTimeout(shortJavaTimeoutMs).build())
                                .get();

        try {
            String key = "blpop-timeout-test-" + UUID.randomUUID();

            long startTime = System.currentTimeMillis();

            // BLPOP with 1 second server-side timeout - should NOT throw Java TimeoutException
            // even though Java timeout is only 200ms
            String[] result = client.blpop(new String[] {key}, 1.0).get();
            long elapsed = System.currentTimeMillis() - startTime;

            // Result should be null (key doesn't exist, server timeout expired)
            assertNull(result, "Expected null from BLPOP on non-existent key");

            // Should have waited ~1 second (server timeout), NOT 200ms (Java timeout)
            assertTrue(
                    elapsed >= 900 && elapsed < 2000,
                    "Expected ~1s server timeout but took " + elapsed + "ms");
        } finally {
            client.close();
        }
    }

    /**
     * Test 4: Timeout tasks are cleaned up after request completion.
     *
     * <p>Verifies that scheduled timeout tasks are cancelled and removed from the registry when
     * requests complete. Uses AsyncRegistry.getPendingTimeoutCount() to assert cleanup.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void timeout_tasks_cleaned_up_after_completion(boolean clusterMode) {
        BaseClient client =
                clusterMode
                        ? GlideClusterClient.createClient(
                                        commonClusterClientConfig().requestTimeout(5000).build())
                                .get()
                        : GlideClient.createClient(commonClientConfig().requestTimeout(5000).build()).get();

        try {
            // Record initial state
            int initialTimeouts = AsyncRegistry.getPendingTimeoutCount();
            int initialFutures = AsyncRegistry.getActiveFutureCount();

            // Execute multiple requests that each schedule a timeout task
            String keyPrefix = "cleanup-test-" + UUID.randomUUID() + "-";
            for (int i = 0; i < 50; i++) {
                String key = keyPrefix + i;
                assertEquals("OK", client.set(key, "value" + i).get());
                assertEquals("value" + i, client.get(key).get());
            }

            // Allow brief time for async cleanup to complete
            Thread.sleep(100);

            // After all requests complete, timeout tasks should be cleaned up
            int finalTimeouts = AsyncRegistry.getPendingTimeoutCount();
            int finalFutures = AsyncRegistry.getActiveFutureCount();

            assertEquals(
                    initialTimeouts,
                    finalTimeouts,
                    "Timeout tasks should be cleaned up after completion, but found "
                            + (finalTimeouts - initialTimeouts)
                            + " leaked");
            assertEquals(
                    initialFutures,
                    finalFutures,
                    "Active futures should be cleaned up after completion, but found "
                            + (finalFutures - initialFutures)
                            + " leaked");
        } finally {
            client.close();
        }
    }
}
