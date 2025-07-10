/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.valkey.glide.jni.benchmarks;

import io.valkey.glide.jni.GlideJniClient;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance benchmark for JNI client implementation.
 * Designed to measure the performance improvements over UDS-based approach.
 */
public class JniBenchmark {
    
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 6379;
    private static final int DEFAULT_OPERATIONS = 100000;
    private static final int DEFAULT_CONCURRENCY = 50;
    private static final int DEFAULT_WARMUP_OPERATIONS = 10000;
    
    private final GlideJniClient client;
    private final int totalOperations;
    private final int concurrency;
    private final ExecutorService executor;
    
    public JniBenchmark(String host, int port, int operations, int concurrency) {
        this.totalOperations = operations;
        this.concurrency = concurrency;
        this.executor = Executors.newFixedThreadPool(concurrency);
        
        // Create client configuration
        GlideJniClient.Config config = new GlideJniClient.Config(
            Arrays.asList(host + ":" + port)
        ).requestTimeout(250); // Match existing benchmark timeout
        
        this.client = new GlideJniClient(config);
        
        // Verify connection
        try {
            String pong = client.ping();
            System.out.println("Connected to Valkey server: " + pong);
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Valkey server", e);
        }
    }
    
    /**
     * Run comprehensive benchmark suite.
     */
    public BenchmarkResults runBenchmark() {
        System.out.println("Starting JNI benchmark...");
        System.out.println("Operations: " + totalOperations);
        System.out.println("Concurrency: " + concurrency);
        
        // Warmup
        System.out.println("Warming up...");
        runWarmup();
        
        // Run benchmarks
        BenchmarkResults results = new BenchmarkResults();
        
        results.setOperations = runSetBenchmark();
        results.getOperations = runGetBenchmark();
        results.mixedOperations = runMixedBenchmark();
        results.pingOperations = runPingBenchmark();
        
        return results;
    }
    
    private void runWarmup() {
        long start = System.nanoTime();
        
        // Warmup with mixed operations
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrency];
        int opsPerThread = DEFAULT_WARMUP_OPERATIONS / concurrency;
        
