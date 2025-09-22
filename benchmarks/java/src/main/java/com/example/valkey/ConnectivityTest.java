package com.example.valkey;

public class ConnectivityTest {
    public static void main(String[] args) {
        TestConfiguration config = new TestConfiguration();
        
        // Parse host and port from command line if provided
        for (String arg : args) {
            if (arg.startsWith("--host=")) {
                config.setRedisHost(arg.substring(7));
            } else if (arg.startsWith("--port=")) {
                config.setRedisPort(Integer.parseInt(arg.substring(7)));
            } else if (arg.equals("--cluster")) {
                config.setClusterMode(true);
            } else if (arg.equals("--tls")) {
                config.setTlsEnabled(true);
            }
        }
        
        System.out.println("Testing connectivity to Redis server...");
        System.out.println("Host: " + config.getRedisHost());
        System.out.println("Port: " + config.getRedisPort());
        System.out.println("Cluster Mode: " + (config.isClusterMode() ? "enabled" : "disabled"));
        System.out.println("TLS: " + (config.isTlsEnabled() ? "enabled" : "disabled"));
        System.out.println();
        
        // Test Jedis
        System.out.println("Testing Jedis connection:");
        testClient(new JedisClient(config));
        
        System.out.println();
        
        // Test Valkey-Glide
        System.out.println("Testing Valkey-Glide connection:");
        testClient(new ValkeyGlideClient(config));
    }
    
    private static void testClient(RedisClient client) {
        try {
            System.out.println("  Connecting with " + client.getClientName() + "...");
            client.connect();
            System.out.println("  ✓ Connected successfully");
            
            System.out.println("  Testing PING...");
            boolean pingResult = client.ping();
            if (pingResult) {
                System.out.println("  ✓ PING successful");
            } else {
                System.out.println("  ✗ PING failed");
            }
            
            System.out.println("  Testing SET operation...");
            boolean setResult = client.set("test_key", "test_value");
            if (setResult) {
                System.out.println("  ✓ SET successful");
            } else {
                System.out.println("  ✗ SET failed");
            }
            
            System.out.println("  Testing GET operation...");
            String getValue = client.get("test_key");
            if ("test_value".equals(getValue)) {
                System.out.println("  ✓ GET successful (value: " + getValue + ")");
            } else {
                System.out.println("  ✗ GET failed or unexpected value: " + getValue);
            }
            
            client.close();
            System.out.println("  ✓ Connection closed");
            
        } catch (Exception e) {
            System.out.println("  ✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
