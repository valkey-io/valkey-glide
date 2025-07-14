package glide.api;

import io.valkey.glide.core.client.GlideClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SimpleStandaloneClient.
 * These tests verify the new protobuf-free client architecture.
 */
public class SimpleStandaloneClientTest {

    private SimpleStandaloneClient client;

    @BeforeEach
    void setUp() {
        // Create a client connection to localhost:6379 (default Redis/Valkey instance)
        client = SimpleStandaloneClient.create("127.0.0.1", 6379);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testPing() throws Exception {
        CompletableFuture<String> result = client.ping();
        assertEquals("PONG", result.get());
    }

    @Test
    void testSetAndGet() throws Exception {
        String key = "test:simple:key";
        String value = "test:value";

        // Set the value
        CompletableFuture<String> setResult = client.set(key, value);
        assertEquals("OK", setResult.get());

        // Get the value
        CompletableFuture<String> getResult = client.get(key);
        assertEquals(value, getResult.get());
    }

    @Test
    void testSelectDatabase() throws Exception {
        // Select database 1
        CompletableFuture<String> selectResult = client.select(1);
        assertEquals("OK", selectResult.get());

        // Set a value in database 1
        String key = "test:db1:key";
        String value = "db1:value";
        client.set(key, value).get();

        // Verify we can get it
        String retrievedValue = client.get(key).get();
        assertEquals(value, retrievedValue);

        // Switch back to database 0
        client.select(0).get();

        // The key should not exist in database 0
        String resultInDb0 = client.get(key).get();
        assertNull(resultInDb0);
    }

    @Test
    void testDbSize() throws Exception {
        // Clear database and check size
        client.flushdb().get();
        Long emptySize = client.dbsize().get();
        assertEquals(0L, emptySize);

        // Add some keys
        client.set("key1", "value1").get();
        client.set("key2", "value2").get();
        client.set("key3", "value3").get();

        // Check size increased
        Long populatedSize = client.dbsize().get();
        assertEquals(3L, populatedSize);
    }

    @Test
    void testFlushDb() throws Exception {
        // Add some data
        client.set("key1", "value1").get();
        client.set("key2", "value2").get();

        // Verify data exists
        Long sizeBefore = client.dbsize().get();
        assertTrue(sizeBefore >= 2);

        // Flush the database
        String flushResult = client.flushdb().get();
        assertEquals("OK", flushResult);

        // Verify database is empty
        Long sizeAfter = client.dbsize().get();
        assertEquals(0L, sizeAfter);
    }

    @Test
    void testMultipleCommands() throws Exception {
        // Test executing multiple operations in sequence
        String key1 = "test:multi:key1";
        String key2 = "test:multi:key2";
        String value1 = "multi:value1";
        String value2 = "multi:value2";

        // Set multiple values
        client.set(key1, value1).get();
        client.set(key2, value2).get();

        // Get multiple values
        String result1 = client.get(key1).get();
        String result2 = client.get(key2).get();

        assertEquals(value1, result1);
        assertEquals(value2, result2);

        // Test ping still works
        assertEquals("PONG", client.ping().get());
    }
}
