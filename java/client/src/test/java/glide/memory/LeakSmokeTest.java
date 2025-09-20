/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClusterClient;
import glide.api.logging.Logger;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A lightweight leak smoke test that performs many GET/SET with large values to check heap growth
 * is bounded. Tests specifically with values >16KB to trigger DirectByteBuffer path in JNI
 * implementation. Disabled by default; enable locally or in CI leak jobs.
 */
public class LeakSmokeTest {

    @Test
    public void getSetDoesNotLeakHeap() throws Exception {
        boolean enabled = Boolean.getBoolean("RUN_LEAK_SMOKE");
        Assumptions.assumeTrue(enabled, "Leak smoke test disabled; run with -DRUN_LEAK_SMOKE=true");

        // Use environment variables or system properties for configuration
        String host =
                System.getProperty(
                        "LEAK_HOST",
                        System.getenv("ELASTICACHE_HOST") != null
                                ? System.getenv("ELASTICACHE_HOST")
                                : "127.0.0.1");
        int port = Integer.getInteger("LEAK_PORT", 6379);
        // Use TLS if specified via environment or property
        boolean tls =
                Boolean.parseBoolean(
                        System.getProperty(
                                "LEAK_TLS",
                                System.getenv("LEAK_TLS") != null ? System.getenv("LEAK_TLS") : "false"));

        // Skip if CLI ping fails to avoid false positives
        Assumptions.assumeTrue(
                cliPing(host, port, tls, Duration.ofSeconds(1)),
                () -> "valkey-cli ping failed for " + host + ":" + port + (tls ? " (tls)" : ""));

        Logger.log(
                Logger.Level.INFO,
                "LeakSmokeTest",
                "Starting leak test with host=" + host + ", port=" + port + ", tls=" + tls);

        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .address(NodeAddress.builder().host(host).port(port).build())
                        .useTLS(tls)
                        .requestTimeout(10000) // 10 second timeout for operations
                        .build();

        try (GlideClusterClient client =
                GlideClusterClient.createClient(config).get(10, TimeUnit.SECONDS)) {

            // Create test data of various sizes, ensuring we test the DirectByteBuffer path
            // Values >16KB will trigger the DirectByteBuffer optimization in JNI
            int smallSize = 1024; // 1KB - standard path
            int mediumSize = 8 * 1024; // 8KB - standard path
            int largeSize = 32 * 1024; // 32KB - DirectByteBuffer path
            int veryLargeSize = Integer.getInteger("LEAK_VALUE_SIZE", 128 * 1024); // 128KB default

            String smallValue = "x".repeat(smallSize);
            String mediumValue = "y".repeat(mediumSize);
            String largeValue = "z".repeat(largeSize);
            String veryLargeValue = "w".repeat(veryLargeSize);

            // Also test with non-ASCII to stress UTF-8 encoding with large data
            String unicodeLarge = "测试数据🔥".repeat(largeSize / 10); // >16KB in UTF-8

            Logger.log(
                    Logger.Level.INFO,
                    "LeakSmokeTest",
                    String.format(
                            "Value sizes: small=%d, medium=%d, large=%d, veryLarge=%d bytes",
                            smallSize, mediumSize, largeSize, veryLargeSize));

            Logger.log(
                    Logger.Level.INFO, "LeakSmokeTest", "Values >16KB will use DirectByteBuffer path in JNI");

            // Warmup with mixed sizes
            Logger.log(Logger.Level.INFO, "LeakSmokeTest", "Starting warmup phase...");
            for (int i = 0; i < 1000; i++) {
                String key = "warmup:" + i;
                if (i % 5 == 0) {
                    client.set(key, smallValue).get(10, TimeUnit.SECONDS);
                } else if (i % 5 == 1) {
                    client.set(key, mediumValue).get(10, TimeUnit.SECONDS);
                } else if (i % 5 == 2) {
                    // This triggers DirectByteBuffer path
                    client.set(key, largeValue).get(10, TimeUnit.SECONDS);
                } else if (i % 5 == 3) {
                    // This also triggers DirectByteBuffer path
                    client.set(key, veryLargeValue).get(10, TimeUnit.SECONDS);
                } else {
                    // Unicode >16KB to test UTF-8 encoding in DirectByteBuffer path
                    client.set(key, unicodeLarge).get(10, TimeUnit.SECONDS);
                }
                client.get(key).get(10, TimeUnit.SECONDS);
            }

            // Measure heap after GC
            System.gc();
            Thread.sleep(500);
            long baseline = usedHeapMb();
            Logger.log(
                    Logger.Level.INFO,
                    "LeakSmokeTest",
                    "Baseline heap usage after warmup: " + baseline + " MB");

            // Run many operations focusing on large values that use DirectByteBuffer
            int loops = Integer.getInteger("LEAK_LOOPS", 10000);
            Logger.log(
                    Logger.Level.INFO,
                    "LeakSmokeTest",
                    "Running " + loops + " iterations with focus on >16KB values...");

            for (int i = 0; i < loops; i++) {
                String key = "test:" + i;

                // Heavily bias toward large values to stress DirectByteBuffer path
                if (i % 10 < 4) {
                    // 40% - Large values (32KB) - DirectByteBuffer path
                    client.set(key, largeValue).get(10, TimeUnit.SECONDS);
                    String result = client.get(key).get(10, TimeUnit.SECONDS);
                    // Verify we got the right data back
                    if (!largeValue.equals(result)) {
                        Logger.log(
                                Logger.Level.ERROR,
                                "LeakSmokeTest",
                                "Data mismatch for key " + key + ", expected " + largeSize + " bytes");
                    }
                } else if (i % 10 < 7) {
                    // 30% - Very large values (128KB) - DirectByteBuffer path
                    client.set(key, veryLargeValue).get(10, TimeUnit.SECONDS);
                    client.get(key).get(10, TimeUnit.SECONDS);
                } else if (i % 10 == 7) {
                    // 10% - Unicode large values - DirectByteBuffer with UTF-8
                    client.set(key, unicodeLarge).get(10, TimeUnit.SECONDS);
                    client.get(key).get(10, TimeUnit.SECONDS);
                } else if (i % 10 == 8) {
                    // 10% - Medium values - Standard path for comparison
                    client.set(key, mediumValue).get(10, TimeUnit.SECONDS);
                    client.get(key).get(10, TimeUnit.SECONDS);
                } else {
                    // 10% - Extremely large value (512KB) - Really stress DirectByteBuffer
                    String extremeLarge = "E".repeat(512 * 1024);
                    client.set(key, extremeLarge).get(10, TimeUnit.SECONDS);
                    client.get(key).get(10, TimeUnit.SECONDS);
                }

                // Periodic progress report
                if (i > 0 && i % 2000 == 0) {
                    long current = usedHeapMb();
                    Logger.log(
                            Logger.Level.INFO,
                            "LeakSmokeTest",
                            String.format(
                                    "Progress: %d/%d iterations, current heap: %d MB (growth: %d MB)",
                                    i, loops, current, (current - baseline)));
                }
            }

            // Force GC and measure final heap
            System.gc();
            Thread.sleep(500);
            long after = usedHeapMb();

            // Calculate results
            long growth = after - baseline;
            long maxMb = Long.getLong("LEAK_MAX_HEAP_GROWTH_MB", 150L); // Allow more for large values
            double avgBytesPerOp = (growth * 1024.0 * 1024.0) / loops;

            Logger.log(Logger.Level.INFO, "LeakSmokeTest", "=== RESULTS ===");
            Logger.log(Logger.Level.INFO, "LeakSmokeTest", "Final heap usage: " + after + " MB");
            Logger.log(
                    Logger.Level.INFO,
                    "LeakSmokeTest",
                    "Heap growth: " + growth + " MB (max allowed: " + maxMb + " MB)");
            Logger.log(
                    Logger.Level.INFO,
                    "LeakSmokeTest",
                    String.format("Average growth per operation: %.2f bytes", avgBytesPerOp));

            // With large values, some growth is expected due to string pooling and JVM internals
            // but it should be bounded
            Logger.log(
                    Logger.Level.INFO,
                    "LeakSmokeTest",
                    growth < maxMb
                            ? "PASSED - No memory leak detected"
                            : "FAILED - Possible memory leak in DirectByteBuffer handling");

            assertTrue(
                    growth < maxMb,
                    String.format(
                            "Heap growth too large: %d MB (max: %d MB). "
                                    + "This indicates a memory leak in DirectByteBuffer handling. "
                                    + "Average: %.2f bytes/op",
                            growth, maxMb, avgBytesPerOp));
        }
    }

    private static long usedHeapMb() {
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return used / (1024 * 1024);
    }

    private static boolean cliPing(String host, int port, boolean tls, Duration timeout) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (tls) {
                pb.command("valkey-cli", "-h", host, "-p", String.valueOf(port), "--tls", "ping");
            } else {
                pb.command("valkey-cli", "-h", host, "-p", String.valueOf(port), "ping");
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            String out = new String(p.getInputStream().readAllBytes());
            return p.exitValue() == 0 && out.trim().toUpperCase().contains("PONG");
        } catch (Exception e) {
            return false;
        }
    }
}
