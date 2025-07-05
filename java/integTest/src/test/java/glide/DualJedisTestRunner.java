/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import org.junit.jupiter.api.*;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Test runner that can load and test both GLIDE Jedis compatibility layer
 * and actual Jedis implementation side by side for comparison.
 */
public class DualJedisTestRunner {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    
    private ClassLoader glideClassLoader;
    private ClassLoader jedisClassLoader;
    private boolean hasGlideJedis = false;
    private boolean hasActualJedis = false;

    public static void main(String[] args) {
        DualJedisTestRunner runner = new DualJedisTestRunner();
        runner.runAllTests();
    }

    public void runAllTests() {
        System.out.println("=== Dual Jedis Test Runner ===");
        System.out.println("Comparing GLIDE Jedis compatibility with actual Jedis");
        System.out.println();

        setupClassLoaders();
        
        if (!hasGlideJedis && !hasActualJedis) {
            System.out.println("❌ No Jedis implementations found!");
            return;
        }

        runConnectivityTest();
        runBasicOperationsTest();
        runStringHandlingTest();
        runConfigurationTest();
        runPoolTest();
        runPerformanceComparison();
        runStressTest();
        
        System.out.println("\n=== Test Summary ===");
        if (hasGlideJedis && hasActualJedis) {
            System.out.println("✅ Both implementations tested and compared");
        } else if (hasGlideJedis) {
            System.out.println("✅ GLIDE Jedis compatibility layer tested");
            System.out.println("ℹ️  Actual Jedis not available for comparison");
        } else {
            System.out.println("✅ Actual Jedis tested");
            System.out.println("ℹ️  GLIDE Jedis compatibility layer not available");
        }
    }

    private void setupClassLoaders() {
        // Setup GLIDE Jedis (current classpath)
        try {
            glideClassLoader = Thread.currentThread().getContextClassLoader();
            Class<?> glideJedisClass = glideClassLoader.loadClass("redis.clients.jedis.Jedis");
            hasGlideJedis = true;
            System.out.println("✅ GLIDE Jedis compatibility layer found");
        } catch (ClassNotFoundException e) {
            System.out.println("❌ GLIDE Jedis compatibility layer not found");
            hasGlideJedis = false;
        }

        // Try to setup actual Jedis (from external JAR if available)
        try {
            // Look for actual Jedis JAR in common locations
            String[] possiblePaths = {
                "lib/jedis-*.jar",
                "../lib/jedis-*.jar",
                "../../lib/jedis-*.jar",
                System.getProperty("jedis.jar.path", "")
            };
            
            File jedisJar = findJedisJar(possiblePaths);
            if (jedisJar != null) {
                jedisClassLoader = new URLClassLoader(
                    new URL[]{jedisJar.toURI().toURL()},
                    ClassLoader.getSystemClassLoader().getParent() // Avoid parent delegation
                );
                Class<?> actualJedisClass = jedisClassLoader.loadClass("redis.clients.jedis.Jedis");
                hasActualJedis = true;
                System.out.println("✅ Actual Jedis found: " + jedisJar.getPath());
            } else {
                System.out.println("❌ Actual Jedis JAR not found");
                System.out.println("   Set -Djedis.jar.path=/path/to/jedis.jar to enable comparison");
                hasActualJedis = false;
            }
        } catch (Exception e) {
            System.out.println("❌ Failed to load actual Jedis: " + e.getMessage());
            hasActualJedis = false;
        }
    }

    private File findJedisJar(String[] paths) {
        for (String path : paths) {
            if (path.isEmpty()) continue;
            
            File file = new File(path);
            if (file.exists() && file.getName().endsWith(".jar")) {
                return file;
            }
            
            // Handle wildcard patterns
            if (path.contains("*")) {
                File dir = new File(path.substring(0, path.lastIndexOf("/")));
                if (dir.exists() && dir.isDirectory()) {
                    File[] jars = dir.listFiles((d, name) -> 
                        name.startsWith("jedis") && name.endsWith(".jar"));
                    if (jars != null && jars.length > 0) {
                        return jars[0]; // Return first match
                    }
                }
            }
        }
        return null;
    }

