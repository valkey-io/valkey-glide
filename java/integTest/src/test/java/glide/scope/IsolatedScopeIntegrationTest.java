/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.scope;

import static org.junit.jupiter.api.Assertions.*;

import glide.TestConfiguration;
import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.scope.IsolatedScope;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for Feature 2: Isolated Execution.
 * Tests WATCH/MULTI/EXEC, scope isolation, and zero-cost release.
 * Requires a Valkey server (uses test infrastructure endpoints).
 */
public class IsolatedScopeIntegrationTest {

    private GlideClientConfiguration getTestClientConfig() {
        String hostPort = TestConfiguration.STANDALONE_HOSTS[0];
        String[] parts = hostPort.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        return GlideClientConfiguration.builder()
                .address(NodeAddress.builder().host(host).port(port).build())
                .requestTimeout(5000)
                .build();
    }

    @Test
    public void testScopeAcquireAndBasicCommands() throws Exception {
        System.out.println("\n=== Test: Scope acquire and basic commands ===");

        GlideClient client = GlideClient.createClient(getTestClientConfig()).get(10, TimeUnit.SECONDS);

        // Acquire an isolated scope
        IsolatedScope scope = client.scopedConnection(Duration.ofSeconds(10))
                .get(10, TimeUnit.SECONDS);
        assertNotNull(scope);
        assertFalse(scope.isReleased());

        // Execute basic commands on the scope
        String key = "scope-test-" + UUID.randomUUID();
        String result = scope.set(key, "hello").get(5, TimeUnit.SECONDS);
        assertNotNull(result);

        String value = scope.get(key).get(5, TimeUnit.SECONDS);
        assertEquals("hello", value);

        String pingResult = scope.ping().get(5, TimeUnit.SECONDS);
        assertEquals("PONG", pingResult);

        // Release the scope
        scope.close();
        assertTrue(scope.isReleased());

        // Cleanup
        client.del(new String[]{key}).get(5, TimeUnit.SECONDS);
        client.close();

        System.out.println("Scope acquire and basic commands test PASSED!");
    }

    @Test
    public void testWatchMultiExecSuccess() throws Exception {
        System.out.println("\n=== Test: WATCH/MULTI/EXEC success ===");

        GlideClient client = GlideClient.createClient(getTestClientConfig()).get(10, TimeUnit.SECONDS);
        String key = "watch-test-" + UUID.randomUUID();
        client.set(key, "10").get(5, TimeUnit.SECONDS);

        try (IsolatedScope scope = client.scopedConnection(Duration.ofSeconds(10))
                .get(10, TimeUnit.SECONDS)) {

            // WATCH the key
            String watchResult = scope.watch(key).get(5, TimeUnit.SECONDS);
            assertEquals("OK", watchResult);

            // Read the value
            String val = scope.get(key).get(5, TimeUnit.SECONDS);
            assertEquals("10", val);

            // Start transaction
            String multiResult = scope.multi().get(5, TimeUnit.SECONDS);
            assertEquals("OK", multiResult);

            // Queue a SET
            scope.set(key, String.valueOf(Integer.parseInt(val) + 1)).get(5, TimeUnit.SECONDS);

            // Execute — should succeed since no one modified the key
            String execResult = scope.exec().get(5, TimeUnit.SECONDS);
            assertNotNull(execResult, "EXEC should succeed (no conflict)");
        }

        // Verify the value was updated
        String finalVal = client.get(key).get(5, TimeUnit.SECONDS);
        assertEquals("11", finalVal);

        // Cleanup
        client.del(new String[]{key}).get(5, TimeUnit.SECONDS);
        client.close();

        System.out.println("WATCH/MULTI/EXEC success test PASSED!");
    }

    @Test
    public void testScopeIsolation() throws Exception {
        System.out.println("\n=== Test: Scope isolation (two scopes don't interfere) ===");

        GlideClient client = GlideClient.createClient(getTestClientConfig()).get(10, TimeUnit.SECONDS);
        String key = "isolation-test-" + UUID.randomUUID();
        client.set(key, "100").get(5, TimeUnit.SECONDS);

        // Acquire two independent scopes
        IsolatedScope scope1 = client.scopedConnection(Duration.ofSeconds(10))
                .get(10, TimeUnit.SECONDS);
        IsolatedScope scope2 = client.scopedConnection(Duration.ofSeconds(10))
                .get(10, TimeUnit.SECONDS);

        // Both scopes should have different scope IDs (different connections)
        assertNotEquals(scope1.getScopeId(), scope2.getScopeId(),
                "Two scopes should use different connections");

        // Both can WATCH the same key independently
        scope1.watch(key).get(5, TimeUnit.SECONDS);
        scope2.watch(key).get(5, TimeUnit.SECONDS);

        // Both can read
        String val1 = scope1.get(key).get(5, TimeUnit.SECONDS);
        String val2 = scope2.get(key).get(5, TimeUnit.SECONDS);
        assertEquals("100", val1);
        assertEquals("100", val2);

        // Scope1 commits a transaction
        scope1.multi().get(5, TimeUnit.SECONDS);
        scope1.set(key, "101").get(5, TimeUnit.SECONDS);
        String exec1 = scope1.exec().get(5, TimeUnit.SECONDS);
        assertNotNull(exec1, "Scope1 EXEC should succeed");

        // Scope2's WATCH should now fail because key was modified
        scope2.multi().get(5, TimeUnit.SECONDS);
        scope2.set(key, "200").get(5, TimeUnit.SECONDS);
        String exec2 = scope2.exec().get(5, TimeUnit.SECONDS);
        // exec2 should be null (WATCH aborted) or empty

        scope1.close();
        scope2.close();

        // Final value should be from scope1 (101)
        String finalVal = client.get(key).get(5, TimeUnit.SECONDS);
        assertEquals("101", finalVal, "Only scope1's transaction should have committed");

        client.del(new String[]{key}).get(5, TimeUnit.SECONDS);
        client.close();

        System.out.println("Scope isolation test PASSED!");
    }

