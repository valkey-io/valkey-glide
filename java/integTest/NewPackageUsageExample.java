/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.JedisPool;

/**
 * Example demonstrating the new package name for the Jedis compatibility layer.
 *
 * <p>PACKAGE CHANGE: - OLD: import redis.clients.jedis.Jedis; - NEW: import
 * compatibility.clients.jedis.Jedis;
 *
 * <p>This change: 1. Avoids conflicts with actual Jedis library 2. Makes it clear this is a
 * compatibility layer 3. Allows coexistence with actual Jedis in same project 4. Maintains exact
 * same API - only import statements change
 */
public class NewPackageUsageExample {

    public static void main(String[] args) {
        System.out.println("=== New Package Usage Example ===");
        System.out.println("Package: compatibility.clients.jedis (was redis.clients.jedis)");

        testDirectConnection();
        testPoolConnection();

        System.out.println("=== Example completed successfully ===");
        System.out.println("✓ Same API as original Jedis, just different package name!");
    }

    /** Direct connection example with new package name */
    private static void testDirectConnection() {
        System.out.println("\n--- Direct Connection (New Package) ---");

        // Same usage as original Jedis, just different import
        Jedis jedis = new Jedis("localhost", 6379);

        try {
            System.out.println("PING: " + jedis.ping());

            jedis.set("new_package:example", "compatibility.clients.jedis works!");
            String value = jedis.get("new_package:example");
            System.out.println("Value: " + value);

        } finally {
            jedis.close();
            System.out.println("✓ Direct connection with new package works perfectly");
        }
    }

    /** Pool connection example with new package name */
    private static void testPoolConnection() {
        System.out.println("\n--- Pool Connection (New Package) ---");

        // Same usage as original JedisPool, just different import
        JedisPool pool = new JedisPool("localhost", 6379);

        try {
            try (Jedis jedis = pool.getResource()) {
                System.out.println("PING: " + jedis.ping());

                jedis.set("new_package:pool", "JedisPool with new package!");
                String value = jedis.get("new_package:pool");
                System.out.println("Pool Value: " + value);
            }

        } finally {
            pool.close();
            System.out.println("✓ Pool connection with new package works perfectly");
        }
    }
}