        for (int i = 0; i < concurrency; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    String key = "warmup_key_" + threadId + "_" + j;
                    String value = "warmup_value_" + j;
                    
                    try {
                        client.set(key, value);
                        client.get(key);
                    } catch (Exception e) {
                        // Ignore warmup errors
                    }
                }
            }, executor);
        }
        
        CompletableFuture.allOf(futures).join();
        
        long duration = System.nanoTime() - start;
        System.out.printf("Warmup completed in %.2f ms%n", duration / 1_000_000.0);
    }
    
    private OperationResult runSetBenchmark() {
        System.out.println("Running SET benchmark...");
        
        long start = System.nanoTime();
        AtomicLong successCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrency];
        int opsPerThread = totalOperations / concurrency;
        
        for (int i = 0; i < concurrency; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    String key = "set_key_" + threadId + "_" + j;
                    String value = "set_value_" + j + "_" + System.nanoTime();
                    
                    try {
                        client.set(key, value);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            }, executor);
        }
        
        CompletableFuture.allOf(futures).join();
        
        long duration = System.nanoTime() - start;
        return new OperationResult("SET", successCount.get(), errorCount.get(), duration);
    }
    
    private OperationResult runGetBenchmark() {
        System.out.println("Running GET benchmark...");
        
        // Pre-populate keys
        System.out.println("Pre-populating keys for GET benchmark...");
        for (int i = 0; i < totalOperations; i++) {
            String key = "get_key_" + i;
            String value = "get_value_" + i;
            client.set(key, value);
        }
        
        long start = System.nanoTime();
        AtomicLong successCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrency];
        int opsPerThread = totalOperations / concurrency;
        
        for (int i = 0; i < concurrency; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    String key = "get_key_" + ((threadId * opsPerThread) + j);
                    
                    try {
                        String value = client.get(key);
                        if (value != null) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            }, executor);
        }
        
        CompletableFuture.allOf(futures).join();
        
        long duration = System.nanoTime() - start;
        return new OperationResult("GET", successCount.get(), errorCount.get(), duration);
    }
    
    private OperationResult runMixedBenchmark() {
        System.out.println("Running MIXED benchmark...");
        
        long start = System.nanoTime();
        AtomicLong successCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrency];
        int opsPerThread = totalOperations / concurrency;
        
        for (int i = 0; i < concurrency; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    String key = "mixed_key_" + threadId + "_" + j;
                    String value = "mixed_value_" + j;
                    
                    try {
                        // 70% GET, 30% SET operations
                        if (j % 10 < 7) {
                            String result = client.get(key);
                            successCount.incrementAndGet();
                        } else {
                            client.set(key, value);
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            }, executor);
        }
        
        CompletableFuture.allOf(futures).join();
        
        long duration = System.nanoTime() - start;
        return new OperationResult("MIXED", successCount.get(), errorCount.get(), duration);
    }
    
    private OperationResult runPingBenchmark() {
        System.out.println("Running PING benchmark...");
        
        long start = System.nanoTime();
        AtomicLong successCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrency];
        int opsPerThread = totalOperations / concurrency;
        
        for (int i = 0; i < concurrency; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    try {
                        String pong = client.ping();
                        if ("PONG".equals(pong)) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            }, executor);
        }
        
        CompletableFuture.allOf(futures).join();
        
        long duration = System.nanoTime() - start;
        return new OperationResult("PING", successCount.get(), errorCount.get(), duration);
    }
    
    public void close() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (client != null) {
            client.close();
        }
    }
    
    public static class BenchmarkResults {
        public OperationResult setOperations;
        public OperationResult getOperations;
        public OperationResult mixedOperations;
        public OperationResult pingOperations;
        
        public void printResults() {
            System.out.println("\n=== JNI BENCHMARK RESULTS ===");
            setOperations.printResult();
            getOperations.printResult();
            mixedOperations.printResult();
            pingOperations.printResult();
            
            long totalOps = setOperations.successCount + getOperations.successCount + 
                           mixedOperations.successCount + pingOperations.successCount;
            long totalTime = setOperations.durationNanos + getOperations.durationNanos + 
                            mixedOperations.durationNanos + pingOperations.durationNanos;
            
            double avgOpsPerSec = (totalOps * 1_000_000_000.0) / totalTime;
            double avgLatencyMs = (totalTime / 1_000_000.0) / totalOps;
            
            System.out.printf("OVERALL: %.0f ops/sec, %.3f ms avg latency%n", 
                avgOpsPerSec, avgLatencyMs);
            System.out.println("==============================");
        }
    }
    
    public static class OperationResult {
        public final String operation;
        public final long successCount;
        public final long errorCount;
        public final long durationNanos;
        
        public OperationResult(String operation, long successCount, long errorCount, long durationNanos) {
            this.operation = operation;
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.durationNanos = durationNanos;
        }
        
        public void printResult() {
            double durationMs = durationNanos / 1_000_000.0;
            double opsPerSec = (successCount * 1_000_000_000.0) / durationNanos;
            double avgLatencyMs = durationMs / successCount;
            
            System.out.printf("%s: %d ops in %.2f ms (%.0f ops/sec, %.3f ms avg latency, %d errors)%n",
                operation, successCount, durationMs, opsPerSec, avgLatencyMs, errorCount);
        }
    }
    
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        int operations = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_OPERATIONS;
        int concurrency = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_CONCURRENCY;
        
        System.out.println("JNI Benchmark Starting...");
        System.out.printf("Target: %s:%d%n", host, port);
        System.out.printf("Operations: %d, Concurrency: %d%n", operations, concurrency);
        
        JniBenchmark benchmark = null;
        try {
            benchmark = new JniBenchmark(host, port, operations, concurrency);
            BenchmarkResults results = benchmark.runBenchmark();
            results.printResults();
        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (benchmark != null) {
                benchmark.close();
            }
        }
    }
}