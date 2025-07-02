/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * Example demonstrating the Jedis wrapper functionality. This example shows the exact code patterns
 * you wanted to support.
 */
public class JedisWrapperExample {

    public static void main(String[] args) {
        System.out.println("=== Jedis Wrapper Example ===");

        // Test simple connection
        testSimpleConnection();

        // Test pool connection
        testPoolConnection();

        System.out.println("=== Example completed successfully ===");
    }

    /** Test the simple connection pattern. */
    private static void testSimpleConnection() {
        System.out.println("\n--- Testing Simple Connection ---");

        try {
            // Simple connection
            Jedis jedis = new Jedis("localhost", 6379);
            jedis.set("key", "value");

            String result = jedis.get("key");
            System.out.println("Simple connection - GET result: " + result);

            jedis.close();
            System.out.println("Simple connection - Connection closed successfully");

        } catch (Exception e) {
            System.err.println("Simple connection failed: " + e.getMessage());
            // This is expected if no Redis/Valkey server is running
        }
    }

    /** Test the pool connection pattern. */
    private static void testPoolConnection() {
        System.out.println("\n--- Testing Pool Connection ---");

        JedisPool pool = null;
        try {
            // Pool connection
            pool = new JedisPool("localhost", 6379);

            try (Jedis jedis = pool.getResource()) {
                jedis.set("key", "value");

                String result = jedis.get("key");
                System.out.println("Pool connection - GET result: " + result);
            }

            System.out.println("Pool connection - Resource returned to pool successfully");

        } catch (Exception e) {
            System.err.println("Pool connection failed: " + e.getMessage());
            // This is expected if no Redis/Valkey server is running
        } finally {
            if (pool != null) {
                pool.close();
                System.out.println("Pool connection - Pool closed successfully");
            }
        }
    }
}
