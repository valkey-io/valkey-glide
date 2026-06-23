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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Benchmarks and correctness tests for WATCH/MULTI/EXEC with IsolatedScope.
 *
 * Demonstrates:
 * 1. Performance: IsolatedScope vs creating a fresh client per transaction
 * 2. Correctness: Optimistic concurrency control (OCC) with concurrent writers
 *
 * Requires a Valkey server (uses test infrastructure endpoints).
 */
public class IsolatedScopeBenchmark {

    private GlideClientConfiguration getConfig() {
        String hostPort = TestConfiguration.STANDALONE_HOSTS[0];
        String[] parts = hostPort.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        return GlideClientConfiguration.builder()
                .address(NodeAddress.builder().host(host).port(port).build())
                .requestTimeout(5000)
                .build();
    }

    // ========== Performance Benchmark ==========

    @Test
    public void benchmarkWatchTransaction() throws Exception {
        final int ITERATIONS = 100;

        System.out.println("\n=== WATCH/MULTI/EXEC Performance Benchmark ===");
        System.out.println("Iterations: " + ITERATIONS);
        System.out.println("Each iteration: WATCH → GET → MULTI → SET(val+1) → EXEC");
        System.out.println();

        GlideClient sharedClient = GlideClient.createClient(getConfig()).get(10, TimeUnit.SECONDS);
        String counterKey = "bench-watch-" + UUID.randomUUID();
        sharedClient.set(counterKey, "0").get(5, TimeUnit.SECONDS);

        // --- Scenario A: Fresh client per transaction (old workaround) ---
        System.out.println("--- Scenario A: Fresh client per WATCH transaction ---");
        System.out.println("    (Only way to do WATCH before Feature 2 — creates TCP connection each time)");

        long scenarioAStart = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            GlideClient txClient = GlideClient.createClient(getConfig()).get(10, TimeUnit.SECONDS);
            txClient.customCommand(new String[]{"WATCH", counterKey}).get(5, TimeUnit.SECONDS);
            String val = txClient.get(counterKey).get(5, TimeUnit.SECONDS);
            txClient.customCommand(new String[]{"MULTI"}).get(5, TimeUnit.SECONDS);
            txClient.set(counterKey, String.valueOf(Integer.parseInt(val) + 1)).get(5, TimeUnit.SECONDS);
            txClient.customCommand(new String[]{"EXEC"}).get(5, TimeUnit.SECONDS);
            txClient.close();
        }

        long scenarioAElapsed = System.nanoTime() - scenarioAStart;
        double scenarioAMs = scenarioAElapsed / 1_000_000.0;
        double scenarioAPerOp = scenarioAMs / ITERATIONS;

        String valAfterA = sharedClient.get(counterKey).get(5, TimeUnit.SECONDS);
        System.out.printf("    Total: %.1f ms%n", scenarioAMs);
        System.out.printf("    Per transaction: %.2f ms%n", scenarioAPerOp);
        System.out.printf("    Counter: %s (expected: %d)%n", valAfterA, ITERATIONS);
        System.out.println();

        // Reset counter
        sharedClient.set(counterKey, "0").get(5, TimeUnit.SECONDS);

        // --- Scenario B: IsolatedScope (Feature 2) ---
        System.out.println("--- Scenario B: IsolatedScope from pool ---");
        System.out.println("    (Borrow dedicated connection, WATCH safely, return to pool)");

        // Warm up the scope pool
        IsolatedScope warmup = sharedClient.scopedConnection(Duration.ofSeconds(10))
                .get(10, TimeUnit.SECONDS);
        warmup.ping().get(5, TimeUnit.SECONDS);
        warmup.close();
        Thread.sleep(100);

