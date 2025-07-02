/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import java.util.Arrays;

/**
 * Test runner that uses actual Jedis client to generate expected results
 * for comparison with GLIDE Jedis compatibility layer.
 * 
 * This class can be run separately with actual Jedis on the classpath
 * to generate expected behavior that can be compared with GLIDE results.
 */
public class ActualJedisTestRunner {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    public static void main(String[] args) {
        System.out.println("=== Actual Jedis Test Runner ===");
        System.out.println("This runner tests actual Jedis behavior for comparison");
        
        try {
            testBasicOperations();
            testPoolOperations();
            testPingOperations();
            testDataTypes();
            
            System.out.println("\n=== All Actual Jedis Tests Completed Successfully ===");
            
        } catch (Exception e) {
            System.err.println("Actual Jedis test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testBasicOperations() {
        System.out.println("\n--- Testing Basic Operations ---");
        
        Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT);
        
        try {
            // Test SET
            String setResult = jedis.set("actual_test:basic", "test_value");
            System.out.println("SET result: " + setResult);
            
            // Test GET
            String getResult = jedis.get("actual_test:basic");
            System.out.println("GET result: " + getResult);
            
            // Test GET non-existent
            String nonExistentResult = jedis.get("actual_test:nonexistent");
            System.out.println("GET non-existent result: " + nonExistentResult);
            
        } finally {
            jedis.close();
        }
    }

    private static void testPoolOperations() {
        System.out.println("\n--- Testing Pool Operations ---");
        
        JedisPool pool = new JedisPool(REDIS_HOST, REDIS_PORT);
        
        try {
            // Test pool resource
            try (Jedis pooledJedis = pool.getResource()) {
                String setResult = pooledJedis.set("actual_test:pool", "pool_value");
                System.out.println("Pool SET result: " + setResult);
                
                String getResult = pooledJedis.get("actual_test:pool");
                System.out.println("Pool GET result: " + getResult);
            }
            
            // Test another pool resource
            try (Jedis anotherPooledJedis = pool.getResource()) {
                String getResult = anotherPooledJedis.get("actual_test:pool");
                System.out.println("Pool GET (different connection) result: " + getResult);
            }
            
        } finally {
            pool.close();
        }
    }

    private static void testPingOperations() {
        System.out.println("\n--- Testing PING Operations ---");
        
        Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT);
        
        try {
            // Test basic PING
            String pingResult = jedis.ping();
            System.out.println("PING result: " + pingResult);
            
            // Test PING with message
            String message = "hello_actual_jedis";
            String pingMessageResult = jedis.ping(message);
            System.out.println("PING with message result: " + pingMessageResult);
            
        } finally {
            jedis.close();
        }
    }

    private static void testDataTypes() {
        System.out.println("\n--- Testing Data Types ---");
        
        Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT);
        
        try {
            // Test regular string
            jedis.set("actual_test:string", "regular_string");
            String stringResult = jedis.get("actual_test:string");
            System.out.println("String result: " + stringResult);
            
            // Test empty string
            jedis.set("actual_test:empty", "");
            String emptyResult = jedis.get("actual_test:empty");
            System.out.println("Empty string result: '" + emptyResult + "'");
            
            // Test special characters
            String specialValue = "special!@#$%^&*()_+-={}[]|\\:;\"'<>?,./";
            jedis.set("actual_test:special", specialValue);
            String specialResult = jedis.get("actual_test:special");
            System.out.println("Special chars result: " + specialResult);
            
        } finally {
            jedis.close();
        }
    }
}
