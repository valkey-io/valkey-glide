/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Performance benchmark tests comparing GLIDE Jedis compatibility layer
 * with actual Jedis implementation across various scenarios.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JedisPerformanceBenchmarkTest {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final String BENCHMARK_KEY_PREFIX = "benchmark:";
    
    private static final int DEFAULT_ITERATIONS = Integer.getInteger("benchmark.iterations", 10000);
    private static final int DEFAULT_WARMUP = Integer.getInteger("benchmark.warmup", 1000);
    private static final int DEFAULT_THREADS = Integer.getInteger("benchmark.threads", 1);
    
    private Object glideJedis;
    private Object actualJedis;
    private boolean hasGlideJedis = false;
    private boolean hasActualJedis = false;

    @BeforeEach
    void setup() {
        // Initialize GLIDE Jedis
        try {
            Class<?> glideJedisClass = Class.forName("redis.clients.jedis.Jedis");
            glideJedis = glideJedisClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            hasGlideJedis = true;
        } catch (Exception e) {
            hasGlideJedis = false;
        }

        // Initialize actual Jedis (if available)
        try {
            // Load from external JAR if specified
            String jedisJarPath = System.getProperty("jedis.jar.path");
            if (jedisJarPath != null) {
                // Custom class loader logic would go here
                // For now, skip actual Jedis testing
            }
            hasActualJedis = false;
        } catch (Exception e) {
            hasActualJedis = false;
        }
    }

    @AfterEach
    void cleanup() {
        if (hasGlideJedis) {
            try {
                cleanupBenchmarkKeys(glideJedis);
                closeClient(glideJedis);
            } catch (Exception e) {
                System.err.println("Error cleaning up GLIDE Jedis: " + e.getMessage());
            }
        }
        
        if (hasActualJedis) {
            try {
                cleanupBenchmarkKeys(actualJedis);
                closeClient(actualJedis);
            } catch (Exception e) {
                System.err.println("Error cleaning up actual Jedis: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Single-threaded GET/SET Performance")
    void benchmarkSingleThreadedOperations() {
        if (!hasGlideJedis) {
            System.out.println("Skipping single-threaded benchmark - GLIDE Jedis not available");
            return;
        }

        System.out.println("\n=== Single-threaded GET/SET Performance ===");
        
        // Warmup
        performWarmup(glideJedis, "glide", DEFAULT_WARMUP);
        
        // Benchmark GLIDE Jedis
        BenchmarkResult glideResult = benchmarkGetSetOperations(glideJedis, "glide", DEFAULT_ITERATIONS);
        printBenchmarkResult("GLIDE Jedis", glideResult);
        
        // Benchmark actual Jedis (if available)
        if (hasActualJedis) {
            performWarmup(actualJedis, "actual", DEFAULT_WARMUP);
            BenchmarkResult actualResult = benchmarkGetSetOperations(actualJedis, "actual", DEFAULT_ITERATIONS);
            printBenchmarkResult("Actual Jedis", actualResult);
            
            compareBenchmarkResults("Single-threaded GET/SET", glideResult, actualResult);
        }
    }

    @Test
    @Order(2)
    @DisplayName("Multi-threaded GET/SET Performance")
    @EnabledIf("hasGlideJedis")
    void benchmarkMultiThreadedOperations() {
        System.out.println("\n=== Multi-threaded GET/SET Performance ===");
        
        int threadCount = Math.max(DEFAULT_THREADS, 4);
        int operationsPerThread = DEFAULT_ITERATIONS / threadCount;
        
        // Benchmark GLIDE Jedis
        BenchmarkResult glideResult = benchmarkConcurrentOperations("glide", threadCount, operationsPerThread);
        printBenchmarkResult("GLIDE Jedis (Concurrent)", glideResult);
        
        // Benchmark actual Jedis (if available)
        if (hasActualJedis) {
            BenchmarkResult actualResult = benchmarkConcurrentOperations("actual", threadCount, operationsPerThread);
            printBenchmarkResult("Actual Jedis (Concurrent)", actualResult);
            
            compareBenchmarkResults("Multi-threaded GET/SET", glideResult, actualResult);
        }
    }

    @Test
    @Order(3)
    @DisplayName("Large Value Performance")
    @EnabledIf("hasGlideJedis")
    void benchmarkLargeValues() {
        System.out.println("\n=== Large Value Performance ===");
        
        int[] valueSizes = {1024, 10240, 102400}; // 1KB, 10KB, 100KB
        
        for (int size : valueSizes) {
            String largeValue = "x".repeat(size);
            String keyPrefix = BENCHMARK_KEY_PREFIX + "large_" + size + ":";
            
            // Warmup
            performLargeValueWarmup(glideJedis, keyPrefix + "glide", largeValue, 100);
            
            // Benchmark GLIDE Jedis
            BenchmarkResult glideResult = benchmarkLargeValueOperations(glideJedis, keyPrefix + "glide", largeValue, 1000);
            printBenchmarkResult("GLIDE Jedis (" + size + " bytes)", glideResult);
            
            // Benchmark actual Jedis (if available)
            if (hasActualJedis) {
                performLargeValueWarmup(actualJedis, keyPrefix + "actual", largeValue, 100);
                BenchmarkResult actualResult = benchmarkLargeValueOperations(actualJedis, keyPrefix + "actual", largeValue, 1000);
                printBenchmarkResult("Actual Jedis (" + size + " bytes)", actualResult);
                
                compareBenchmarkResults("Large Value (" + size + " bytes)", glideResult, actualResult);
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("Connection Pool Performance")
    @EnabledIf("hasGlideJedis")
    void benchmarkPoolPerformance() {
        System.out.println("\n=== Connection Pool Performance ===");
        
        try {
            // Test GLIDE JedisPool
            Class<?> poolClass = Class.forName("redis.clients.jedis.JedisPool");
            Object glidePool = poolClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            
            BenchmarkResult glidePoolResult = benchmarkPoolOperations(glidePool, "glide_pool", DEFAULT_ITERATIONS);
            printBenchmarkResult("GLIDE JedisPool", glidePoolResult);
            
            closeClient(glidePool);
            
            // Compare with direct connection
            BenchmarkResult glideDirectResult = benchmarkGetSetOperations(glideJedis, "glide_direct", DEFAULT_ITERATIONS);
            printBenchmarkResult("GLIDE Direct Connection", glideDirectResult);
            
            System.out.println("Pool vs Direct overhead: " + 
                String.format("%.2f%%", ((double)(glidePoolResult.totalTimeNs - glideDirectResult.totalTimeNs) / glideDirectResult.totalTimeNs) * 100));
            
        } catch (Exception e) {
            System.err.println("Pool benchmark failed: " + e.getMessage());
        }
    }

    @Test
    @Order(5)
    @DisplayName("Memory Usage Comparison")
    @EnabledIf("hasGlideJedis")
    void benchmarkMemoryUsage() {
        System.out.println("\n=== Memory Usage Comparison ===");
        
        Runtime runtime = Runtime.getRuntime();
        
        // Measure baseline memory
        System.gc();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Create multiple GLIDE Jedis connections
        List<Object> glideConnections = new ArrayList<>();
        try {
            for (int i = 0; i < 100; i++) {
                Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
                Object jedis = jedisClass.getConstructor(String.class, int.class)
                        .newInstance(REDIS_HOST, REDIS_PORT);
                glideConnections.add(jedis);
            }
            
            System.gc();
            long glideMemory = runtime.totalMemory() - runtime.freeMemory();
            long glideMemoryUsage = glideMemory - baselineMemory;
            
            System.out.println("GLIDE Jedis memory usage (100 connections): " + 
                String.format("%.2f MB", glideMemoryUsage / (1024.0 * 1024.0)));
            
            // Cleanup
            for (Object jedis : glideConnections) {
                closeClient(jedis);
            }
            
        } catch (Exception e) {
            System.err.println("Memory benchmark failed: " + e.getMessage());
        }
    }

    // Helper methods
    private void performWarmup(Object jedis, String keyPrefix, int iterations) {
        try {
            for (int i = 0; i < iterations; i++) {
                String key = BENCHMARK_KEY_PREFIX + keyPrefix + ":warmup:" + i;
                String value = "warmup_value_" + i;
                invokeMethod(jedis, "set", key, value);
                invokeMethod(jedis, "get", key);
            }
        } catch (Exception e) {
            System.err.println("Warmup failed: " + e.getMessage());
        }
    }

    private BenchmarkResult benchmarkGetSetOperations(Object jedis, String keyPrefix, int iterations) {
        long totalSetTime = 0;
        long totalGetTime = 0;
        long totalTime = 0;
        
        try {
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                String key = BENCHMARK_KEY_PREFIX + keyPrefix + ":" + i;
                String value = "benchmark_value_" + i;
                
                long setStart = System.nanoTime();
                invokeMethod(jedis, "set", key, value);
                long setEnd = System.nanoTime();
                totalSetTime += (setEnd - setStart);
                
                long getStart = System.nanoTime();
                invokeMethod(jedis, "get", key);
                long getEnd = System.nanoTime();
                totalGetTime += (getEnd - getStart);
            }
            
            long endTime = System.nanoTime();
            totalTime = endTime - startTime;
            
        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
        }
        
        return new BenchmarkResult(iterations, totalTime, totalSetTime, totalGetTime);
    }

    private BenchmarkResult benchmarkConcurrentOperations(String keyPrefix, int threadCount, int operationsPerThread) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<BenchmarkResult>> futures = new ArrayList<>();
        
        long startTime = System.nanoTime();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            Future<BenchmarkResult> future = executor.submit(() -> {
                try {
                    Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
                    Object threadJedis = jedisClass.getConstructor(String.class, int.class)
                            .newInstance(REDIS_HOST, REDIS_PORT);
                    
                    BenchmarkResult result = benchmarkGetSetOperations(threadJedis, 
                            keyPrefix + "_thread_" + threadId, operationsPerThread);
                    
                    closeClient(threadJedis);
                    return result;
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " failed: " + e.getMessage());
                    return new BenchmarkResult(0, 0, 0, 0);
                }
            });
            futures.add(future);
        }
        
        // Collect results
        long totalOperations = 0;
        long totalSetTime = 0;
        long totalGetTime = 0;
        
        for (Future<BenchmarkResult> future : futures) {
            try {
                BenchmarkResult result = future.get(30, TimeUnit.SECONDS);
                totalOperations += result.operations;
                totalSetTime += result.setTimeNs;
                totalGetTime += result.getTimeNs;
            } catch (Exception e) {
                System.err.println("Failed to get thread result: " + e.getMessage());
            }
        }
        
        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;
        
        executor.shutdown();
        
        return new BenchmarkResult((int)totalOperations, totalTime, totalSetTime, totalGetTime);
    }

    private void performLargeValueWarmup(Object jedis, String keyPrefix, String value, int iterations) {
        try {
            for (int i = 0; i < iterations; i++) {
                String key = keyPrefix + ":warmup:" + i;
                invokeMethod(jedis, "set", key, value);
                invokeMethod(jedis, "get", key);
            }
        } catch (Exception e) {
            System.err.println("Large value warmup failed: " + e.getMessage());
        }
    }

    private BenchmarkResult benchmarkLargeValueOperations(Object jedis, String keyPrefix, String value, int iterations) {
        return benchmarkGetSetOperations(jedis, keyPrefix, iterations);
    }

    private BenchmarkResult benchmarkPoolOperations(Object pool, String keyPrefix, int iterations) {
        long totalSetTime = 0;
        long totalGetTime = 0;
        long totalTime = 0;
        
        try {
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                Object jedis = invokeMethod(pool, "getResource");
                
                String key = BENCHMARK_KEY_PREFIX + keyPrefix + ":" + i;
                String value = "pool_benchmark_value_" + i;
                
                long setStart = System.nanoTime();
                invokeMethod(jedis, "set", key, value);
                long setEnd = System.nanoTime();
                totalSetTime += (setEnd - setStart);
                
                long getStart = System.nanoTime();
                invokeMethod(jedis, "get", key);
                long getEnd = System.nanoTime();
                totalGetTime += (getEnd - getStart);
                
                invokeMethod(pool, "returnResource", jedis);
            }
            
            long endTime = System.nanoTime();
            totalTime = endTime - startTime;
            
        } catch (Exception e) {
            System.err.println("Pool benchmark failed: " + e.getMessage());
        }
        
        return new BenchmarkResult(iterations, totalTime, totalSetTime, totalGetTime);
    }

    private void printBenchmarkResult(String implementation, BenchmarkResult result) {
        if (result.operations == 0) {
            System.out.println(implementation + ": FAILED");
            return;
        }
        
        double totalTimeMs = result.totalTimeNs / 1_000_000.0;
        double avgSetTimeUs = result.setTimeNs / (result.operations * 1000.0);
        double avgGetTimeUs = result.getTimeNs / (result.operations * 1000.0);
        double opsPerSecond = (result.operations * 1000.0) / totalTimeMs;
        
        System.out.printf("%s: %d ops in %.2fms (%.2f ops/sec)%n", 
                implementation, result.operations, totalTimeMs, opsPerSecond);
        System.out.printf("  SET avg: %.2fμs, GET avg: %.2fμs%n", avgSetTimeUs, avgGetTimeUs);
    }

    private void compareBenchmarkResults(String testName, BenchmarkResult glideResult, BenchmarkResult actualResult) {
        if (glideResult.operations == 0 || actualResult.operations == 0) {
            System.out.println("Cannot compare - one or both benchmarks failed");
            return;
        }
        
        double glideOpsPerSec = (glideResult.operations * 1_000_000_000.0) / glideResult.totalTimeNs;
        double actualOpsPerSec = (actualResult.operations * 1_000_000_000.0) / actualResult.totalTimeNs;
        double ratio = glideOpsPerSec / actualOpsPerSec;
        
        System.out.printf("%s Comparison:%n", testName);
        System.out.printf("  Performance ratio (GLIDE/Actual): %.2f%n", ratio);
        
        if (ratio > 1.2) {
            System.out.println("  🚀 GLIDE is significantly faster!");
        } else if (ratio > 0.8) {
            System.out.println("  ✅ GLIDE performance is comparable");
        } else {
            System.out.println("  ⚠️  GLIDE is slower than actual Jedis");
        }
    }

    private Object invokeMethod(Object obj, String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
            if (paramTypes[i] == Integer.class) paramTypes[i] = int.class;
        }
        return obj.getClass().getMethod(methodName, paramTypes).invoke(obj, args);
    }

    private void closeClient(Object client) throws Exception {
        invokeMethod(client, "close");
    }

    private void cleanupBenchmarkKeys(Object jedis) throws Exception {
        // Simplified cleanup - in production, use SCAN pattern
        for (int i = 0; i < 1000; i++) {
            try {
                invokeMethod(jedis, "del", BENCHMARK_KEY_PREFIX + "*");
            } catch (Exception e) {
                // Ignore cleanup errors
                break;
            }
        }
    }

    boolean hasGlideJedis() {
        return hasGlideJedis;
    }

    // Benchmark result holder
    private static class BenchmarkResult {
        final int operations;
        final long totalTimeNs;
        final long setTimeNs;
        final long getTimeNs;
        
        BenchmarkResult(int operations, long totalTimeNs, long setTimeNs, long getTimeNs) {
            this.operations = operations;
            this.totalTimeNs = totalTimeNs;
            this.setTimeNs = setTimeNs;
            this.getTimeNs = getTimeNs;
        }
    }
}
