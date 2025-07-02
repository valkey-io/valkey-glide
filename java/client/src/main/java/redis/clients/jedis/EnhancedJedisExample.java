/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Enhanced example demonstrating configuration mapping, SSL/TLS support, and resource lifecycle
 * management in the Jedis compatibility layer.
 */
public class EnhancedJedisExample {

    public static void main(String[] args) {
        System.out.println("=== Enhanced Jedis Wrapper Example ===");

        // Test basic configuration
        testBasicConfiguration();

        // Test SSL configuration
        testSslConfiguration();

        // Test advanced pool configuration
        testAdvancedPoolConfiguration();

        // Test resource lifecycle management
        testResourceLifecycleManagement();

        System.out.println("=== Enhanced example completed ===");
    }

    /** Test basic configuration mapping. */
    private static void testBasicConfiguration() {
        System.out.println("\n--- Testing Basic Configuration ---");

        try {
            // Create configuration with custom timeouts and authentication
            JedisClientConfig config =
                    DefaultJedisClientConfig.builder()
                            .socketTimeoutMillis(5000)
                            .connectionTimeoutMillis(3000)
                            .password("mypassword")
                            .clientName("test-client")
                            .database(1)
                            .protocol(RedisProtocol.RESP2)
                            .build();

            // Create Jedis with configuration
            Jedis jedis = new Jedis("localhost", 6379, config);

            System.out.println("Configuration - Socket timeout: " + config.getSocketTimeoutMillis());
            System.out.println(
                    "Configuration - Connection timeout: " + config.getConnectionTimeoutMillis());
            System.out.println("Configuration - Client name: " + config.getClientName());
            System.out.println("Configuration - Database: " + config.getDatabase());
            System.out.println("Configuration - Protocol: " + config.getRedisProtocol());

            // Test basic operations
            jedis.set("config-test", "success");
            String result = jedis.get("config-test");
            System.out.println("Configuration test result: " + result);

            jedis.close();
            System.out.println("Basic configuration test completed");

        } catch (Exception e) {
            System.err.println("Basic configuration test failed: " + e.getMessage());
        }
    }

    /** Test SSL/TLS configuration. */
    private static void testSslConfiguration() {
        System.out.println("\n--- Testing SSL Configuration ---");

        try {
            // Create a trust-all SSL context for testing (DO NOT USE IN PRODUCTION)
            SSLContext sslContext = createTrustAllSslContext();

            // Create SSL configuration
            JedisClientConfig sslConfig =
                    DefaultJedisClientConfig.builder()
                            .ssl(true)
                            .sslSocketFactory(sslContext.getSocketFactory())
                            .socketTimeoutMillis(10000)
                            .build();

            System.out.println("SSL Configuration - SSL enabled: " + sslConfig.isSsl());
            System.out.println(
                    "SSL Configuration - Socket factory: "
                            + (sslConfig.getSslSocketFactory() != null ? "configured" : "not configured"));

            // Note: This will fail without a real SSL-enabled Redis/Valkey server
            // but demonstrates the configuration mapping
            System.out.println("SSL configuration mapping completed (connection not attempted)");

        } catch (Exception e) {
            System.err.println("SSL configuration test failed: " + e.getMessage());
        }
    }

    /** Test advanced pool configuration. */
    private static void testAdvancedPoolConfiguration() {
        System.out.println("\n--- Testing Advanced Pool Configuration ---");

        try {
            // Create advanced pool configuration
            JedisClientConfig poolConfig =
                    DefaultJedisClientConfig.builder()
                            .socketTimeoutMillis(2000)
                            .connectionTimeoutMillis(1000)
                            .clientName("pool-client")
                            .build();

            // Create pool with advanced settings
            JedisPool pool = new JedisPool("localhost", 6379, poolConfig, 10, 5000);

            System.out.println("Pool configuration - Max connections: " + pool.getMaxTotal());
            System.out.println("Pool configuration - Max wait time: " + pool.getMaxWaitMillis());
            System.out.println(
                    "Pool configuration - Client config: "
                            + (pool.getConfig() != null ? "configured" : "not configured"));

            // Test pool operations
            try (Jedis jedis = pool.getResource()) {
                System.out.println("Pool stats before operation: " + pool.getPoolStats());

                jedis.set("pool-test", "advanced-config");
                String result = jedis.get("pool-test");
                System.out.println("Pool test result: " + result);

                System.out.println("Pool stats during operation: " + pool.getPoolStats());
            }

            System.out.println("Pool stats after operation: " + pool.getPoolStats());

            pool.close();
            System.out.println("Advanced pool configuration test completed");

        } catch (Exception e) {
            System.err.println("Advanced pool configuration test failed: " + e.getMessage());
        }
    }

    /** Test resource lifecycle management. */
    private static void testResourceLifecycleManagement() {
        System.out.println("\n--- Testing Resource Lifecycle Management ---");

        try {
            ResourceLifecycleManager manager = ResourceLifecycleManager.getInstance();

            System.out.println("Initial tracked resources: " + manager.getTrackedResourceCount());

            // Create multiple resources to test tracking
            JedisPool pool1 = new JedisPool("localhost", 6379);
            JedisPool pool2 = new JedisPool("localhost", 6379);

            System.out.println(
                    "Tracked resources after creating pools: " + manager.getTrackedResourceCount());

            // Test resource creation and cleanup
            try (Jedis jedis1 = pool1.getResource();
                    Jedis jedis2 = pool2.getResource()) {

                System.out.println(
                        "Tracked resources with active connections: " + manager.getTrackedResourceCount());

                // Test operations
                jedis1.set("lifecycle-test-1", "resource-1");
                jedis2.set("lifecycle-test-2", "resource-2");

                System.out.println("Resource 1 result: " + jedis1.get("lifecycle-test-1"));
                System.out.println("Resource 2 result: " + jedis2.get("lifecycle-test-2"));
            }

            // Close pools
            pool1.close();
            pool2.close();

            System.out.println("Tracked resources after cleanup: " + manager.getTrackedResourceCount());
            System.out.println("Resource lifecycle management test completed");

        } catch (Exception e) {
            System.err.println("Resource lifecycle management test failed: " + e.getMessage());
        }
    }

    /**
     * Create a trust-all SSL context for testing purposes. WARNING: This is insecure and should only
     * be used for testing!
     */
    private static SSLContext createTrustAllSslContext() throws Exception {
        TrustManager[] trustAllCerts =
                new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
                };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext;
    }
}
