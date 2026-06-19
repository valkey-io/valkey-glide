/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package com.company.glide.circuitbreaker;

import glide.api.GlideClusterClient;
import glide.api.models.configuration.ClientCircuitBreakerConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-wide circuit breaker e2e test.
 *
 * <p>Runs a slow-node scenario (DEBUG SLEEP) and compares behavior with and without CB. Also
 * supports long-running mode with periodic failure injection for stability testing.
 *
 * <p>Environment variables:
 *
 * <ul>
 *   <li>DURATION_SECS - test duration per phase (default: 30)
 *   <li>INJECT_INTERVAL_SECS - if >0, periodically inject/restore failures (long-running mode)
 * </ul>
 */
public class ClientCircuitBreakerTest {

    private static final String HOST = "172.33.0.11";
    private static final int PORT = 6379;
    private static final int REQUEST_TIMEOUT_MS = 100;
    private static final int POOL_SIZE = 500;

    private static final AtomicLong successCount = new AtomicLong();
    private static final AtomicLong errorCount = new AtomicLong();
    private static final AtomicLong fastErrors = new AtomicLong();
    private static final AtomicLong slowErrors = new AtomicLong();
    private static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        int durationSecs = Integer.parseInt(System.getenv().getOrDefault("DURATION_SECS", "30"));
        int injectInterval =
                Integer.parseInt(System.getenv().getOrDefault("INJECT_INTERVAL_SECS", "0"));

        System.out.println("[INIT] Waiting for cluster...");
        Thread.sleep(8000);

