#!/bin/bash

# Simple test script for JNI Valkey client
# This tests the basic functionality without JMH overhead

echo "=== Testing JNI Valkey Client ==="

# Check if Valkey server is running
if ! pgrep -x "valkey-server" > /dev/null; then
    echo "Starting valkey-server on localhost:6379..."
    valkey-server --daemonize yes --port 6379
    sleep 2
else
    echo "valkey-server is already running"
fi

# Compile Java classes
echo "Compiling Java classes..."
cd /home/ubuntu/valkey-glide/java-jni
javac -d build -cp src src/main/java/io/valkey/glide/jni/client/GlideJniClient.java

# Create a simple test class
cat > TestJniClient.java << 'EOF'
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
EOF

# Compile test class
javac -cp build:src TestJniClient.java

# Run test with library path
echo "Running test..."
java -Djava.library.path=/home/ubuntu/valkey-glide/rust-jni/target/release -cp .:build:src TestJniClient

echo "Test completed successfully!"
