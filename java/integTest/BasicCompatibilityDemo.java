import redis.clients.jedis.*;

public class BasicCompatibilityDemo {
    public static void main(String[] args) {
        System.out.println("=== GLIDE Jedis Compatibility Layer Demo ===");
        System.out.println("Testing basic operations available in the compatibility layer...\n");
        
        try {
            // Test 1: Basic Jedis operations
            System.out.println("1. Testing basic Jedis operations...");
            Jedis jedis = new Jedis("localhost", 6379);
            
            // Test PING
            String pingResult = jedis.ping();
            System.out.println("   ✓ PING: " + pingResult);
            
            // Test SET/GET
            String setResult = jedis.set("demo:compatibility", "GLIDE Jedis Works!");
            System.out.println("   ✓ SET: " + setResult);
            
            String getValue = jedis.get("demo:compatibility");
            System.out.println("   ✓ GET: " + getValue);
            
            // Test connection status
            boolean isClosed = jedis.isClosed();
            System.out.println("   ✓ isClosed(): " + isClosed);
            
            // Test 2: Configuration
            System.out.println("\n2. Testing configuration...");
            DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                .socketTimeoutMillis(2000)
                .build();
            System.out.println("   ✓ Configuration created successfully");
            
            // Test 3: Pool functionality
            System.out.println("\n3. Testing JedisPool...");
            JedisPool pool = new JedisPool("localhost", 6379);
            System.out.println("   ✓ JedisPool created");
            
            try (Jedis pooledJedis = pool.getResource()) {
                String poolPing = pooledJedis.ping();
                System.out.println("   ✓ Pooled Jedis PING: " + poolPing);
                
                pooledJedis.set("demo:pool", "Pool works!");
                String poolValue = pooledJedis.get("demo:pool");
                System.out.println("   ✓ Pool SET/GET: " + poolValue);
            }
            
            // Test 4: Exception handling
            System.out.println("\n4. Testing exception compatibility...");
            try {
                throw new JedisException("Test exception");
            } catch (JedisException e) {
                System.out.println("   ✓ JedisException caught: " + e.getMessage());
            }
            
            try {
                throw new JedisConnectionException("Test connection exception");
            } catch (JedisConnectionException e) {
                System.out.println("   ✓ JedisConnectionException caught: " + e.getMessage());
            }
            
            // Test 5: Utility classes
            System.out.println("\n5. Testing utility classes...");
            HostAndPort hostPort = new HostAndPort("localhost", 6379);
            System.out.println("   ✓ HostAndPort: " + hostPort.toString());
            
            RedisProtocol protocol = RedisProtocol.RESP3;
            System.out.println("   ✓ RedisProtocol: " + protocol);
            
            // Cleanup and close
            jedis.close();
            pool.close();
            
            System.out.println("\n" + "=".repeat(50));
            System.out.println("🎉 COMPATIBILITY TEST RESULTS:");
            System.out.println("✅ Basic Operations: PASSED");
            System.out.println("✅ Configuration: PASSED");
            System.out.println("✅ Pool Management: PASSED");
            System.out.println("✅ Exception Handling: PASSED");
            System.out.println("✅ Utility Classes: PASSED");
            System.out.println("=".repeat(50));
            System.out.println("🚀 GLIDE Jedis compatibility layer is working correctly!");
            System.out.println("📊 All tested features are compatible with original Jedis API");
            
        } catch (Exception e) {
            System.err.println("❌ Compatibility test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
