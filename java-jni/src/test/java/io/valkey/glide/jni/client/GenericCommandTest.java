package io.valkey.glide.jni.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generic command execution system.
 * These tests verify that the new Command-based API works correctly
 * for various server operations.
 */
public class GenericCommandTest {
    
    private GlideJniClient client;

    @BeforeEach
    void setUp() {
        try {
            // Try to create a client - if this fails, server is not available
            client = new GlideJniClient("localhost", 6379);
            
            // Test basic connectivity with a PING
            String result = client.ping().get();
            Assumptions.assumeTrue("PONG".equals(result), "Server not available for testing");
            
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Server not available for testing: " + e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        if (client != null && !client.isClosed()) {
            client.close();
        }
    }

    @Test
    void testGenericGetCommand() throws ExecutionException, InterruptedException {
        // First set a value using specific method
        client.set("test_key", "test_value").get();
        
        // Then get it using generic command system
        Command getCommand = Command.builder("GET").arg("test_key").build();
        CompletableFuture<Object> result = client.executeCommand(getCommand);
        
        Object value = result.get();
        assertNotNull(value);
        assertEquals("test_value", value);
    }

    @Test
    void testGenericSetCommand() throws ExecutionException, InterruptedException {
        // Use generic command system to set a value
        Command setCommand = Command.builder("SET")
            .arg("generic_key")
            .arg("generic_value")
            .build();
        
        CompletableFuture<Object> setResult = client.executeCommand(setCommand);
        Object setResponse = setResult.get();
        assertEquals("OK", setResponse);
        
        // Verify with specific GET method
        String value = client.get("generic_key").get();
        assertEquals("generic_value", value);
    }

    @Test
    void testGenericPingCommand() throws ExecutionException, InterruptedException {
        Command pingCommand = Command.builder("PING").build();
        CompletableFuture<Object> result = client.executeCommand(pingCommand);
        
        Object response = result.get();
        assertEquals("PONG", response);
    }

    @Test
    void testGenericCommandWithMultipleArgs() throws ExecutionException, InterruptedException {
        // Test MSET command (multiple arguments)
        Command msetCommand = Command.builder("MSET")
            .arg("key1").arg("value1")
            .arg("key2").arg("value2")
            .arg("key3").arg("value3")
            .build();
        
        CompletableFuture<Object> result = client.executeCommand(msetCommand);
        Object response = result.get();
        assertEquals("OK", response);
        
        // Verify the values were set correctly
        assertEquals("value1", client.get("key1").get());
        assertEquals("value2", client.get("key2").get());
        assertEquals("value3", client.get("key3").get());
    }

    @Test
    void testGenericCommandWithTypedResult() throws ExecutionException, InterruptedException {
        // Set a value first
        client.set("typed_key", "typed_value").get();
        
        // Use typed command execution
        Command getCommand = Command.builder("GET").arg("typed_key").build();
        CompletableFuture<String> result = client.executeCommand(getCommand, String.class);
        
        String value = result.get();
        assertEquals("typed_value", value);
    }

    @Test
    void testGenericCommandWithNumbers() throws ExecutionException, InterruptedException {
        // Test INCR command (returns integer)
        client.set("counter", "10").get();
        
        Command incrCommand = Command.builder("INCR").arg("counter").build();
        CompletableFuture<Object> result = client.executeCommand(incrCommand);
        
        Object response = result.get();
        assertNotNull(response);
        // The response should be a Long object
        assertTrue(response instanceof Long);
        assertEquals(11L, response);
    }

    @Test
    void testGenericCommandWithBinaryData() throws ExecutionException, InterruptedException {
        // Test with binary data
        byte[] binaryKey = "binary_key".getBytes();
        byte[] binaryValue = {1, 2, 3, 4, 5, (byte)0xFF, (byte)0xFE};
        
        Command setCommand = Command.builder("SET")
            .arg(binaryKey)
            .arg(binaryValue)
            .build();
        
        CompletableFuture<Object> setResult = client.executeCommand(setCommand);
        assertEquals("OK", setResult.get());
        
        // Retrieve with binary key
        Command getCommand = Command.builder("GET").arg(binaryKey).build();
        CompletableFuture<Object> getResult = client.executeCommand(getCommand);
        
        Object value = getResult.get();
        assertNotNull(value);
        
        // For binary data, the result should be a byte array
        assertTrue(value instanceof byte[], "Expected byte array for binary data, got: " + value.getClass());
        
        byte[] retrievedBytes = (byte[]) value;
        assertArrayEquals(binaryValue, retrievedBytes);
    }

    @Test
    void testGenericCommandWithListResults() throws ExecutionException, InterruptedException {
        // Test MGET command (returns array)
        client.set("list_key1", "list_value1").get();
        client.set("list_key2", "list_value2").get();
        
        Command mgetCommand = Command.builder("MGET")
            .arg("list_key1")
            .arg("list_key2")
            .arg("nonexistent_key")
            .build();
        
        CompletableFuture<Object> result = client.executeCommand(mgetCommand);
        Object response = result.get();
        
        assertNotNull(response);
        assertTrue(response instanceof Object[]);
        
        Object[] array = (Object[]) response;
        assertEquals(3, array.length);
        assertEquals("list_value1", array[0]);
        assertEquals("list_value2", array[1]);
        assertNull(array[2]); // nonexistent key should return null
    }

    @Test
    void testCommandBuilder() {
        // Test the command builder functionality
        Command command = Command.builder("TESTCMD")
            .arg("string_arg")
            .arg(42)
            .arg(3.14)
            .arg(999L)
            .args("multi", "args")
            .build();
        
        assertEquals("TESTCMD", command.getCommand());
        assertEquals(6, command.getArgumentCount());
        
        // Test toString for debugging
        String commandStr = command.toString();
        assertTrue(commandStr.contains("TESTCMD"));
        assertTrue(commandStr.contains("string_arg"));
        assertTrue(commandStr.contains("42"));
    }

    @Test
    void testInvalidCommand() {
        assertThrows(IllegalArgumentException.class, () -> {
            Command.builder(null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Command.builder("");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Command.builder("   ");
        });
    }

    @Test
    void testCommandWithNullArguments() throws ExecutionException, InterruptedException {
        // Test that null arguments are handled gracefully
        Command command = Command.builder("GET")
            .arg((String) null)  // Null argument should be ignored
            .arg("test_key")
            .build();
        
        // Should only have one argument (test_key), null should be ignored
        assertEquals(1, command.getArgumentCount());
    }
}