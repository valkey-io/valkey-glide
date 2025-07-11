/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
import redis.clients.jedis.*;

public class SimpleCompatibilityDemo {
    public static void main(String[] args) {
        System.out.println("=== GLIDE Jedis Compatibility Layer Demo ===");

        try {
            // Test basic Jedis functionality
            System.out.println("1. Testing basic Jedis operations...");
            Jedis jedis = new Jedis("localhost", 6379);

            // Test PING
            String pingResult = jedis.ping();
            System.out.println("   PING: " + pingResult);

            // Test SET/GET
            String setResult = jedis.set("demo:test", "Hello GLIDE!");
            System.out.println("   SET: " + setResult);

            String getValue = jedis.get("demo:test");
            System.out.println("   GET: " + getValue);

            // Test configuration
            System.out.println("\n2. Testing configuration...");
            JedisClientConfig config =
                    DefaultJedisClientConfig.builder()
                            .host("localhost")
                            .port(6379)
                            .socketTimeoutMillis(2000)
                            .build();

            Jedis configuredJedis = new Jedis("localhost", 6379, config);
            String configPing = configuredJedis.ping();
            System.out.println("   Configured Jedis PING: " + configPing);

            // Test pool
            System.out.println("\n3. Testing JedisPool...");
            JedisPool pool = new JedisPool("localhost", 6379);

            try (Jedis pooledJedis = pool.getResource()) {
                String poolPing = pooledJedis.ping();
                System.out.println("   Pooled Jedis PING: " + poolPing);

                pooledJedis.set("demo:pool", "Pool test");
                String poolValue = pooledJedis.get("demo:pool");
                System.out.println("   Pool GET: " + poolValue);
            }

            // Cleanup
            jedis.del("demo:test");
            configuredJedis.del("demo:pool");

            jedis.close();
            configuredJedis.close();
            pool.close();

            System.out.println("\n✅ All compatibility tests passed!");
            System.out.println("✅ GLIDE Jedis compatibility layer is working correctly!");

        } catch (Exception e) {
            System.err.println("❌ Compatibility test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
