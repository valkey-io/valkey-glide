/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.utils;
import java.util.Arrays;
import glide.api.GlideClusterClient;
import glide.benchmarks.clients.glide.GlideAsyncClient;


import com.google.common.util.concurrent.RateLimiter;
import glide.benchmarks.BenchmarkingApp.RunConfiguration;
import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.clients.Client;
import glide.benchmarks.clients.SyncClient;
import java.io.File;
import java.util.stream.Collectors;
import java.util.concurrent.Future;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;

/** Class to calculate latency on client-actions */
public class Benchmarking {
    static final double PROB_GET = 0.8;
    static final double PROB_GET_EXISTING_KEY = 0.8;
    static final int SIZE_GET_KEYSPACE = 3750000;
    static final int SIZE_SET_KEYSPACE = 3000000;
    public static final double NANO_TO_SECONDS = 1e9;
    static final int KEY_SIZE = 2000; // 2KB keys
    private static int availableKeyCount = SIZE_SET_KEYSPACE;

    private static ChosenAction randomAction(OperationType operationType) {
        switch (operationType) {
            case READ_ONLY:
                // Only GET operations
                // if (Math.random() > PROB_GET_EXISTING_KEY) {
                //     return ChosenAction.GET_NON_EXISTING;
                // }
                return ChosenAction.GET_EXISTING;

            case WRITE_ONLY:
                // Only SET operations
                return ChosenAction.SET;
            
            case DELETE_ONLY:
                // Only DEL operations
                return ChosenAction.DEL;

            case ALL:
            default:
                // Original behavior
                if (Math.random() > PROB_GET) {
                    return ChosenAction.SET;
                }
                if (Math.random() > PROB_GET_EXISTING_KEY) {
                    return ChosenAction.GET_NON_EXISTING;
                }
                return ChosenAction.GET_EXISTING;
        }
    }

    /** Pre-populates data in Valkey for read-only benchmarks. */
    public static void warmupData(
            List<Client> clients, int dataSize, int keyCount, boolean async, boolean debugLogging) {

        // SET THE AVAILABLE KEY COUNT FOR READS
        availableKeyCount = keyCount;

        System.out.printf(
                "Pre-populating %d keys (2KB each) with %d byte values...%n", keyCount, dataSize);
        String value = "0".repeat(dataSize);
        long startTime = System.nanoTime();

        for (int i = 1; i <= keyCount; i++) {
            final Client client = clients.get(i % clients.size());
            final String key = padKey(i);

            try {
                if (async) {
                    ((AsyncClient<String>) client).asyncSet(key, value).get();
                } else {
                    ((SyncClient) client).set(key, value);
                }
            } catch (Exception e) {
                System.err.println("Warmup error for key " + i + ": " + e.getMessage());
            }

            if (i % 10000 == 0) {
                System.out.printf(
                        "Warmup progress: %,d/%,d keys (%.1f%%)%n", i, keyCount, (i * 100.0 / keyCount));
            }
        }

        long elapsed = System.nanoTime() - startTime;
        System.out.printf(
                "Warmup complete: %d keys in %.2f seconds (%.0f keys/sec)%n",
                keyCount, elapsed / NANO_TO_SECONDS, keyCount * NANO_TO_SECONDS / elapsed);
    }

    // Add at top of class
    public static String generateKeyGet() {
        int range = SIZE_GET_KEYSPACE - SIZE_SET_KEYSPACE;
        int keyNum = (int) (Math.floor(Math.random() * range + SIZE_SET_KEYSPACE + 1));
        return padKey(keyNum);
    }

    public static String generateKeySet() {
        int keyNum = (int) (Math.floor(Math.random() * SIZE_SET_KEYSPACE) + 1);
        return padKey(keyNum);
    }

    public static String generateKeyForRead() {
        int keyNum = (int) (Math.floor(Math.random() * availableKeyCount) + 1);
        return padKey(keyNum);
    }