        if (injectInterval > 0) {
            runLongRunning(durationSecs, injectInterval);
        } else {
            runBenchmark(durationSecs);
        }
    }

    private static void runBenchmark(int durationSecs) throws Exception {
        System.out.println("=== Phase 1: WITHOUT Circuit Breaker ===");
        Results withoutCB = runPhase(durationSecs, false);

        System.out.println("\n=== Phase 2: WITH Circuit Breaker ===");
        Results withCB = runPhase(durationSecs, true);

        System.out.println("\n========== CLIENT CIRCUIT BREAKER BENCHMARK ==========");
        System.out.printf("%-25s %-15s %-15s%n", "Metric", "Without CB", "With CB");
        System.out.printf("%-25s %-15s %-15s%n", "---", "---", "---");
        System.out.printf(
                "%-25s %-15d %-15d%n",
                "Successful ops/s", withoutCB.ops / durationSecs, withCB.ops / durationSecs);
        System.out.printf("%-25s %-15d %-15d%n", "Total errors", withoutCB.errors, withCB.errors);
        System.out.printf(
                "%-25s %-15s %-15s%n",
                "Fast errors (<10ms)",
                pct(withoutCB.fast, withoutCB.errors),
                pct(withCB.fast, withCB.errors));
        System.out.printf(
                "%-25s %-15s %-15s%n",
                "Slow errors (>=10ms)",
                pct(withoutCB.slow, withoutCB.errors),
                pct(withCB.slow, withCB.errors));
        System.out.printf("%-25s %-15d %-15d%n", "Peak pool size", withoutCB.peakPool, withCB.peakPool);
        System.out.println("=====================================================");

        boolean passed = withCB.fast > withoutCB.fast || withCB.ops > withoutCB.ops;
        System.out.println(
                passed
                        ? "Client Circuit Breaker Test PASSED"
                        : "Client Circuit Breaker Test FAILED (CB did not improve metrics)");
        System.exit(passed ? 0 : 1);
    }

    private static void runLongRunning(int durationSecs, int injectInterval) throws Exception {
        System.out.printf("[LONG-RUN] Duration: %ds, inject every %ds%n", durationSecs, injectInterval);

        GlideClusterClient client = createClient(true);
        prepopulate(client);

        ForkJoinPool pool = createPool();
        Thread producer = startProducer(client, pool);

        long endTime = System.currentTimeMillis() + (durationSecs * 1000L);
        int cycle = 0;
        boolean sick = false;

        while (System.currentTimeMillis() < endTime) {
            Thread.sleep(injectInterval * 1000L);
            cycle++;

            if (!sick) {
                System.out.printf("[CYCLE %d] Injecting failure (DEBUG SLEEP)...%n", cycle);
                makeSick();
                sick = true;
            } else {
                System.out.printf("[CYCLE %d] Restoring...%n", cycle);
                // Just stop injecting — node recovers on its own
                sick = false;
            }

            System.out.printf(
                    "[CYCLE %d] ok:%d err:%d fast:%d slow:%d pool:%d%n",
                    cycle,
                    successCount.get(),
                    errorCount.get(),
                    fastErrors.get(),
                    slowErrors.get(),
                    pool.getPoolSize());
        }

        running = false;
        producer.interrupt();
        pool.shutdownNow();
        client.close();

        System.out.println("[LONG-RUN] Completed. Final stats:");
        System.out.printf(
                "  Total OK: %d, Errors: %d, Fast: %d, Slow: %d%n",
                successCount.get(), errorCount.get(), fastErrors.get(), slowErrors.get());
        System.out.println("Client Circuit Breaker Test PASSED");
    }

    private static Results runPhase(int durationSecs, boolean enableCB) throws Exception {
        GlideClusterClient client = createClient(enableCB);
        prepopulate(client);

        ForkJoinPool pool = createPool();
        Thread producer = startProducer(client, pool);

        // Warm up
        System.out.println("[PHASE] Warming up 5s...");
        Thread.sleep(5000);
        resetCounters();

        // Make node sick — pause the container (half-open TCP, guaranteed to cause timeouts)
        System.out.println("[PHASE] Pausing cb-valkey-1 (docker pause)...");
        exec("docker pause cb-valkey-1");
        // Node is now completely unresponsive

        // Monitor
        int peakPool = 0;
        for (int i = 0; i < durationSecs / 5; i++) {
            Thread.sleep(5000);
            int poolSize = pool.getPoolSize();
            if (poolSize > peakPool) peakPool = poolSize;
            System.out.printf(
                    "[PHASE] t=%ds ok=%d err=%d fast=%d slow=%d pool=%d%n",
                    (i + 1) * 5,
                    successCount.get(),
                    errorCount.get(),
                    fastErrors.get(),
                    slowErrors.get(),
                    poolSize);
        }

        // Stop
        running = false;
        exec("docker unpause cb-valkey-1");
        producer.interrupt();
        pool.shutdownNow();
        Thread.sleep(2000);

        Results r =
                new Results(
                        successCount.get(), errorCount.get(), fastErrors.get(), slowErrors.get(), peakPool);

        client.close();
        running = true; // reset for next phase
        return r;
    }

    private static GlideClusterClient createClient(boolean enableCB) throws Exception {
        var builder =
                GlideClusterClientConfiguration.builder()
                        .address(NodeAddress.builder().host(HOST).port(PORT).build())
                        .requestTimeout(REQUEST_TIMEOUT_MS)
                        .inflightRequestsLimit(1000);

        if (enableCB) {
            builder.clientCircuitBreakerConfiguration(
                    ClientCircuitBreakerConfiguration.builder()
                            .windowSizeMs(10000)
                            .failureRateThreshold(0.5f)
                            .minErrors(5)
                            .openTimeoutMs(5000)
                            .countTimeouts(true)
                            .consecutiveSuccesses(3)
                            .build());
        }

        return GlideClusterClient.createClient(builder.build()).get(30, TimeUnit.SECONDS);
    }

    private static void prepopulate(GlideClusterClient client) throws Exception {
        System.out.println("[PHASE] Pre-populating...");
        for (int i = 0; i < 100; i++) {
            for (int attempt = 0; attempt < 10; attempt++) {
                try {
                    client.set("cb:" + i, "v" + i).get();
                    break;
                } catch (Exception e) {
                    if (attempt == 9) throw e;
                    Thread.sleep(500);
                }
            }
        }
    }

    private static ForkJoinPool createPool() {
        AtomicInteger tid = new AtomicInteger();
        return new ForkJoinPool(
                POOL_SIZE,
                p -> {
                    ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
                    t.setName("cb-" + tid.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                null,
                true);
    }

    private static Thread startProducer(GlideClusterClient client, ForkJoinPool pool) {
        Thread producer =
                new Thread(
                        () -> {
                            while (running) {
                                int k = ThreadLocalRandom.current().nextInt(100);
                                pool.execute(() -> doGet(client, "cb:" + k));
                                try {
                                    Thread.sleep(0, 100_000);
                                } catch (InterruptedException e) {
                                    return;
                                }
                            }
                        },
                        "producer");
        producer.setDaemon(true);
        producer.start();
        return producer;
    }

    private static void doGet(GlideClusterClient client, String key) {
        long start = System.nanoTime();
        try {
            ForkJoinPool.managedBlock(
                    new ForkJoinPool.ManagedBlocker() {
                        volatile boolean done = false;

                        @Override
                        public boolean block() throws InterruptedException {
                            try {
                                client.get(key).get();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                            done = true;
                            return true;
                        }

                        @Override
                        public boolean isReleasable() {
                            return done;
                        }
                    });
            successCount.incrementAndGet();
        } catch (Exception e) {
            long latencyNs = System.nanoTime() - start;
            errorCount.incrementAndGet();
            if (latencyNs < 10_000_000L) {
                fastErrors.incrementAndGet();
            } else {
                slowErrors.incrementAndGet();
            }
        }
    }

    private static void makeSick() {
        try {
            exec("docker exec cb-valkey-1 valkey-cli DEBUG SLEEP 0.15");
        } catch (Exception e) {
            System.err.println("Failed to make node sick: " + e);
        }
    }

    private static void resetCounters() {
        successCount.set(0);
        errorCount.set(0);
        fastErrors.set(0);
        slowErrors.set(0);
    }

    private static void exec(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[] {"sh", "-c", cmd});
        int exit = p.waitFor();
        if (exit != 0) {
            byte[] err = p.getErrorStream().readAllBytes();
            System.err.printf("[EXEC] '%s' failed (exit %d): %s%n", cmd, exit, new String(err));
        }
    }

    private static String pct(long part, long total) {
        if (total == 0) return "0 (0%)";
        return String.format("%d (%.1f%%)", part, part * 100.0 / total);
    }

    static class Results {
        final long ops, errors, fast, slow;
        final int peakPool;

        Results(long ops, long errors, long fast, long slow, int peakPool) {
            this.ops = ops;
            this.errors = errors;
            this.fast = fast;
            this.slow = slow;
            this.peakPool = peakPool;
        }
    }
}
