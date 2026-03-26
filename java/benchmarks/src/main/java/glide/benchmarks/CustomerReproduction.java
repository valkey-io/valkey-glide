/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks;

import glide.benchmarks.BenchmarkingApp.RunConfiguration;
import glide.benchmarks.clients.glide.GlideAsyncClient;
import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Reproduces customer issue:
 * - Event consumer processing messages from a topic
 * - 60 worker threads each doing: del(keysArray).get() in a loop
 * - requestTimeout configured as 1000ms in GLIDE client config
 * - Keys can span different hash slots
 *
 * Customer reports:
 * - All 60 threads get stuck on del().get() — timeout NOT honored
 * - Unbounded queue fills up → OOM
 * - Happens across multiple pods simultaneously
 *
 * Usage:
 *   ./gradlew :benchmarks:run --args="--mode customer-repro \
 *       --host <host> --port 6379 --tls --clusterModeEnabled \
 *       --duration 18000 --workerThreads 60 \
 *       --delKeysPerEvent 3 --delTimeoutMs 1000 \
 *       --warmupKeys 100000 --dataSize 100"
 */
public class CustomerReproduction {

    private static final int KEY_SIZE = 2000;
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // Metrics
    private static final AtomicLong totalSubmitted = new AtomicLong(0);
    private static final AtomicLong totalCompleted = new AtomicLong(0);
    private static final AtomicLong totalTimeouts = new AtomicLong(0);
    private static final AtomicLong totalErrors = new AtomicLong(0);
    private static final AtomicLong totalStuckDetections = new AtomicLong(0);
    private static final LongAdder intervalCompleted = new LongAdder();
    private static final LongAdder intervalTimeouts = new LongAdder();
    private static final LongAdder intervalErrors = new LongAdder();
    private static final LongAdder intervalLatencySum = new LongAdder();
    private static final AtomicLong maxLatencyMs = new AtomicLong(0);

    public static void run(RunConfiguration config) throws Exception {
        int workerThreads = config.workerThreads;
        int delKeysPerEvent = config.delKeysPerEvent;
        long delTimeoutMs = config.delTimeoutMs;
        long durationSeconds = config.durationSeconds > 0 ? config.durationSeconds : 3600;
        int warmupKeys = config.warmupKeyCount;
        int dataSize = config.dataSize.length > 0 ? config.dataSize[0] : 100;

        // ==================== PRINT CONFIG ====================
        System.out.println("\n" + "=".repeat(60));
        System.out.println("CUSTOMER REPRODUCTION BENCHMARK");
        System.out.println("=".repeat(60));
        System.out.printf("Host:              %s:%d (TLS: %s, Cluster: %s)%n",
                config.host, config.port, config.tls, config.clusterModeEnabled);
        System.out.printf("Worker threads:    %d (all active, looping)%n", workerThreads);
        System.out.printf("DEL keys/event:    %d (cross-slot)%n", delKeysPerEvent);
        System.out.printf("DEL timeout:       %dms (requestTimeout in GLIDE config)%n", delTimeoutMs);
        System.out.printf("DEL .get():        BARE .get() — NO explicit timeout (like customer)%n");
        System.out.printf("Duration:          %ds (%.1f hours)%n",
                durationSeconds, durationSeconds / 3600.0);
        System.out.printf("Warmup keys:       %d%n", warmupKeys);
        System.out.printf("Data size:         %d bytes%n", dataSize);
        System.out.println("-".repeat(60));
        System.out.println("CUSTOMER PATTERN:");
        System.out.println("  60 threads, each looping: del(keys).get() → next del()");
        System.out.println("  All threads stay busy. No rate limiting.");
        System.out.println("  If del().get() hangs → thread blocks → TPS drops to 0");
        System.out.println("=".repeat(60));

        // ==================== CREATE CLIENT ====================
        System.out.println("\nCreating GLIDE client...");
        GlideAsyncClient glideClient = new GlideAsyncClient();
        glideClient.connectToValkey(new ConnectionSettings(
                config.host, config.port, config.tls, config.clusterModeEnabled, config.tcpNoDelay, config.requestTimeoutMs));
        System.out.println("Connected!");

        // ==================== WARMUP ====================
        System.out.printf("\nWarming up %d keys...%n", warmupKeys);
        String value = "0".repeat(dataSize);
        for (int i = 1; i <= warmupKeys; i++) {
            glideClient.asyncSet(padKey(i), value).get();
            if (i % 10000 == 0) {
                System.out.printf("  Warmup: %,d/%,d (%.0f%%)%n",
                        i, warmupKeys, i * 100.0 / warmupKeys);
            }
        }
        System.out.println("Warmup complete!\n");

        // ==================== EXECUTOR ====================
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                workerThreads,
                workerThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
        );

