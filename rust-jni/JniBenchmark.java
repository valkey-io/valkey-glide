import io.valkey.glide.jni.GlideJniClient;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class JniBenchmark {
    
    public static void main(String[] args) {
        int operations = args.length > 0 ? Integer.parseInt(args[0]) : 10000;
        int concurrency = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        
        System.out.println("🚀 Valkey GLIDE JNI Performance Benchmark");
        System.out.println("==========================================");
        System.out.printf("Operations: %d, Concurrency: %d%n%n", operations, concurrency);
        
        // Create client
        GlideJniClient.Config config = new GlideJniClient.Config(Arrays.asList("127.0.0.1:6379"))
            .databaseId(0)
            .requestTimeout(250);
        
        GlideJniClient client = new GlideJniClient(config);
        
        // Verify connection
        String pong = client.ping();
        if (!"PONG".equals(pong)) {
            System.err.println("❌ Connection failed - PING returned: " + pong);
            client.close();
            System.exit(1);
        }
        
        System.out.println("✅ Connected to Valkey server");
        
        try {
            // Run benchmarks
            BenchmarkResult setResult = runSetBenchmark(client, operations, concurrency);
            BenchmarkResult getResult = runGetBenchmark(client, operations, concurrency);
            BenchmarkResult mixedResult = runMixedBenchmark(client, operations, concurrency);
            
            // Print summary
            System.out.println("📊 BENCHMARK SUMMARY");
            System.out.println("====================");
            System.out.printf("SET:   %8.0f ops/sec, %6.3f ms avg latency%n", setResult.opsPerSec, setResult.avgLatencyMs);
            System.out.printf("GET:   %8.0f ops/sec, %6.3f ms avg latency%n", getResult.opsPerSec, getResult.avgLatencyMs);
            System.out.printf("MIXED: %8.0f ops/sec, %6.3f ms avg latency%n", mixedResult.opsPerSec, mixedResult.avgLatencyMs);
            
            double overallOpsPerSec = (setResult.opsPerSec + getResult.opsPerSec + mixedResult.opsPerSec) / 3;
            System.out.printf("OVERALL: %6.0f ops/sec average%n", overallOpsPerSec);
            
        } finally {
            client.close();
        }
        
        System.out.println("\n🎉 Benchmark completed successfully!");
    }
    
    private static BenchmarkResult runSetBenchmark(GlideJniClient client, int operations, int concurrency) {
        System.out.println("📊 SET Benchmark");
        System.out.println("-----------------");
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        AtomicLong successCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        
        long startTime = System.nanoTime();
        
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrency];
        int opsPerThread = operations / concurrency;
        
        for (int i = 0; i < concurrency; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    String key = "set_key_" + threadId + "_" + j;
                    String value = "set_value_" + j + "_" + System.nanoTime();
                    
                    try {
                        boolean result = client.set(key, value);
                        if (result) {
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
        
        // Wait for completion
        CompletableFuture.allOf(futures).join();
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        double opsPerSec = (successCount.get() * 1000.0) / durationMs;
        double avgLatencyMs = (double) durationMs / successCount.get();
        
        System.out.printf("Success: %d, Errors: %d%n", successCount.get(), errorCount.get());
        System.out.printf("Duration: %d ms%n", durationMs);
        System.out.printf("Throughput: %.0f ops/sec%n", opsPerSec);
        System.out.printf("Avg Latency: %.3f ms%n%n", avgLatencyMs);
        
        executor.shutdown();
        return new BenchmarkResult(opsPerSec, avgLatencyMs, successCount.get(), errorCount.get());
    }
    
    private static BenchmarkResult runGetBenchmark(GlideJniClient client, int operations, int concurrency) {
        System.out.println("📊 GET Benchmark");
        System.out.println("-----------------");
        
        // Pre-populate keys
        System.out.println("Pre-populating keys...");
        for (int i = 0; i < operations; i++) {
            client.set("get_key_" + i, "get_value_" + i);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        AtomicLong successCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        
        long startTime = System.nanoTime();
        
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrency];
        int opsPerThread = operations / concurrency;
        
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
        
        // Wait for completion
        CompletableFuture.allOf(futures).join();
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        double opsPerSec = (successCount.get() * 1000.0) / durationMs;
        double avgLatencyMs = (double) durationMs / successCount.get();
        
        System.out.printf("Success: %d, Errors: %d%n", successCount.get(), errorCount.get());
        System.out.printf("Duration: %d ms%n", durationMs);
        System.out.printf("Throughput: %.0f ops/sec%n", opsPerSec);
        System.out.printf("Avg Latency: %.3f ms%n%n", avgLatencyMs);
        
        executor.shutdown();
        return new BenchmarkResult(opsPerSec, avgLatencyMs, successCount.get(), errorCount.get());
    }
    
    private static BenchmarkResult runMixedBenchmark(GlideJniClient client, int operations, int concurrency) {
        System.out.println("📊 Mixed Operations Benchmark (70% GET, 30% SET)");
        System.out.println("--------------------------------------------------");
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        AtomicLong successCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        
        long startTime = System.nanoTime();
        
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrency];
        int opsPerThread = operations / concurrency;
        
        for (int i = 0; i < concurrency; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    String key = "mixed_key_" + threadId + "_" + j;
                    String value = "mixed_value_" + j;
                    
                    try {
                        // 70% GET, 30% SET
                        if (j % 10 < 7) {
                            client.get(key);
                            successCount.incrementAndGet();
                        } else {
                            boolean result = client.set(key, value);
                            if (result) {
                                successCount.incrementAndGet();
                            } else {
                                errorCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            }, executor);
        }
        
        // Wait for completion
        CompletableFuture.allOf(futures).join();
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        double opsPerSec = (successCount.get() * 1000.0) / durationMs;
        double avgLatencyMs = (double) durationMs / successCount.get();
        
        System.out.printf("Success: %d, Errors: %d%n", successCount.get(), errorCount.get());
        System.out.printf("Duration: %d ms%n", durationMs);
        System.out.printf("Throughput: %.0f ops/sec%n", opsPerSec);
        System.out.printf("Avg Latency: %.3f ms%n%n", avgLatencyMs);
        
        executor.shutdown();
        return new BenchmarkResult(opsPerSec, avgLatencyMs, successCount.get(), errorCount.get());
    }
    
    private static class BenchmarkResult {
        final double opsPerSec;
        final double avgLatencyMs;
        final long successCount;
        final long errorCount;
        
        BenchmarkResult(double opsPerSec, double avgLatencyMs, long successCount, long errorCount) {
            this.opsPerSec = opsPerSec;
            this.avgLatencyMs = avgLatencyMs;
            this.successCount = successCount;
            this.errorCount = errorCount;
        }
    }
}