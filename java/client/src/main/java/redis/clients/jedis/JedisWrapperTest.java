/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * Test class to verify the Jedis wrapper functionality. This class contains the exact code examples
 * you want to support.
 */
public class JedisWrapperTest {

    public static void main(String[] args) {
        testSimpleConnection();
        testPoolConnection();
    }

    /** Test simple connection example. */
    public static void testSimpleConnection() {
        System.out.println("Testing simple connection...");

        try {
            // Simple connection
            Jedis jedis = new Jedis("localhost", 6379);
            jedis.set("key", "value");
            System.out.println("Simple connection: SET operation successful");

            String value = jedis.get("key");
            System.out.println("Simple connection: GET result = " + value);

            jedis.close();
            System.out.println("Simple connection: Connection closed successfully");
        } catch (Exception e) {
            System.err.println("Simple connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Test pool connection example. */
    public static void testPoolConnection() {
        System.out.println("\nTesting pool connection...");

        JedisPool pool = null;
        try {
            // Pool connection
            pool = new JedisPool("localhost", 6379);

            try (Jedis jedis = pool.getResource()) {
                jedis.set("key", "value");
                System.out.println("Pool connection: SET operation successful");

                String value = jedis.get("key");
                System.out.println("Pool connection: GET result = " + value);
            }

            System.out.println("Pool connection: Resource returned to pool successfully");
        } catch (Exception e) {
            System.err.println("Pool connection failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (pool != null) {
                pool.close();
                System.out.println("Pool connection: Pool closed successfully");
            }
        }
    }
}