    private static String padKey(int keyNum) {
        String base = String.valueOf(keyNum);
        if (base.length() >= KEY_SIZE) {
            return base.substring(0, KEY_SIZE);
        }
        // Pad with zeros to reach 2KB
        return base + "0".repeat(KEY_SIZE - base.length());
    }

    /** Check if an exception is a timeout error from Glide */
    private static boolean isTimeoutException(Throwable e) {
        if (e == null) return false;

        // Check the exception message for timeout indicators
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("timeout")
                    || lowerMessage.contains("timed out")
                    || lowerMessage.contains("request timed out")) {
                return true;
            }
        }

        // Check if it's a TimeoutException
        if (e instanceof java.util.concurrent.TimeoutException
                || e instanceof glide.api.models.exceptions.TimeoutException) {
            return true;
        }

        // Check the cause recursively
        if (e.getCause() != null && e.getCause() != e) {
            return isTimeoutException(e.getCause());
        }

        return false;
    }

    public interface Operation {
        void go(Client client) throws InterruptedException, ExecutionException;
    }

    public static Pair<ChosenAction, Long> measurePerformance(
            Client client,
            Map<ChosenAction, Operation> actions,
            OperationType operationType,
            MetricsCsvWriter metricsWriter) {
        var action = randomAction(operationType);
        long before = System.nanoTime();
        boolean success = false;
        boolean isTimeout = false;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            actions.get(action).go(client);
            success = true;
        } catch (glide.api.models.exceptions.TimeoutException e) {
            isTimeout = true;
            // System.err.println(
            //         "["
            //                 + LocalDateTime.now().format(formatter)
            //                 + "] ERROR [TimeoutException] Action="
            //                 + action
            //                 + " | Message="
            //                 + e.getMessage());
        } catch (ExecutionException e) {
            if (isTimeoutException(e)) {
                isTimeout = true;
                // System.err.println(
                //         "["
                //                 + LocalDateTime.now().format(formatter)
                //                 + "] ERROR [TimeoutException] Action="
                //                 + action
                //                 + " | Message="
                //                 + e.getMessage());
            } else if (e.getMessage() != null && e.getMessage().contains("maximum inflight requests")) {
                // Suppress spam when inflight limit < concurrent tasks
            } else {
                System.err.println(
                        "["
                                + LocalDateTime.now().format(formatter)
                                + "] ERROR [ExecutionException] Action="
                                + action
                                + " | Message="
                                + e.getMessage());
            }
        } catch (InterruptedException e) {
            System.err.println(
                    "["
                            + LocalDateTime.now().format(formatter)
                            + "] ERROR [InterruptedException] Action="
                            + action
                            + " | Message="
                            + e.getMessage());
            if (Thread.currentThread().isInterrupted()) {
                Thread.interrupted();
            }
            throw new RuntimeException("The thread was interrupted", e);
        } catch (Exception e) {
            System.err.println(
                    "["
                            + LocalDateTime.now().format(formatter)
                            + "] ERROR ["
                            + e.getClass().getSimpleName()
                            + "] Action="
                            + action
                            + " | Message="
                            + e.getMessage());
        }
        long after = System.nanoTime();
        long latencyNanos = after - before;
        if (metricsWriter != null) {
            if (success) {
                metricsWriter.recordSuccess(latencyNanos, action);
            } else if (isTimeout) {
                metricsWriter.recordTimeoutError();
            } else {
                metricsWriter.recordOtherError();
            }
        }
        return Pair.of(action, latencyNanos);
    }

    public static Map<ChosenAction, LatencyResults> calculateResults(
            Map<ChosenAction, List<Long>> actionLatencies) {
        Map<ChosenAction, LatencyResults> results = new HashMap<>();

        for (Map.Entry<ChosenAction, List<Long>> entry : actionLatencies.entrySet()) {
            ChosenAction action = entry.getKey();
            double[] latencies = entry.getValue().stream().mapToDouble(Long::doubleValue).toArray();

            if (latencies.length != 0) {
                results.put(action, new LatencyResults(latencies));
            }
        }

        return results;
    }

    public static void printResults(
            Map<ChosenAction, LatencyResults> resultsMap, double duration, int iterations) {
        System.out.printf("Runtime (sec): %.3f%n", duration);
        System.out.printf("Iterations: %d%n", iterations);
        System.out.printf("TPS: %d%n", (int) (iterations / duration));
        int totalRequests = 0;
        for (Map.Entry<ChosenAction, LatencyResults> entry : resultsMap.entrySet()) {
            ChosenAction action = entry.getKey();
            LatencyResults results = entry.getValue();

            System.out.printf("===> %s <===%n", action);
            System.out.printf("avg. latency (ms): %.3f%n", results.avgLatency);
            System.out.printf("std dev (ms): %.3f%n", results.stdDeviation);
            System.out.printf("p50 latency (ms): %.3f%n", results.p50Latency);
            System.out.printf("p90 latency (ms): %.3f%n", results.p90Latency);
            System.out.printf("p99 latency (ms): %.3f%n", results.p99Latency);
            System.out.printf("Total requests: %d%n", results.totalRequests);
            totalRequests += results.totalRequests;
        }
        System.out.println("Total requests: " + totalRequests);
    }

    /**
 * Starts a single canary thread that executes a Lua script sleeping ~1 second,
 * once per minute. With a 10ms requestTimeout, this MUST produce a TimeoutException.
 * If DEL commands hang without timeout but this correctly times out,
 * it proves the timeout mechanism doesn't cover multi-slot command aggregation.
 */