    private void runConnectivityTest() {
        System.out.println("\n--- Connectivity Test ---");
        
        if (hasGlideJedis) {
            testConnectivity("GLIDE Jedis", glideClassLoader);
        }
        
        if (hasActualJedis) {
            testConnectivity("Actual Jedis", jedisClassLoader);
        }
    }

    private void testConnectivity(String implementation, ClassLoader classLoader) {
        try {
            Class<?> jedisClass = classLoader.loadClass("redis.clients.jedis.Jedis");
            Object jedis = jedisClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            
            String result = (String) jedisClass.getMethod("ping").invoke(jedis);
            System.out.println("✅ " + implementation + " PING: " + result);
            
            jedisClass.getMethod("close").invoke(jedis);
        } catch (Exception e) {
            System.out.println("❌ " + implementation + " connectivity failed: " + e.getMessage());
        }
    }

    private void runBasicOperationsTest() {
        System.out.println("\n--- Basic Operations Test ---");
        
        String testKey = "dual_test:basic:" + System.currentTimeMillis();
        String testValue = "test_value_" + System.currentTimeMillis();
        
        String glideResult = null;
        String actualResult = null;
        
        if (hasGlideJedis) {
            glideResult = testBasicOperations("GLIDE Jedis", glideClassLoader, testKey + ":glide", testValue);
        }
        
        if (hasActualJedis) {
            actualResult = testBasicOperations("Actual Jedis", jedisClassLoader, testKey + ":actual", testValue);
        }
        
        if (glideResult != null && actualResult != null) {
            if (glideResult.equals(actualResult)) {
                System.out.println("✅ Basic operations results are identical");
            } else {
                System.out.println("⚠️  Basic operations results differ:");
                System.out.println("   GLIDE: " + glideResult);
                System.out.println("   Actual: " + actualResult);
            }
        }
    }

    private String testBasicOperations(String implementation, ClassLoader classLoader, String key, String value) {
        try {
            Class<?> jedisClass = classLoader.loadClass("redis.clients.jedis.Jedis");
            Object jedis = jedisClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            
            // SET operation
            String setResult = (String) jedisClass.getMethod("set", String.class, String.class)
                    .invoke(jedis, key, value);
            
            // GET operation
            String getResult = (String) jedisClass.getMethod("get", String.class)
                    .invoke(jedis, key);
            
            System.out.println("✅ " + implementation + " SET: " + setResult + ", GET: " + getResult);
            
            jedisClass.getMethod("close").invoke(jedis);
            return getResult;
        } catch (Exception e) {
            System.out.println("❌ " + implementation + " basic operations failed: " + e.getMessage());
            return null;
        }
    }

    private void runStringHandlingTest() {
        System.out.println("\n--- String Handling Test ---");
        
        String[] testStrings = {
            "simple_string",
            "string with spaces",
            "special!@#$%^&*()characters",
            "unicode_测试_🚀_emoji",
            "very_long_string_" + "x".repeat(1000)
        };
        
        for (String testString : testStrings) {
            String key = "dual_test:string:" + testString.hashCode();
            
            boolean glideSuccess = false;
            boolean actualSuccess = false;
            
            if (hasGlideJedis) {
                glideSuccess = testStringHandling("GLIDE Jedis", glideClassLoader, key + ":glide", testString);
            }
            
            if (hasActualJedis) {
                actualSuccess = testStringHandling("Actual Jedis", jedisClassLoader, key + ":actual", testString);
            }
            
            if (hasGlideJedis && hasActualJedis) {
                if (glideSuccess == actualSuccess) {
                    System.out.println("✅ String handling consistent for: " + 
                        (testString.length() > 50 ? testString.substring(0, 50) + "..." : testString));
                } else {
                    System.out.println("⚠️  String handling differs for: " + testString);
                }
            }
        }
    }

