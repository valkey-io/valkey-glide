package com.example.valkey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformanceTest {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceTest.class);

    private final TestConfiguration config;
    private final Supplier<RedisClient> clientFactory;
    private final String clientName;

    public PerformanceTest(TestConfiguration config, Supplier<RedisClient> clientFactory, String clientName) {
        this.config = config;
        this.clientFactory = clientFactory;
        this.clientName = clientName;
    }

    public PerformanceMetrics runTest() throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("Starting Performance Test for " + clientName);
        System.out.println("Configuration: " + config.toString());
        System.out.println("=".repeat(80));

        // Test connectivity first
        System.out.println("Testing connectivity to " + config.getRedisHost() + ":" + config.getRedisPort() + "...");
        try {
            RedisClient testClient = clientFactory.get();
            testClient.connect();
            boolean pingResult = testClient.ping();
            testClient.close();

            if (pingResult) {
                System.out.println("✓ Connection test successful");
            } else {
                System.out.println("✗ Connection test failed - PING returned false");
                throw new Exception("Redis server connectivity test failed");
            }
        } catch (Exception e) {
            System.out.println("✗ Connection test failed: " + e.getMessage());
            System.out.println("Please check:");
//            System.out.println("  - Redis server is running at " + config.getRedisHost() + ":" + config.getRedisPort());
            System.out.println("  - Network connectivity to the server");
            System.out.println("  - Firewall settings");
            System.out.println("  - Authentication requirements");
            throw new Exception("Cannot connect to Redis server: " + e.getMessage(), e);
        }

        PerformanceMetrics metrics = new PerformanceMetrics();
        AtomicBoolean running = new AtomicBoolean(true);

        // Create thread pool for workers
        ExecutorService executor = Executors.newFixedThreadPool(config.getConcurrentConnections());
        List<Future<?>> futures = new ArrayList<>();

        // Test connectivity first
        RedisClient testClient = clientFactory.get();
        testClient.connect();
        testClient.ping();
        testClient.close();
        
        try {
            // Start worker threads - each gets its own client
            for (int i = 0; i < config.getConcurrentConnections(); i++) {
                RedisClient client = clientFactory.get();
                PerformanceWorker worker = new PerformanceWorker(client, config, metrics, running, i);
                Future<?> future = executor.submit(worker);
                futures.add(future);
            }

            System.out.println("Started " + config.getConcurrentConnections() + " worker threads");

            // Warmup period
            if (config.getWarmupSeconds() > 0) {
                System.out.println("Warming up for " + config.getWarmupSeconds() + " seconds...");
                Thread.sleep(config.getWarmupSeconds() * 1000L);

                // Reset metrics after warmup (keep same object reference)
                metrics.reset();

                System.out.println("Warmup complete. Starting measurement...");
            }

            // Run test for specified duration with progress reporting
            long testStartTime = System.currentTimeMillis();
            long testDurationMs = config.getTestDurationSeconds() * 1000L;
            // long lastReportTime = testStartTime;

            while (System.currentTimeMillis() - testStartTime < testDurationMs) {
                Thread.sleep(5000); // Report every 5 seconds

                long currentTime = System.currentTimeMillis();
                long elapsedSeconds = (currentTime - testStartTime) / 1000;
                long remainingSeconds = config.getTestDurationSeconds() - elapsedSeconds;

                String progressReport = String.format("[%02d:%02d] %s",
                    elapsedSeconds / 60, elapsedSeconds % 60, metrics.getSummary());
                System.out.println(progressReport);

                // Check if we have any activity
                if (elapsedSeconds > 10 && metrics.getTotalRequests() == 0) {
                    System.out.println("WARNING: No requests processed after 10 seconds. Check worker threads.");
                    logger.warn("No requests processed after 10 seconds of testing");
                }

                if (remainingSeconds <= 0) break;
            }

            // Stop all workers
            running.set(false);

            // Wait for all workers to complete
            System.out.println("Stopping workers...");
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    logger.error("Worker completion error: {}", e.getMessage());
                }
            }

        } finally {
            executor.shutdown();
        }

        // Finalize metrics to capture final RPS before any delays
        metrics.finalizeMetrics();

        // Final results
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Performance Test Results for " + clientName);
        System.out.println("=".repeat(80));
        System.out.println("Final Results: " + metrics.getSummary());

        if (metrics.getTotalRequests() == 0) {
            System.out.println("WARNING: No requests were processed during the test!");
            System.out.println("This usually indicates connection or configuration issues.");
        }

        System.out.println("=".repeat(80));

        return metrics;
    }

    public static PerformanceTest createJedisTest(TestConfiguration config) {
        return new PerformanceTest(config, () -> new JedisClient(config), "Jedis");
    }

    public static PerformanceTest createJedisPooledTest(TestConfiguration config) {
        return new PerformanceTest(config, () -> new JedisClient(config, true), "Jedis (Pooled)");
    }

    public static PerformanceTest createValkeyGlideTest(TestConfiguration config) {
        return new PerformanceTest(config, () -> new ValkeyGlideClient(config), "Valkey-Glide");
    }

    public String getClientName() {
        return clientName;
    }
}
