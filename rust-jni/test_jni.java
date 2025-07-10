import java.util.Arrays;
import java.util.List;

public class test_jni {
    static {
        // Load the JNI library
        System.loadLibrary("glidejni");
    }
    
    // Native method declarations matching our Rust implementation
    private static native long createClient(
        String[] addresses,
        int databaseId,
        String username,
        String password,
        boolean useTls,
        boolean clusterMode,
        int requestTimeoutMs
    );
    
    private static native void closeClient(long clientPtr);
    private static native String get(long clientPtr, String key);
    private static native boolean set(long clientPtr, String key, String value);
    private static native String ping(long clientPtr);
    
    public static void main(String[] args) {
        System.out.println("Testing JNI client integration...");
        
        try {
            // Create client
            String[] addresses = {"127.0.0.1:6379"};
            long clientPtr = createClient(addresses, -1, null, null, false, false, 250);
            
            if (clientPtr == 0) {
                System.err.println("Failed to create client");
                System.exit(1);
            }
            
            System.out.println("✅ Client created successfully");
            
            // Test PING
            String pong = ping(clientPtr);
            System.out.println("PING response: " + pong);
            
            if (!"PONG".equals(pong)) {
                System.err.println("❌ PING failed - expected PONG, got: " + pong);
                System.exit(1);
            }
            
            System.out.println("✅ PING successful");
            
            // Test SET
            boolean setResult = set(clientPtr, "jni_test_key", "jni_test_value");
            System.out.println("SET result: " + setResult);
            
            if (!setResult) {
                System.err.println("❌ SET failed");
                System.exit(1);
            }
            
            System.out.println("✅ SET successful");
            
            // Test GET
            String getValue = get(clientPtr, "jni_test_key");
            System.out.println("GET result: " + getValue);
            
            if (!"jni_test_value".equals(getValue)) {
                System.err.println("❌ GET failed - expected 'jni_test_value', got: " + getValue);
                System.exit(1);
            }
            
            System.out.println("✅ GET successful");
            
            // Test performance with multiple operations
            System.out.println("\nPerformance test - 1000 operations...");
            long startTime = System.nanoTime();
            
            for (int i = 0; i < 1000; i++) {
                String key = "perf_key_" + i;
                String value = "perf_value_" + i;
                set(clientPtr, key, value);
                String result = get(clientPtr, key);
                if (!value.equals(result)) {
                    System.err.println("❌ Performance test failed at iteration " + i);
                    System.exit(1);
                }
            }
            
            long endTime = System.nanoTime();
            double durationMs = (endTime - startTime) / 1_000_000.0;
            double opsPerSec = 2000.0 / (durationMs / 1000.0); // 2000 ops (1000 SET + 1000 GET)
            
            System.out.printf("✅ Performance test completed: %.2f ms, %.0f ops/sec%n", durationMs, opsPerSec);
            
            // Cleanup
            closeClient(clientPtr);
            System.out.println("✅ Client closed successfully");
            
            System.out.println("\n🎉 All tests passed! JNI client is working correctly.");
            
        } catch (Exception e) {
            System.err.println("❌ Test failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}