    private boolean testStringHandling(String implementation, ClassLoader classLoader, String key, String value) {
        try {
            Class<?> jedisClass = classLoader.loadClass("redis.clients.jedis.Jedis");
            Object jedis = jedisClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            
            jedisClass.getMethod("set", String.class, String.class).invoke(jedis, key, value);
            String result = (String) jedisClass.getMethod("get", String.class).invoke(jedis, key);
            
            boolean success = value.equals(result);
            if (!success) {
                System.out.println("❌ " + implementation + " string mismatch for: " + value);
            }
            
            jedisClass.getMethod("close").invoke(jedis);
            return success;
        } catch (Exception e) {
            System.out.println("❌ " + implementation + " string handling failed: " + e.getMessage());
            return false;
        }
    }

    private void runConfigurationTest() {
        System.out.println("\n--- Configuration Test ---");
        
        if (hasGlideJedis) {
            testConfiguration("GLIDE Jedis", glideClassLoader);
        }
        
        if (hasActualJedis) {
            testConfiguration("Actual Jedis", jedisClassLoader);
        }
    }

    private void testConfiguration(String implementation, ClassLoader classLoader) {
        try {
            Class<?> configClass = classLoader.loadClass("redis.clients.jedis.DefaultJedisClientConfig");
            Object builder = configClass.getMethod("builder").invoke(null);
            
            // Build configuration
            builder = builder.getClass().getMethod("host", String.class).invoke(builder, REDIS_HOST);
            builder = builder.getClass().getMethod("port", int.class).invoke(builder, REDIS_PORT);
            builder = builder.getClass().getMethod("socketTimeoutMillis", int.class).invoke(builder, 2000);
            Object config = builder.getClass().getMethod("build").invoke(builder);
            
            // Create Jedis with configuration
            Class<?> jedisClass = classLoader.loadClass("redis.clients.jedis.Jedis");
            Object jedis = jedisClass.getConstructor(String.class, int.class, config.getClass())
                    .newInstance(REDIS_HOST, REDIS_PORT, config);
            
            String result = (String) jedisClass.getMethod("ping").invoke(jedis);
            System.out.println("✅ " + implementation + " configuration test: " + result);
            
            jedisClass.getMethod("close").invoke(jedis);
        } catch (Exception e) {
            System.out.println("❌ " + implementation + " configuration test failed: " + e.getMessage());
        }
    }

    private void runPoolTest() {
        System.out.println("\n--- Pool Test ---");
        
        if (hasGlideJedis) {
            testPool("GLIDE Jedis", glideClassLoader);
        }
        
        if (hasActualJedis) {
            testPool("Actual Jedis", jedisClassLoader);
        }
    }

    private void testPool(String implementation, ClassLoader classLoader) {
        try {
            Class<?> poolClass = classLoader.loadClass("redis.clients.jedis.JedisPool");
            Object pool = poolClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            
            // Get resource from pool
            Object jedis = poolClass.getMethod("getResource").invoke(pool);
            String result = (String) jedis.getClass().getMethod("ping").invoke(jedis);
            
            // Return resource
            poolClass.getMethod("returnResource", Object.class).invoke(pool, jedis);
            
            // Close pool
            poolClass.getMethod("close").invoke(pool);
            
            System.out.println("✅ " + implementation + " pool test: " + result);
        } catch (Exception e) {
            System.out.println("❌ " + implementation + " pool test failed: " + e.getMessage());
        }
    }

    private void runPerformanceComparison() {
        if (!hasGlideJedis || !hasActualJedis) {
            System.out.println("\n--- Performance Comparison ---");
            System.out.println("⚠️  Skipping performance comparison - both implementations not available");
            return;
        }

        System.out.println("\n--- Performance Comparison ---");
        
        int iterations = 1000;
        String keyPrefix = "dual_test:perf:" + System.currentTimeMillis();
        String value = "performance_test_value";
        
        // Warm up
        performanceWarmup(glideClassLoader, keyPrefix + ":glide:warmup", value, 100);
        performanceWarmup(jedisClassLoader, keyPrefix + ":actual:warmup", value, 100);
        
        // Measure GLIDE performance
        long glideTime = measurePerformance("GLIDE Jedis", glideClassLoader, 
                keyPrefix + ":glide", value, iterations);
        
        // Measure actual Jedis performance
        long actualTime = measurePerformance("Actual Jedis", jedisClassLoader, 
                keyPrefix + ":actual", value, iterations);
        
        if (glideTime > 0 && actualTime > 0) {
            double ratio = (double) glideTime / actualTime;
            System.out.println("Performance ratio (GLIDE/Actual): " + String.format("%.2f", ratio));
            
            if (ratio < 0.8) {
                System.out.println("🚀 GLIDE Jedis is significantly faster!");
            } else if (ratio < 1.2) {
                System.out.println("✅ GLIDE Jedis performance is comparable");
            } else if (ratio < 2.0) {
                System.out.println("⚠️  GLIDE Jedis is slower but acceptable");
            } else {
                System.out.println("❌ GLIDE Jedis is significantly slower");
            }
        }
    }

