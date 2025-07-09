import io.valkey.glide.jni.client.GlideJniClient;
import java.util.concurrent.CompletableFuture;

public class TestJniClient {
    public static void main(String[] args) {
        try {
            System.out.println("Testing JNI client...");

            // Create client with host and port
            GlideJniClient client = new GlideJniClient("localhost", 6379);

            // Test PING
            CompletableFuture<String> pingFuture = client.ping();
            String pingResult = pingFuture.get();
            System.out.println("PING result: " + pingResult);

            // Test SET
            CompletableFuture<String> setFuture = client.set("test_key", "test_value");
            String setResult = setFuture.get();
            System.out.println("SET result: " + setResult);

            // Test GET
            CompletableFuture<String> getFuture = client.get("test_key");
            String getValue = getFuture.get();
            System.out.println("GET result: " + getValue);

            // Close client
            client.close();

            System.out.println("All tests passed!");

        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
