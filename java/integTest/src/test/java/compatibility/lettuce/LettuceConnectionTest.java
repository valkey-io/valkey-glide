/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.lettuce;

import static glide.TestConfiguration.STANDALONE_HOSTS;
import static org.junit.jupiter.api.Assertions.*;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.lettuce.core.api.sync.RedisStringCommands;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Lettuce compatibility integration test that validates GLIDE's Lettuce compatibility layer
 * functionality.
 *
 * <p>This test ensures that the GLIDE compatibility layer provides the expected Lettuce API and
 * behavior for connection and basic operations.
 */
public class LettuceConnectionTest {

    private static String valkeyHost;
    private static int valkeyPort;

    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;

    @BeforeAll
    static void setupClass() {
        String[] standaloneHosts = STANDALONE_HOSTS;

        if (standaloneHosts.length == 0 || standaloneHosts[0].trim().isEmpty()) {
            throw new IllegalStateException(
                    "Standalone server configuration not found in system properties. "
                            + "Please set 'test.server.standalone' system property with server address "
                            + "(e.g., -Dtest.server.standalone=localhost:6379)");
        }

        String firstHost = standaloneHosts[0].trim();
        String[] hostPort = firstHost.split(":");

        if (hostPort.length == 2) {
            try {
                valkeyHost = hostPort[0];
                valkeyPort = Integer.parseInt(hostPort[1]);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "Invalid port number in standalone server configuration: " + firstHost, e);
            }
        } else {
            throw new IllegalStateException(
                    "Invalid standalone server format: " + firstHost + ". Expected format: host:port");
        }
    }

    @BeforeEach
    void setup() {
        RedisURI uri = RedisURI.Builder.redis(valkeyHost, valkeyPort).build();
        client = RedisClient.create(uri);
        assertNotNull(client, "RedisClient should be created");
    }

    @AfterEach
    void teardown() {
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
        if (client != null && !client.isShutdown()) {
            client.shutdown();
        }
    }

    @Test
    public void testBasicConnection() {
        connection = client.connect();

        assertNotNull(connection, "Connection should not be null");
        assertTrue(connection.isOpen(), "Connection should be open");
    }

    @Test
    public void testSyncCommands() {
        connection = client.connect();
        RedisStringCommands<String, String> sync = connection.sync();

        assertNotNull(sync, "Sync commands should not be null");
    }

    @Test
    public void testAsyncCommands() {
        connection = client.connect();
        RedisStringAsyncCommands<String, String> async = connection.async();

        assertNotNull(async, "Async commands should not be null");
    }

    @Test
    public void testAsyncSetAndGet() throws Exception {
        connection = client.connect();
        RedisStringAsyncCommands<String, String> async = connection.async();

        String key = "lettuce-async-key-" + UUID.randomUUID();
        String value = "async-value";

        RedisFuture<String> setFuture = async.set(key, value);
        assertEquals("OK", setFuture.get(5, TimeUnit.SECONDS), "Async SET should return OK");

        RedisFuture<String> getFuture = async.get(key);
        assertEquals(value, getFuture.get(5, TimeUnit.SECONDS), "Async GET should return value");

        // Cleanup
        async.set(key, "").get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testSetAndGet() {
        connection = client.connect();
        RedisStringCommands<String, String> sync = connection.sync();

        String key = "lettuce-test-key-" + UUID.randomUUID();
        String value = "test-value";

        String setResult = sync.set(key, value);
        assertEquals("OK", setResult, "SET should return OK");

        String getResult = sync.get(key);
        assertEquals(value, getResult, "GET should return the set value");

        // Cleanup
        sync.set(key, "");
    }

    @Test
    public void testMultipleOperations() {
        connection = client.connect();
        RedisStringCommands<String, String> sync = connection.sync();

        String key1 = "lettuce-test-key1-" + UUID.randomUUID();
        String key2 = "lettuce-test-key2-" + UUID.randomUUID();
        String value1 = "value1";
        String value2 = "value2";

        sync.set(key1, value1);
        sync.set(key2, value2);

        assertEquals(value1, sync.get(key1));
        assertEquals(value2, sync.get(key2));

        // Cleanup
        sync.set(key1, "");
        sync.set(key2, "");
    }

    @Test
    public void testGetNonExistentKey() {
        connection = client.connect();
        RedisStringCommands<String, String> sync = connection.sync();

        String nonExistentKey = "non-existent-key-" + UUID.randomUUID();
        String result = sync.get(nonExistentKey);

        assertNull(result, "GET on non-existent key should return null");
    }

    @Test
    public void testConnectionClose() {
        connection = client.connect();

        assertTrue(connection.isOpen(), "Connection should be open initially");

        connection.close();

        assertFalse(connection.isOpen(), "Connection should be closed after close()");
    }

    @Test
    public void testCreateFromURIString() {
        RedisClient stringClient = RedisClient.create("redis://" + valkeyHost + ":" + valkeyPort);
        assertNotNull(stringClient, "Client created from URI string should not be null");

        StatefulRedisConnection<String, String> conn = stringClient.connect();
        assertNotNull(conn, "Connection should be created");
        assertTrue(conn.isOpen(), "Connection should be open");

        conn.close();
        stringClient.shutdown();
    }

    @Test
    public void testBuilderWithAllOptions() {
        RedisURI uri = RedisURI.builder().withHost(valkeyHost).withPort(valkeyPort).build();

        RedisClient testClient = RedisClient.create(uri);
        StatefulRedisConnection<String, String> conn = testClient.connect();

        assertNotNull(conn);
        assertTrue(conn.isOpen());

        conn.close();
        testClient.shutdown();
    }

    @Test
    public void testInvalidConnectionFails() {
        RedisURI badUri = RedisURI.Builder.redis("invalid-host", 99999).build();
        RedisClient badClient = RedisClient.create(badUri);

        assertThrows(
                RedisConnectionException.class,
                () -> {
                    badClient.connect();
                },
                "Connection to invalid host should throw RedisConnectionException");

        badClient.shutdown();
    }
}