        long scenarioBStart = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            try (IsolatedScope scope = sharedClient.scopedConnection(Duration.ofSeconds(10))
                    .get(10, TimeUnit.SECONDS)) {
                scope.watch(counterKey).get(5, TimeUnit.SECONDS);
                String val = scope.get(counterKey).get(5, TimeUnit.SECONDS);
                scope.multi().get(5, TimeUnit.SECONDS);
                scope.set(counterKey, String.valueOf(Integer.parseInt(val) + 1)).get(5, TimeUnit.SECONDS);
                scope.exec().get(5, TimeUnit.SECONDS);
            }
        }

        long scenarioBElapsed = System.nanoTime() - scenarioBStart;
        double scenarioBMs = scenarioBElapsed / 1_000_000.0;
        double scenarioBPerOp = scenarioBMs / ITERATIONS;

        String valAfterB = sharedClient.get(counterKey).get(5, TimeUnit.SECONDS);
        System.out.printf("    Total: %.1f ms%n", scenarioBMs);
        System.out.printf("    Per transaction: %.2f ms%n", scenarioBPerOp);
        System.out.printf("    Counter: %s (expected: %d)%n", valAfterB, ITERATIONS);
        System.out.println();

        // --- Breakdown: per-command latency on warmed scope ---
        System.out.println("--- Breakdown: per-command latency (warmed, no acquire/release) ---");
        sharedClient.set(counterKey, "0").get(5, TimeUnit.SECONDS);
        IsolatedScope breakdownScope = sharedClient.scopedConnection(Duration.ofSeconds(10))
                .get(10, TimeUnit.SECONDS);
        // Warmup JIT on this scope
        for (int i = 0; i < 20; i++) {
            breakdownScope.ping().get(5, TimeUnit.SECONDS);
        }

        int cmdIterations = 100;
        long cmdStart = System.nanoTime();
        for (int i = 0; i < cmdIterations; i++) {
            breakdownScope.watch(counterKey).get(5, TimeUnit.SECONDS);
            breakdownScope.get(counterKey).get(5, TimeUnit.SECONDS);
            breakdownScope.multi().get(5, TimeUnit.SECONDS);
            breakdownScope.set(counterKey, String.valueOf(i)).get(5, TimeUnit.SECONDS);
            breakdownScope.exec().get(5, TimeUnit.SECONDS);
        }
        long cmdElapsed = System.nanoTime() - cmdStart;
        double cmdMs = cmdElapsed / 1_000_000.0;
        double perTxMs = cmdMs / cmdIterations;
        double perCmdMs = perTxMs / 5.0;
        breakdownScope.close();

        System.out.printf("    %d transactions (5 cmds each) on held scope: %.1f ms total%n", cmdIterations, cmdMs);
        System.out.printf("    Per transaction (no acquire/release): %.2f ms%n", perTxMs);
        System.out.printf("    Per command: %.2f ms%n", perCmdMs);
        System.out.printf("    Acquire/release overhead per tx: ~%.2f ms%n", scenarioBPerOp - perTxMs);
        System.out.println();

        // --- Summary ---
        System.out.println("=== Summary ===");
        System.out.printf("    Fresh client per tx:  %.2f ms/tx%n", scenarioAPerOp);
        System.out.printf("    IsolatedScope:        %.2f ms/tx%n", scenarioBPerOp);
        System.out.printf("    Speedup:              %.1fx%n", scenarioAPerOp / scenarioBPerOp);
        System.out.println();

        // Cleanup
        sharedClient.del(new String[]{counterKey}).get(5, TimeUnit.SECONDS);
        sharedClient.close();
    }

    // ========== OCC Correctness Tests ==========

    /**
     * Demonstrates correct optimistic concurrency control:
     * Multiple threads increment a counter using WATCH/MULTI/EXEC.
     * With OCC, some transactions will abort (EXEC returns null) and must retry.
     * The final counter value must equal the total number of successful increments.
     */
    @Test
    public void testOCCConcurrentIncrement() throws Exception {
        System.out.println("\n=== OCC Test: Concurrent counter increment ===");

        GlideClient client = GlideClient.createClient(getConfig()).get(10, TimeUnit.SECONDS);
        String counterKey = "occ-counter-" + UUID.randomUUID();
        client.set(counterKey, "0").get(5, TimeUnit.SECONDS);

        int numThreads = 5;
        int incrementsPerThread = 20;
        int expectedFinal = numThreads * incrementsPerThread;

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger totalAttempts = new AtomicInteger(0);
        AtomicInteger totalAborts = new AtomicInteger(0);
        AtomicInteger totalSuccess = new AtomicInteger(0);

        // Pre-warm the scope pool with enough connections
        for (int i = 0; i < numThreads; i++) {
            IsolatedScope w = client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS);
            w.ping().get(5, TimeUnit.SECONDS);
            w.close();
        }
        Thread.sleep(200);

        for (int t = 0; t < numThreads; t++) {
            final int threadIdx = t;
            new Thread(() -> {
                try {
                    startGate.await(); // All threads start together

                    for (int i = 0; i < incrementsPerThread; i++) {
                        boolean committed = false;
                        while (!committed) {
                            totalAttempts.incrementAndGet();

                            try (IsolatedScope scope = client.scopedConnection(Duration.ofSeconds(10))
                                    .get(10, TimeUnit.SECONDS)) {

                                scope.watch(counterKey).get(5, TimeUnit.SECONDS);
                                String val = scope.get(counterKey).get(5, TimeUnit.SECONDS);
                                int current = Integer.parseInt(val);

                                scope.multi().get(5, TimeUnit.SECONDS);
                                scope.set(counterKey, String.valueOf(current + 1))
                                        .get(5, TimeUnit.SECONDS);
                                String execResult = scope.exec().get(5, TimeUnit.SECONDS);

                                if (execResult != null && !execResult.isEmpty()
                                        && !execResult.equals("null")) {
                                    committed = true;
                                    totalSuccess.incrementAndGet();
                                } else {
                                    // Transaction aborted — another thread modified the key
                                    totalAborts.incrementAndGet();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadIdx + " fatal error: " + e);
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            }, "OCC-Thread-" + t).start();
        }

        // Release all threads simultaneously
        startGate.countDown();

        assertTrue(doneLatch.await(60, TimeUnit.SECONDS), "All threads should finish within 60s");

        // Verify the counter is exactly correct
        String finalVal = client.get(counterKey).get(5, TimeUnit.SECONDS);
        int finalCount = Integer.parseInt(finalVal);

        System.out.printf("    Threads: %d, Increments/thread: %d%n", numThreads, incrementsPerThread);
        System.out.printf("    Expected final counter: %d%n", expectedFinal);
        System.out.printf("    Actual final counter:   %d%n", finalCount);
        System.out.printf("    Total attempts:         %d%n", totalAttempts.get());
        System.out.printf("    Successful commits:     %d%n", totalSuccess.get());
        System.out.printf("    Aborted (retried):      %d%n", totalAborts.get());
        System.out.printf("    Abort rate:             %.1f%%%n",
                100.0 * totalAborts.get() / totalAttempts.get());
        System.out.println();

        assertEquals(expectedFinal, finalCount,
                "Counter must equal exactly " + expectedFinal + " — OCC guarantees correctness");
        assertEquals(expectedFinal, totalSuccess.get(),
                "Total successful commits must equal expected increments");

        // Cleanup
        client.del(new String[]{counterKey}).get(5, TimeUnit.SECONDS);
        client.close();

        System.out.println("OCC concurrent increment test PASSED!");
        System.out.println("    (Contention caused " + totalAborts.get() + " retries across "
                + totalAttempts.get() + " attempts — OCC working correctly)");
    }

    /**
     * Demonstrates that WATCH correctly detects external modification.
     * Thread A watches a key, Thread B modifies it, Thread A's EXEC fails.
     */
    @Test
    public void testOCCConflictDetection() throws Exception {
        System.out.println("\n=== OCC Test: Conflict detection ===");

        GlideClient client = GlideClient.createClient(getConfig()).get(10, TimeUnit.SECONDS);
        String key = "occ-conflict-" + UUID.randomUUID();
        client.set(key, "original").get(5, TimeUnit.SECONDS);

        CountDownLatch aWatched = new CountDownLatch(1);
        CountDownLatch bModified = new CountDownLatch(1);
        AtomicInteger aExecResult = new AtomicInteger(-1); // -1=pending, 0=aborted, 1=committed

        // Thread A: WATCH, read, wait for B to modify, then MULTI/EXEC
        new Thread(() -> {
            try (IsolatedScope scope = client.scopedConnection(Duration.ofSeconds(10))
                    .get(10, TimeUnit.SECONDS)) {

                scope.watch(key).get(5, TimeUnit.SECONDS);
                String val = scope.get(key).get(5, TimeUnit.SECONDS);
                assertEquals("original", val);

                // Signal that we've watched
                aWatched.countDown();

                // Wait for Thread B to modify the key
                bModified.await(10, TimeUnit.SECONDS);

                // Now try to commit — should FAIL because Thread B modified the key
                scope.multi().get(5, TimeUnit.SECONDS);
                scope.set(key, "from-thread-a").get(5, TimeUnit.SECONDS);
                String execResult = scope.exec().get(5, TimeUnit.SECONDS);

                if (execResult == null || execResult.isEmpty() || execResult.equals("null")) {
                    aExecResult.set(0); // Aborted (expected!)
                } else {
                    aExecResult.set(1); // Committed (would be a bug)
                }
            } catch (Exception e) {
                System.err.println("Thread A error: " + e);
            }
        }, "OCC-A").start();

        // Thread B: wait for A to WATCH, then modify the key
        aWatched.await(10, TimeUnit.SECONDS);

        // Modify the watched key through the shared multiplexed client
        client.set(key, "modified-by-b").get(5, TimeUnit.SECONDS);
        bModified.countDown();

        // Wait for Thread A to finish
        Thread.sleep(2000);

        // Verify: Thread A's EXEC must have been aborted
        assertEquals(0, aExecResult.get(),
                "Thread A's EXEC should be ABORTED because Thread B modified the watched key");

        // Verify: the key has Thread B's value (Thread A's write was rejected)
        String finalVal = client.get(key).get(5, TimeUnit.SECONDS);
        assertEquals("modified-by-b", finalVal,
                "Key should have Thread B's value since Thread A's transaction was aborted");

        // Cleanup
        client.del(new String[]{key}).get(5, TimeUnit.SECONDS);
        client.close();

        System.out.println("OCC conflict detection test PASSED!");
        System.out.println("    (WATCH correctly detected external modification → EXEC aborted)");
    }
}