    private void performanceWarmup(ClassLoader classLoader, String keyPrefix, String value, int iterations) {
        try {
            Class<?> jedisClass = classLoader.loadClass("redis.clients.jedis.Jedis");
            Object jedis = jedisClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            
            for (int i = 0; i < iterations; i++) {
                jedisClass.getMethod("set", String.class, String.class)
                        .invoke(jedis, keyPrefix + i, value);
            }
            
            jedisClass.getMethod("close").invoke(jedis);
        } catch (Exception e) {
            // Ignore warmup errors
        }
    }

    private long measurePerformance(String implementation, ClassLoader classLoader, 
                                   String keyPrefix, String value, int iterations) {
        try {
            Class<?> jedisClass = classLoader.loadClass("redis.clients.jedis.Jedis");
            Object jedis = jedisClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                jedisClass.getMethod("set", String.class, String.class)
                        .invoke(jedis, keyPrefix + i, value);
                jedisClass.getMethod("get", String.class)
                        .invoke(jedis, keyPrefix + i);
            }
            
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            
            jedisClass.getMethod("close").invoke(jedis);
            
            long durationMs = TimeUnit.NANOSECONDS.toMillis(duration);
            System.out.println(implementation + " (" + iterations + " ops): " + durationMs + "ms");
            
            return duration;
        } catch (Exception e) {
            System.out.println("❌ " + implementation + " performance test failed: " + e.getMessage());
            return -1;
        }
    }

    private void runStressTest() {
        System.out.println("\n--- Stress Test ---");
        
        int concurrentClients = 10;
        int operationsPerClient = 100;
        
        if (hasGlideJedis) {
            runConcurrentTest("GLIDE Jedis", glideClassLoader, concurrentClients, operationsPerClient);
        }
        
        if (hasActualJedis) {
            runConcurrentTest("Actual Jedis", jedisClassLoader, concurrentClients, operationsPerClient);
        }
    }

    private void runConcurrentTest(String implementation, ClassLoader classLoader, 
                                  int clientCount, int operationsPerClient) {
        try {
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();
            
            for (int i = 0; i < clientCount; i++) {
                final int clientId = i;
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        Class<?> jedisClass = classLoader.loadClass("redis.clients.jedis.Jedis");
                        Object jedis = jedisClass.getConstructor(String.class, int.class)
                                .newInstance(REDIS_HOST, REDIS_PORT);
                        
                        for (int j = 0; j < operationsPerClient; j++) {
                            String key = "stress_test:" + implementation + ":" + clientId + ":" + j;
                            String value = "value_" + j;
                            
                            jedisClass.getMethod("set", String.class, String.class)
                                    .invoke(jedis, key, value);
                            String result = (String) jedisClass.getMethod("get", String.class)
                                    .invoke(jedis, key);
                            
                            if (!value.equals(result)) {
                                return false;
                            }
                        }
                        
                        jedisClass.getMethod("close").invoke(jedis);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                futures.add(future);
            }
            
            // Wait for all futures to complete
            long successCount = futures.stream()
                    .mapToLong(f -> {
                        try {
                            return f.get(30, TimeUnit.SECONDS) ? 1 : 0;
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .sum();
            
            System.out.println(implementation + " stress test: " + successCount + "/" + clientCount + 
                    " clients succeeded (" + (clientCount * operationsPerClient) + " total operations)");
            
        } catch (Exception e) {
            System.out.println("❌ " + implementation + " stress test failed: " + e.getMessage());
        }
    }
}