        // ==================== MONITOR (every 5 seconds) ====================
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "repro-monitor");
            t.setDaemon(true);
            return t;
        });

        final long startTimeNanos = System.nanoTime();
        final long pid = ProcessHandle.current().pid();
        final int monitorIntervalSec = 5;

        monitor.scheduleAtFixedRate(() -> {
            try {
                double elapsedSec = (System.nanoTime() - startTimeNanos) / 1e9;
                long submitted = totalSubmitted.get();
                long completed = totalCompleted.get();
                long timeouts = totalTimeouts.get();
                long errors = totalErrors.get();
                int queueDepth = executor.getQueue().size();
                int activeThreads = executor.getActiveCount();

                long intCompleted = intervalCompleted.sumThenReset();
                long intTimeouts = intervalTimeouts.sumThenReset();
                long intErrors = intervalErrors.sumThenReset();
                long intLatencySum = intervalLatencySum.sumThenReset();
                double avgLatencyMs = intCompleted > 0
                        ? (intLatencySum / (double) intCompleted) / 1_000_000.0
                        : 0;
                double tps = intCompleted / (double) monitorIntervalSec;

                long rssKb = getRssKb(pid);
                long maxLat = maxLatencyMs.getAndSet(0);

                // Stuck detection: all threads busy but TPS is 0
                String stuckInfo = "";
                if (activeThreads == workerThreads && intCompleted == 0 && submitted > 100) {
                    long stuckCount = totalStuckDetections.incrementAndGet();
                    stuckInfo = String.format(" | 🔴 ALL %d THREADS STUCK! NO PROGRESS! x%d",
                            workerThreads, stuckCount);
                }

                // Low TPS warning
                String tpsWarning = "";
                if (tps > 0 && tps < 100 && submitted > 1000) {
                    tpsWarning = " ⚠️ LOW TPS!";
                }

                System.out.printf(
                        "[%s] %.0fs | Active: %d/%d | TPS: %.0f%s"
                                + " | Submitted: %,d | Done: %,d | Timeouts: %,d | Errors: %,d"
                                + " | AvgLat: %.1fms | MaxLat: %dms | RSS: %dMB%s%n",
                        LocalDateTime.now().format(TS),
                        elapsedSec,
                        activeThreads, workerThreads,
                        tps, tpsWarning,
                        submitted,
                        completed,
                        timeouts,
                        errors,
                        avgLatencyMs,
                        maxLat,
                        rssKb / 1024,
                        stuckInfo);

            } catch (Exception e) {
                System.err.println("Monitor error: " + e.getMessage());
            }
        }, monitorIntervalSec, monitorIntervalSec, TimeUnit.SECONDS);

        // ==================== STUCK THREAD DETECTOR (every 1 second) ====================
        ScheduledExecutorService stuckDetector = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stuck-detector");
            t.setDaemon(true);
            return t;
        });

        final AtomicLong lastCompletedSnapshot = new AtomicLong(0);

        stuckDetector.scheduleAtFixedRate(() -> {
            long currentCompleted = totalCompleted.get();
            long lastCompleted = lastCompletedSnapshot.getAndSet(currentCompleted);
            int activeThreads = executor.getActiveCount();

            // No progress in the last second and all threads are active
            if (currentCompleted == lastCompleted && activeThreads == workerThreads
                    && totalSubmitted.get() > 100) {
                System.err.printf(
                        "[%s] 🔴 STUCK: ALL %d threads busy, ZERO completions in last 1s | "
                                + "Total done: %,d | Timeouts: %,d%n",
                        LocalDateTime.now().format(TS),
                        workerThreads,
                        currentCompleted,
                        totalTimeouts.get());
            }
        }, 10, 1, TimeUnit.SECONDS);

        // ==================== WORKERS — ALL 60 THREADS LOOP INDEPENDENTLY ====================
        // Each thread continuously does: del(keys).get() → loop
        // This keeps all 60 threads active at all times
        // No external producer, no queue — just pure DEL throughput

        final long endTimeNanos = System.nanoTime() + (durationSeconds * 1_000_000_000L);
        final int finalDelKeysPerEvent = delKeysPerEvent;
        final int finalWarmupKeys = warmupKeys;

        CountDownLatch allWorkersDone = new CountDownLatch(workerThreads);

        for (int t = 0; t < workerThreads; t++) {
            final int threadNum = t;
            executor.submit(() -> {
                try {
                    while (System.nanoTime() < endTimeNanos && !Thread.currentThread().isInterrupted()) {
                        totalSubmitted.incrementAndGet();
                        long startNanos = System.nanoTime();
                        try {
                            // Generate keys that span DIFFERENT hash slots
                            // Customer confirmed: "keys can map to different hash slots"
                            String[] keys = new String[finalDelKeysPerEvent];
                            for (int k = 0; k < finalDelKeysPerEvent; k++) {
                                keys[k] = padKey((int) (Math.random() * finalWarmupKeys) + 1);
                            }

                            // ==================== THE CRITICAL LINE ====================
                            // Customer does: Long deleted = glideClient.del(keysArray).get();
                            // BARE .get() — no explicit timeout!
                            Long deleted = ((AsyncClient<String>) glideClient).asyncDel(keys).get();
                            // ===========================================================

                            long elapsedNanos = System.nanoTime() - startNanos;
                            long elapsedMs = elapsedNanos / 1_000_000;

                            totalCompleted.incrementAndGet();
                            intervalCompleted.increment();
                            intervalLatencySum.add(elapsedNanos);

                            // Track max latency
                            long currentMax = maxLatencyMs.get();
                            while (elapsedMs > currentMax) {
                                if (maxLatencyMs.compareAndSet(currentMax, elapsedMs)) break;
                                currentMax = maxLatencyMs.get();
                            }

                            // Warn if DEL took longer than requestTimeout
                            if (elapsedMs > 2000) {
                                System.err.printf(
                                        "[%s] ⚠️ SLOW DEL: %dms (thread-%d) | keys=%d | deleted=%d%n",
                                        LocalDateTime.now().format(TS),
                                        elapsedMs, threadNum, keys.length, deleted);
                            }

                        } catch (ExecutionException e) {
                            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

                            // Check if the ExecutionException wraps a timeout from GLIDE
                            boolean isTimeout = e.getCause() != null && (
                                    e.getCause() instanceof glide.api.models.exceptions.TimeoutException
                                    || (e.getCause().getMessage() != null
                                        && e.getCause().getMessage().toLowerCase().contains("timeout")));

                            if (isTimeout) {
                                totalTimeouts.incrementAndGet();
                                intervalTimeouts.increment();
                                if (totalTimeouts.get() <= 10 || elapsedMs > 2000) {
                                    System.err.printf("[%s] TIMEOUT after %dms (thread-%d) | %s%n",
                                            LocalDateTime.now().format(TS),
                                            elapsedMs, threadNum,
                                            e.getCause().getMessage());
                                }
                            } else {
                                totalErrors.incrementAndGet();
                                intervalErrors.increment();

                                String cause = e.getCause() != null
                                        ? e.getCause().getClass().getSimpleName() + ": "
                                            + e.getCause().getMessage()
                                        : e.getMessage();

                                if (totalErrors.get() <= 10 || elapsedMs > 2000) {
                                    System.err.printf("[%s] DEL ERROR after %dms (thread-%d) | %s%n",
                                            LocalDateTime.now().format(TS),
                                            elapsedMs, threadNum, cause);
                                }
                            }

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
                            totalErrors.incrementAndGet();
                            intervalErrors.increment();

                            if (totalErrors.get() <= 10) {
                                System.err.printf(
                                        "[%s] UNEXPECTED ERROR after %dms (thread-%d) | %s: %s%n",
                                        LocalDateTime.now().format(TS),
                                        elapsedMs, threadNum,
                                        e.getClass().getSimpleName(),
                                        e.getMessage());
                            }
                        }
                    }
                } finally {
                    allWorkersDone.countDown();
                }
            });
        }

        // ==================== WAIT FOR DURATION ====================
        System.out.printf("All %d workers started. Running for %d seconds...%n%n",
                workerThreads, durationSeconds);

        try {
            allWorkersDone.await(durationSeconds + 60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println("Interrupted — shutting down.");
        }

        // ==================== SHUTDOWN ====================
        System.out.println("\nShutting down...");
        stuckDetector.shutdownNow();
        monitor.shutdownNow();

        int finalActiveThreads = executor.getActiveCount();
        executor.shutdownNow();

        // ==================== FINAL REPORT ====================
        double totalSec = (System.nanoTime() - startTimeNanos) / 1e9;
        long submitted = totalSubmitted.get();
        long completed = totalCompleted.get();
        long timeouts = totalTimeouts.get();
        long errors = totalErrors.get();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("CUSTOMER REPRODUCTION — FINAL RESULTS");
        System.out.println("=".repeat(60));
        System.out.printf("Duration:            %.1f seconds (%.2f hours)%n",
                totalSec, totalSec / 3600);
        System.out.printf("Events submitted:    %,d%n", submitted);
        System.out.printf("DELs completed:      %,d%n", completed);
        System.out.printf("Timeouts:            %,d%n", timeouts);
        System.out.printf("Errors:              %,d%n", errors);
        System.out.printf("Final active threads: %d/%d%n", finalActiveThreads, workerThreads);
        System.out.printf("Stuck detections:    %,d%n", totalStuckDetections.get());
        System.out.printf("Avg TPS:             %.0f%n",
                completed > 0 ? completed / totalSec : 0);
        System.out.printf("RSS:                 %d MB%n", getRssKb(pid) / 1024);
        System.out.println("-".repeat(60));

        // ==================== ANALYSIS ====================
        if (totalStuckDetections.get() > 0) {
            System.out.println("\n🔴 CUSTOMER ISSUE REPRODUCED!");
            System.out.printf("   All %d threads were stuck %d times.%n",
                    workerThreads, totalStuckDetections.get());
            System.out.println("   del().get() blocked threads — no progress detected.");
            System.out.println();
            System.out.println("   POSSIBLE ROOT CAUSES:");
            System.out.println("   1. Multi-slot DEL: one sub-command hangs → entire future hangs");
            System.out.println("   2. .get() doesn't honor requestTimeout → blocks forever");
            System.out.println("   3. Connection stall under load");
        } else if (timeouts > 0) {
            System.out.println("\n⚠️  Timeouts occurred but threads recovered.");
            System.out.printf("   %,d timeouts out of %,d operations (%.2f%%)%n",
                    timeouts, submitted, timeouts * 100.0 / submitted);
            System.out.println("   requestTimeout IS working — threads aren't permanently stuck.");
        } else {
            System.out.println("\n✅ GLIDE handled all DELs successfully.");
            System.out.println("   No stuck threads, no timeouts, no errors.");
            System.out.println("   Customer's issue did not reproduce under these conditions.");
        }
        System.out.println("=".repeat(60) + "\n");

        glideClient.closeConnection();
    }

    // ==================== HELPERS ====================

    private static String padKey(int keyNum) {
        String base = String.valueOf(keyNum);
        if (base.length() >= KEY_SIZE) {
            return base.substring(0, KEY_SIZE);
        }
        return base + "0".repeat(KEY_SIZE - base.length());
    }

    private static long getRssKb(long pid) {
        try {
            String status = new String(
                    java.nio.file.Files.readAllBytes(
                            java.nio.file.Paths.get("/proc/" + pid + "/status")));
            for (String line : status.split("\n")) {
                if (line.startsWith("VmRSS:")) {
                    return Long.parseLong(line.split("\\s+")[1]);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }
}