public static Thread startCanaryThread(Client client, boolean async) {
    Thread canary = new Thread(() -> {
        DateTimeFormatter ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        // Lua script that busy-waits for ~200ms using server TIME (seconds + microseconds)
        String luaScript =
            "local start = redis.call('TIME') " +
            "local s0 = tonumber(start[1]) " +
            "local us0 = tonumber(start[2]) " +
            "while true do " +
            "  local now = redis.call('TIME') " +
            "  local elapsed_us = (tonumber(now[1]) - s0) * 1000000 + (tonumber(now[2]) - us0) " +
            "  if elapsed_us >= 200000 then break end " +
            "end " +
            "return 'canary_done'";

        while (!Thread.currentThread().isInterrupted()) {
            long startMs = System.currentTimeMillis();
            try {
                System.out.printf("[%s] [CANARY] Executing 1s Lua script (should timeout at 10ms)...%n",
                        LocalDateTime.now().format(ts));

                CompletableFuture<?> future =
                        ((GlideClusterClient) ((GlideAsyncClient) client).getGlideClient())
                                .customCommand(new String[]{"EVAL", luaScript, "0"});

                Object result = future.get(30, TimeUnit.SECONDS);

                // If we get here, timeout did NOT fire — that's a problem
                long elapsed = System.currentTimeMillis() - startMs;
                System.err.printf("[%s] [CANARY] ⚠️ NO TIMEOUT! Lua completed in %dms, result=%s%n",
                        LocalDateTime.now().format(ts), elapsed, result);

            } catch (ExecutionException e) {
                long elapsed = System.currentTimeMillis() - startMs;
                if (isTimeoutException(e)) {
                    // EXPECTED — timeout is working
                    System.out.printf("[%s] [CANARY] ✅ Timeout fired correctly after %dms: %s%n",
                            LocalDateTime.now().format(ts), elapsed,
                            e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                } else {
                    System.err.printf("[%s] [CANARY] ❌ Unexpected error after %dms: %s%n",
                            LocalDateTime.now().format(ts), elapsed,
                            e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                }
            } catch (java.util.concurrent.TimeoutException e) {
                long elapsed = System.currentTimeMillis() - startMs;
                System.err.printf("[%s] [CANARY] 🚨 Lua future itself hung for %dms!%n",
                        LocalDateTime.now().format(ts), elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // Wait 1 minute before next canary
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[CANARY] Thread stopped");
    }, "canary-lua-timeout-checker");

    canary.setDaemon(true);
    canary.start();
    System.out.println("[CANARY] Started — will execute 1s Lua script every 60s to verify timeouts");
    return canary;
}

    public static void testClientSetGet(
            Supplier<Client> clientCreator, RunConfiguration config, boolean async) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // Create metrics output directory
        new File(config.metricsOutputDir).mkdirs();

        for (int concurrentNum : config.concurrentTasks) {

            // ========== AUTO-ADJUST CONCURRENT TASKS ==========
            int effectiveConcurrentNum = concurrentNum;
            if (config.targetTps > 0 && config.targetTps < concurrentNum) {
                effectiveConcurrentNum = Math.max(1, config.targetTps);
                System.out.printf(
                        "Note: Reducing concurrent tasks from %d to %d to match TPS target (%d)%n",
                        concurrentNum, effectiveConcurrentNum, config.targetTps);
            }
            // ==================================================

            // same as Executors.newCachedThreadPool() with a RejectedExecutionHandler for robustness
            ExecutorService executor =
                    new ThreadPoolExecutor(
                            0,
                            Integer.MAX_VALUE,
                            60L,
                            TimeUnit.SECONDS,
                            new SynchronousQueue<Runnable>(),
                            (r, poolExecutor) -> {
                                if (!poolExecutor.isShutdown()) {
                                    try {
                                        poolExecutor.getQueue().put(r);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException("interrupted");
                                    }
                                }
                            });

            // Determine iterations
            int iterations =
                    config.minimal ? 1000 : Math.min(Math.max(100000, concurrentNum * 10000), 10000000);
            // For duration-based runs, set iterations to MAX_VALUE
            if (config.durationSeconds > 0) {
                iterations = Integer.MAX_VALUE;
            } else if (config.targetTps > 0) {
                int durationSeconds = 60; // Run for 60 seconds at target TPS
                iterations = Math.min(iterations, config.targetTps * durationSeconds);
            }

            for (int clientCount : config.clientCount) {
                for (int dataSize : config.dataSize) {
                    // create clients
                    List<Client> clients = new LinkedList<>();
                    for (int cc = 0; cc < clientCount; cc++) {
                        Client newClient = clientCreator.get();
                        newClient.connectToValkey(
                                new ConnectionSettings(
                                        config.host,
                                        config.port,
                                        config.tls,
                                        config.clusterModeEnabled,
                                        config.tcpNoDelay,
                                        config.requestTimeoutMs,
                                        config.inflightLimit));
                        clients.add(newClient);
                    }

                    var clientName = clients.get(0).getName();

                    // ========== ADD WARMUP FOR READ-ONLY BENCHMARKS ==========
                    if (config.operationType == OperationType.READ_ONLY 
        || config.operationType == OperationType.DELETE_ONLY) {
    System.out.printf("%n===== Warmup Phase (1000ms timeout) =====%n");

    // Create a temporary client with safe timeout for warmup
    Client warmupClient = clientCreator.get();
    warmupClient.connectToValkey(
            new ConnectionSettings(
                    config.host, config.port, config.tls,
                    config.clusterModeEnabled, config.tcpNoDelay,
                    1000));  // always 1000ms for warmup
    warmupData(List.of(warmupClient), dataSize, config.warmupKeyCount, async, config.debugLogging);
    warmupClient.closeConnection();
}
                    // =========================================================

                    System.out.printf(
                            "%n =====> %s <===== %d clients %d concurrent %d data size %n%n",
                            clientName, clientCount, concurrentNum, dataSize);
                    System.out.println("\n" + "=".repeat(60));
                    System.out.println("BENCHMARK CONFIGURATION");
                    System.out.println("=".repeat(60));
                    System.out.printf("Start time:        %s%n", LocalDateTime.now().format(formatter));
                    System.out.printf("Client:            %s%n", clientName);
                    System.out.printf("Clients:           %d%n", clientCount);
                    System.out.printf("Concurrent tasks:  %d%n", effectiveConcurrentNum);
                    System.out.printf("Data size:         %d bytes%n", dataSize);
                    System.out.printf("Key size:          %d bytes%n", KEY_SIZE);
                    System.out.printf("Operation type:    %s%n", config.operationType);
                    System.out.printf("TCP_NODELAY:       %s%n", config.tcpNoDelay);
                    if (config.durationSeconds > 0) {
                        System.out.printf(
                                "Duration:          %d seconds (%.1f hours)%n",
                                config.durationSeconds, config.durationSeconds / 3600.0);
                    } else {
                        System.out.printf("Iterations:        %,d%n", iterations);
                    }

                    if (config.targetTps > 0) {
                        System.out.printf("Target TPS:        %d%n", config.targetTps);
                    }
                    System.out.printf("Metrics interval:  %d seconds%n", config.metricsIntervalSeconds);
                    System.out.printf("Metrics dir:       %s%n", config.metricsOutputDir);
                    System.out.println("=".repeat(60) + "\n");

                    // Create a SINGLE shared RateLimiter
                    RateLimiter rateLimiter = null;
                    if (config.targetTps > 0) {
                        rateLimiter = RateLimiter.create(config.targetTps);
                        System.out.printf("Rate limiting to %d TPS%n", config.targetTps);
                    }

                    // Create metrics CSV writer
                    MetricsCsvWriter metricsWriter =
                            new MetricsCsvWriter(
                                    clientName,
                                    config.metricsOutputDir,
                                    dataSize,
                                    config.targetTps,
                                    config.metricsIntervalSeconds,
                                    config.tcpNoDelay);
                    metricsWriter.start();

                    AtomicInteger iterationCounter = new AtomicInteger(0);

                    // Calculate end time for duration-based runs
                    long endTimeNanos = 0;
                    if (config.durationSeconds > 0) {
                        endTimeNanos = System.nanoTime() + (config.durationSeconds * 1_000_000_000L);
                    }

                    // ========== START CANARY THREAD ==========
                    Thread canaryThread = null;
                    if (config.lua) {
                        canaryThread = startCanaryThread(clients.get(0), async);
                    }
                    // =========================================

                    AtomicInteger activeDelCount = new AtomicInteger(0);

                    long started = System.nanoTime();
                    final int finalIterations = iterations;
                    final long finalEndTimeNanos = endTimeNanos;
                    List<CompletableFuture<Map<ChosenAction, ArrayList<Long>>>> asyncTasks =
                            new ArrayList<>();
                    for (int taskNum = 0; taskNum < effectiveConcurrentNum; taskNum++) {
                        final int taskNumDebugging = taskNum;
                        asyncTasks.add(
                                createTask(
                                        async,
                                        effectiveConcurrentNum,
                                        clientCount,
                                        dataSize,
                                        iterationCounter,
                                        clients,
                                        taskNumDebugging,
                                        finalIterations,
                                        executor,
                                        config.debugLogging,
                                        rateLimiter,
                                        config.operationType,
                                        finalEndTimeNanos,
                                        metricsWriter,
                                        activeDelCount));
                    }
                    if (config.debugLogging) {
                        System.out.printf("%s client Benchmarking: %n", clientName);
                        System.out.printf(
                                "===> concurrentNum = %d, clientNum = %d, tasks = %d%n",
                                effectiveConcurrentNum, clientCount, asyncTasks.size());
                    }

                    // This will start execution of all the concurrent tasks asynchronously
                    CompletableFuture<Map<ChosenAction, ArrayList<Long>>>[] completableAsyncTaskArray =
                            asyncTasks.toArray(new CompletableFuture[asyncTasks.size()]);
                    try {
                        // wait for all futures to complete
                        CompletableFuture.allOf(completableAsyncTaskArray).get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                    long after = System.nanoTime();
                    // Stop metrics collection
                    metricsWriter.stop();

                    // Map to save latency results separately for each action
                    Map<ChosenAction, List<Long>> actionResults =
                            Map.of(
                                    ChosenAction.GET_EXISTING, new ArrayList<>(),
                                    ChosenAction.GET_NON_EXISTING, new ArrayList<>(),
                                    ChosenAction.SET, new ArrayList<>(),
                                     ChosenAction.DEL, new ArrayList<>());

                    // for each task, call future.get() to retrieve & save the result in the map
                    asyncTasks.forEach(
                            future -> {
                                try {
                                    var futureResult = future.get();
                                    futureResult.forEach(
                                            (action, result) -> actionResults.get(action).addAll(result));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                    var calculatedResults = calculateResults(actionResults);

                    clients.forEach(Client::closeConnection);

                    if (config.resultsFile.isPresent()) {
                        int tps = (int) (iterationCounter.get() * NANO_TO_SECONDS / (after - started));
                        JsonWriter.Write(
                                calculatedResults,
                                config.resultsFile.get(),
                                config.clusterModeEnabled,
                                dataSize,
                                clientName,
                                clientCount,
                                effectiveConcurrentNum,
                                tps);
                    }
                    printResults(
                            calculatedResults, (after - started) / NANO_TO_SECONDS, iterationCounter.get());
                    // ========== STOP CANARY THREAD ==========
            if (canaryThread != null) {
                canaryThread.interrupt();
            }
                }
            }
            executor.shutdownNow();
            

        }

        System.out.println();
    }

    private static CompletableFuture<Map<ChosenAction, ArrayList<Long>>> createTask(
        boolean async,
        int concurrentNum,
        int clientCount,
        int dataSize,
        AtomicInteger iterationCounter,
        List<Client> clients,
        int taskNumDebugging,
        int iterations,
        Executor executor,
        boolean debugLogging,
        RateLimiter rateLimiter,
        OperationType operationType,
        long endTimeNanos,
        MetricsCsvWriter metricsWriter,
        AtomicInteger activeDelCount) {
    return CompletableFuture.supplyAsync(
            () -> {
                // Only keep the LAST 1000 latencies per action for final summary stats
                // Instead of accumulating millions forever
                final int MAX_SAMPLES = 1000;

                var taskActionResults =
                        Map.of(
                                ChosenAction.GET_EXISTING, new ArrayList<Long>(MAX_SAMPLES),
                                ChosenAction.GET_NON_EXISTING, new ArrayList<Long>(MAX_SAMPLES),
                                ChosenAction.SET, new ArrayList<Long>(MAX_SAMPLES),
                                ChosenAction.DEL, new ArrayList<Long>(MAX_SAMPLES));
                var actions = getActionMap(dataSize, async, activeDelCount);

                // Per-action counters (lightweight, no memory growth)
                var taskActionCounts = Map.of(
                        ChosenAction.GET_EXISTING, new AtomicInteger(0),
                        ChosenAction.GET_NON_EXISTING, new AtomicInteger(0),
                        ChosenAction.SET, new AtomicInteger(0),
                        ChosenAction.DEL, new AtomicInteger(0));

                if (debugLogging) {
                    System.out.printf("%n concurrent = %d/%d%n", taskNumDebugging, concurrentNum);
                }
                while (true) {
                    // Check termination condition
                    if (endTimeNanos > 0) {
                        if (System.nanoTime() >= endTimeNanos) {
                            break;
                        }
                    } else {
                        if (iterationCounter.get() >= iterations) {
                            break;
                        }
                    }

                    // Rate limiting
                    if (rateLimiter != null) {
                        rateLimiter.acquire();
                    }

                    int iterationIncrement = iterationCounter.getAndIncrement();

                    if (endTimeNanos == 0 && iterationIncrement >= iterations) {
                        break;
                    }

                    int clientIndex = iterationIncrement % clients.size();

                    if (debugLogging) {
                        System.out.printf(
                                "> iteration = %d/%d, client# = %d/%d%n",
                                iterationIncrement + 1, iterations, clientIndex + 1, clientCount);
                    }

                    // operate and calculate tik-tok
                    Pair<ChosenAction, Long> result =
                            measurePerformance(clients.get(clientIndex), actions, operationType, metricsWriter);

                    // Only keep last MAX_SAMPLES latencies (rolling window)
                    ChosenAction action = result.getLeft();
                    ArrayList<Long> samples = taskActionResults.get(action);
                    int count = taskActionCounts.get(action).getAndIncrement();

                    // Reservoir sampling: keep a fixed-size representative sample
                    if (samples.size() < MAX_SAMPLES) {
                        samples.add(result.getRight());
                    } else {
                        // Randomly replace an existing sample
                        // This gives a statistically representative sample
                        int idx = (int) (Math.random() * (count + 1));
                        if (idx < MAX_SAMPLES) {
                            samples.set(idx, result.getRight());
                        }
                    }
                }
                return taskActionResults;
            },
            executor);
}

    public static Map<ChosenAction, Operation> getActionMap(
        int dataSize, boolean async, AtomicInteger activeDelCount) {

    String value = "0".repeat(dataSize);

    // Pre-create formatter ONCE, reuse for all calls
    final DateTimeFormatter ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    return Map.of(
            ChosenAction.GET_EXISTING,
                    (client) -> {
                        if (async) {
                            ((AsyncClient) client).asyncGet(generateKeyForRead()).get();
                        } else {
                            ((SyncClient) client).get(generateKeyForRead());
                        }
                    },
            ChosenAction.GET_NON_EXISTING,
                    (client) -> {
                        if (async) {
                            ((AsyncClient) client).asyncGet(generateKeyGet()).get();
                        } else {
                            ((SyncClient) client).get(generateKeyGet());
                        }
                    },
            ChosenAction.SET,
                    (client) -> {
                        if (async) {
                            ((AsyncClient) client).asyncSet(generateKeyForRead(), value).get();
                        } else {
                            ((SyncClient) client).set(generateKeyForRead(), value);
                        }
                    },
            ChosenAction.DEL,
                    (client) -> {
                        if (async) {
                            String[] keys = new String[3];
                            for (int i = 0; i < 3; i++) {
                                keys[i] = generateKeyForRead();
                            }

                            long startMs = System.currentTimeMillis();
                            activeDelCount.incrementAndGet();
                            try {
                                Future<Long> future = ((AsyncClient<String>) client).asyncDel(keys);
                                try {
                                    future.get(3, TimeUnit.SECONDS);
                                } catch (java.util.concurrent.TimeoutException e) {
                                    // Only create debug objects on ERROR path
                                    String thread = Thread.currentThread().getName();
                                    String keysSummary = keys[0].substring(0, Math.min(10, keys[0].length())) + "...";
                                    System.err.printf(
                                            "[%s] !!! DEL STUCK after 3s | thread=%s | key0=%s%n",
                                            LocalDateTime.now().format(ts),
                                            thread,
                                            keysSummary);
                                    try {
                                        future.get(30, TimeUnit.SECONDS);
                                        long totalMs = System.currentTimeMillis() - startMs;
                                        System.err.printf(
                                                "[%s] !!! DEL UNSTUCK after %dms | thread=%s%n",
                                                LocalDateTime.now().format(ts),
                                                totalMs,
                                                thread);
                                    } catch (java.util.concurrent.TimeoutException e2) {
                                        long totalMs = System.currentTimeMillis() - startMs;
                                        System.err.printf(
                                                "[%s] !!! DEL NEVER COMPLETED after %dms | thread=%s%n",
                                                LocalDateTime.now().format(ts),
                                                totalMs,
                                                thread);
                                        throw new RuntimeException("DEL future never completed", e2);
                                    } catch (ExecutionException e3) {
                                        long totalMs = System.currentTimeMillis() - startMs;
                                        System.err.printf(
                                                "[%s] !!! DEL FAILED after %dms | thread=%s | error=%s%n",
                                                LocalDateTime.now().format(ts),
                                                totalMs,
                                                thread,
                                                e3.getCause() != null ? e3.getCause().getMessage() : e3.getMessage());
                                        throw e3;
                                    }
                                }
                            } finally {
                                activeDelCount.decrementAndGet();
                                long elapsed = System.currentTimeMillis() - startMs;
                                if (elapsed > 2000) {
                                    System.err.printf("[%s] [SLOW DEL] took %dms%n",
                                            LocalDateTime.now().format(ts),
                                            elapsed);
                                }
                            }
                        } else {
                            throw new UnsupportedOperationException("Sync DEL not implemented");
                        }
                    });
}
}
