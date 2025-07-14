/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import io.valkey.glide.jni.commands.CommandType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Simple integration test example for the new non-protobuf client.
 * This demonstrates the basic functionality without any protobuf dependencies.
 */
public class SimpleClientExample {
    
    public static void main(String[] args) {
        System.out.println("Starting Simple Client Example (No Protobuf)");
        
        try {
            // Create a simple standalone client
            SimpleStandaloneClient client = SimpleStandaloneClient.create("localhost", 6379);
            
            System.out.println("✓ Created client successfully");
            
            // Test basic operations
            testBasicOperations(client).get();
            testHashOperations(client).get();
            testMultiOperations(client).get();
            
            // Close the client
            client.close();
            System.out.println("✓ Client closed successfully");
            
            System.out.println("\n🎉 All tests passed! Simple client working without protobuf.");
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static CompletableFuture<Void> testBasicOperations(SimpleStandaloneClient client) {
        System.out.println("\n--- Testing Basic Operations ---");
        
        return client.ping()
            .thenCompose(response -> {
                System.out.println("PING: " + response);
                return client.set("test:key", "test:value");
            })
            .thenCompose(response -> {
                System.out.println("SET: " + response);
                return client.get("test:key");
            })
            .thenCompose(value -> {
                System.out.println("GET: " + value);
                return client.exists("test:key");
            })
            .thenCompose(count -> {
                System.out.println("EXISTS: " + count);
                return client.del("test:key");
            })
            .thenApply(deleted -> {
                System.out.println("DEL: " + deleted);
                System.out.println("✓ Basic operations completed");
                return null;
            });
    }
    
    private static CompletableFuture<Void> testHashOperations(SimpleStandaloneClient client) {
        System.out.println("\n--- Testing Hash Operations ---");
        
        return client.hset("test:hash", "field1", "value1")
            .thenCompose(result -> {
                System.out.println("HSET: " + result);
                return client.hset("test:hash", "field2", "value2");
            })
            .thenCompose(result -> {
                System.out.println("HSET: " + result);
                return client.hget("test:hash", "field1");
            })
            .thenCompose(value -> {
                System.out.println("HGET: " + value);
                return client.hgetall("test:hash");
            })
            .thenCompose(map -> {
                System.out.println("HGETALL: " + map);
                return client.del("test:hash");
            })
            .thenApply(deleted -> {
                System.out.println("DEL: " + deleted);
                System.out.println("✓ Hash operations completed");
                return null;
            });
    }
    
    private static CompletableFuture<Void> testMultiOperations(SimpleStandaloneClient client) {
        System.out.println("\n--- Testing Multi Operations ---");
        
        Map<String, String> keyValues = new HashMap<>();
        keyValues.put("test:key1", "value1");
        keyValues.put("test:key2", "value2");
        keyValues.put("test:key3", "value3");
        
        return client.mset(keyValues)
            .thenCompose(response -> {
                System.out.println("MSET: " + response);
                return client.mget("test:key1", "test:key2", "test:key3");
            })
            .thenCompose(values -> {
                System.out.println("MGET: " + String.join(", ", values));
                return client.del("test:key1", "test:key2", "test:key3");
            })
            .thenApply(deleted -> {
                System.out.println("DEL: " + deleted);
                System.out.println("✓ Multi operations completed");
                return null;
            });
    }
    
    /**
     * Display available command types - showing we have all the protobuf-free commands
     */
    public static void displayAvailableCommands() {
        System.out.println("\n--- Available Commands (No Protobuf) ---");
        CommandType[] commands = CommandType.values();
        System.out.println("Total commands available: " + commands.length);
        
        // Show a sampling of commands
        int count = 0;
        for (CommandType cmd : commands) {
            if (count < 20) { // Show first 20 commands
                System.out.println("  " + cmd.getCommand());
                count++;
            }
        }
        if (commands.length > 20) {
            System.out.println("  ... and " + (commands.length - 20) + " more commands");
        }
    }
}
