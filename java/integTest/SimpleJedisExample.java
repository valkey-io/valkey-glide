/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.JedisPool;

/**
 * Simple example demonstrating how to use the Jedis compatibility layer with Valkey GLIDE. This
 * shows the exact same code patterns that users would use with original Jedis.
 */
public class SimpleJedisExample {

    public static void main(String[] args) {
        System.out.println("=== Valkey GLIDE Jedis Compatibility Example ===");

        // Test direct connection
        testDirectConnection();

        // Test pool connection
        testPoolConnection();

        System.out.println("=== Example completed successfully ===");
    }

    /** Demonstrates direct Jedis connection usage - exactly like original Jedis */
    private static void testDirectConnection() {
        System.out.println("\n--- Direct Connection Example ---");

        // Create a direct connection (same as original Jedis)
        Jedis jedis = new Jedis("localhost", 6379);

        try {
            // Test connectivity
            System.out.println("PING: " + jedis.ping());

            // Set and get a value
            jedis.set("example:user:1", "John Doe");
            String user = jedis.get("example:user:1");
            System.out.println("User: " + user);

            // Set and get another value
            jedis.set("example:counter", "42");
            String counter = jedis.get("example:counter");
            System.out.println("Counter: " + counter);

        } finally {
            // Always close the connection
            jedis.close();
            System.out.println("Direct connection closed");
        }
    }

    /** Demonstrates JedisPool usage - exactly like original Jedis */
    private static void testPoolConnection() {
        System.out.println("\n--- Pool Connection Example ---");

        // Create a connection pool (same as original Jedis)
        JedisPool pool = new JedisPool("localhost", 6379);

        try {
            // Use try-with-resources for automatic connection management
            try (Jedis jedis = pool.getResource()) {
                // Test connectivity
                System.out.println("PING: " + jedis.ping());

                // Set and get values
                jedis.set("example:session:abc123", "user_data");
                String session = jedis.get("example:session:abc123");
                System.out.println("Session: " + session);

                // Test with custom ping message
                System.out.println("Custom PING: " + jedis.ping("Hello GLIDE!"));
            }
            // Connection is automatically returned to pool here

            // Use another connection from the pool
            try (Jedis jedis = pool.getResource()) {
                jedis.set("example:config:timeout", "30");
                String timeout = jedis.get("example:config:timeout");
                System.out.println("Config timeout: " + timeout);
            }

        } finally {
            // Always close the pool
            pool.close();
            System.out.println("Connection pool closed");
        }
    }
}
