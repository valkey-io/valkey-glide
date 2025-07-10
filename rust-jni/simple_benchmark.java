import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class simple_benchmark {
    static {
        System.loadLibrary("glidejni");
    }
    
    // Native JNI methods
    private static native long createClient(String[] addresses, int databaseId, String username, String password, boolean useTls, boolean clusterMode, int requestTimeoutMs);
    private static native void closeClient(long clientPtr);
    private static native String get(long clientPtr, String key);
    private static native boolean set(long clientPtr, String key, String value);
    private static native String ping(long clientPtr);
    
    public static void main(String[] args) {
        int operations = args.length > 0 ? Integer.parseInt(args[0]) : 10000;
        int concurrency = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        
        System.out.println("🚀 Valkey GLIDE JNI Performance Benchmark");
        System.out.println("==========================================");
        System.out.printf("Operations: %d, Concurrency: %d%n%n", operations, concurrency);
        
        // Create client
        String[] addresses = {"127.0.0.1:6379"};
        long clientPtr = createClient(addresses, -1, null, null, false, false, 250);
        
        if (clientPtr == 0) {
            System.err.println("❌ Failed to create JNI client");
            System.exit(1);
        }
        
        // Verify connection
        String pong = ping(clientPtr);
        if (!"PONG".equals(pong)) {
            System.err.println("❌ Connection failed - PING returned: " + pong);
            closeClient(clientPtr);
            System.exit(1);
        }
        
        System.out.println("✅ Connected to Valkey server");
        
        try {
            // Run benchmarks
            runSetBenchmark(clientPtr, operations, concurrency);
            runGetBenchmark(clientPtr, operations, concurrency);
            runMixedBenchmark(clientPtr, operations, concurrency);
            
        } finally {
            closeClient(clientPtr);
        }
        
        System.out.println("\n🎉 Benchmark completed successfully!");
    }
    
    private static void runSetBenchmark(long clientPtr, int operations, int concurrency) {
        System.out.println("📊 SET Benchmark");
        System.out.println("-----------------");
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        AtomicLong successCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        
        long startTime = System.nanoTime();
        
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrency];
        int opsPerThread = operations / concurrency;
        
        for (int i = 0; i < concurrency; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    String key = "set_key_" + threadId + "_" + j;
                    String value = "set_value_" + j + "_" + System.nanoTime();
                    
                    try {
                        boolean result = set(clientPtr, key, value);
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
    }
    
    private static void runGetBenchmark(long clientPtr, int operations, int concurrency) {
        System.out.println("📊 GET Benchmark");
        System.out.println("-----------------");
        
        // Pre-populate keys
        System.out.println("Pre-populating keys...");
        for (int i = 0; i < operations; i++) {
            set(clientPtr, "get_key_" + i, "get_value_" + i);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        AtomicLong successCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        
        long startTime = System.nanoTime();
        
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrency];
        int opsPerThread = operations / concurrency;
        
        for (int i = 0; i < concurrency; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < opsPerThread; j++) {
                    String key = "get_key_" + ((threadId * opsPerThread) + j);
                    
                    try {
                        String value = get(clientPtr, key);
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
    }
    
    private static void runMixedBenchmark(long clientPtr, int operations, int concurrency) {
        System.out.println("📊 Mixed Operations Benchmark (70% GET, 30% SET)");
        System.out.println("--------------------------------------------------");
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        AtomicLong successCount = new AtomicLong();
        AtomicLong errorCount = new AtomicLong();
        
        long startTime = System.nanoTime();
        
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
                            String result = get(clientPtr, key);
                            successCount.incrementAndGet();
                        } else {
                            boolean result = set(clientPtr, key, value);
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
    }
}