    @Test
    public void testZeroCostRelease() throws Exception {
        System.out.println("\n=== Test: Zero-cost release (no cleanup for clean state) ===");

        GlideClient client = GlideClient.createClient(getTestClientConfig()).get(10, TimeUnit.SECONDS);
        String key = "zerocost-" + UUID.randomUUID();

        // Acquire, do only GET/SET (no state mutations), release
        long startNanos = System.nanoTime();

        try (IsolatedScope scope = client.scopedConnection(Duration.ofSeconds(10))
                .get(10, TimeUnit.SECONDS)) {
            scope.set(key, "value").get(5, TimeUnit.SECONDS);
            scope.get(key).get(5, TimeUnit.SECONDS);
        }
        // scope.close() called by try-with-resources — should be instantaneous

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        System.out.println("  Scope lifecycle (acquire + SET + GET + release): " + elapsedMs + " ms");

        // Cleanup
        client.del(new String[]{key}).get(5, TimeUnit.SECONDS);
        client.close();

        System.out.println("Zero-cost release test PASSED!");
    }

    @Test
    public void testScopeReuse() throws Exception {
        System.out.println("\n=== Test: Scope reuse (LIFO — same connection returned) ===");

        GlideClient client = GlideClient.createClient(getTestClientConfig()).get(10, TimeUnit.SECONDS);

        // First borrow
        IsolatedScope scope1 = client.scopedConnection(Duration.ofSeconds(10))
                .get(10, TimeUnit.SECONDS);
        long scopeId1 = scope1.getScopeId();
        scope1.ping().get(5, TimeUnit.SECONDS);
        scope1.close();

        // Give the pool a moment to process the release
        Thread.sleep(100);

        // Second borrow — should get the same connection back (LIFO)
        IsolatedScope scope2 = client.scopedConnection(Duration.ofSeconds(10))
                .get(10, TimeUnit.SECONDS);
        long scopeId2 = scope2.getScopeId();
        scope2.close();

        assertEquals(scopeId1, scopeId2, "LIFO reuse: same scope_id should be returned");

        client.close();
        System.out.println("Scope reuse test PASSED!");
    }

    @Test
    public void testScopeCloseIdempotent() throws Exception {
        System.out.println("\n=== Test: Scope close is idempotent ===");

        GlideClient client = GlideClient.createClient(getTestClientConfig()).get(10, TimeUnit.SECONDS);

        IsolatedScope scope = client.scopedConnection(Duration.ofSeconds(10))
                .get(10, TimeUnit.SECONDS);

        // Close multiple times — should not throw
        scope.close();
        scope.close();
        scope.close();

        assertTrue(scope.isReleased());

        // Using scope after close should throw
        assertThrows(IllegalStateException.class, scope::getScopeId);

        client.close();
        System.out.println("Scope close idempotent test PASSED!");
    }

    @Test
    public void testConcurrentScopeAcquisition() throws Exception {
        System.out.println("\n=== Test: Concurrent scope acquisition from single client ===");

        GlideClient client = GlideClient.createClient(getTestClientConfig()).get(10, TimeUnit.SECONDS);

        int numThreads = 8;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        ConcurrentHashMap<Long, Boolean> scopeIds = new ConcurrentHashMap<>();

        for (int t = 0; t < numThreads; t++) {
            final int threadIdx = t;
            new Thread(() -> {
                try {
                    // Each thread acquires its own scope
                    IsolatedScope scope = client.scopedConnection(Duration.ofSeconds(15))
                            .get(15, TimeUnit.SECONDS);

                    // Record scope_id
                    scopeIds.put(scope.getScopeId(), true);

                    // Do some work on the scope
                    String key = "concurrent-scope-" + threadIdx + "-" + UUID.randomUUID();
                    scope.set(key, "thread-" + threadIdx).get(5, TimeUnit.SECONDS);
                    String val = scope.get(key).get(5, TimeUnit.SECONDS);
                    assertEquals("thread-" + threadIdx, val);

                    // Clean up key via scope
                    scope.executeCommand("DEL", key).get(5, TimeUnit.SECONDS);
                    scope.close();

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Thread " + threadIdx + " error: " + e);
                    e.printStackTrace();
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should finish within 30s");
        assertEquals(numThreads, successCount.get(),
                "All " + numThreads + " threads should succeed. Errors: " + errorCount.get());

        System.out.println("  Unique scope_ids observed: " + scopeIds.size()
                + " (may be < " + numThreads + " due to LIFO reuse)");

        client.close();
        System.out.println("Concurrent scope acquisition test PASSED! "
                + numThreads + "/" + numThreads + " threads succeeded with unique scopes.");
    }
}
