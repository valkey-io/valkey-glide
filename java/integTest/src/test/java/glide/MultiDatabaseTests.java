/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.exceptions.ConfigurationError;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(10) // seconds
public class MultiDatabaseTests {

    /** Helper method to check if server version is at least 9.0 */
    private static boolean isServerVersionAtLeast9_0() {
        return SERVER_VERSION.isGreaterThanOrEqualTo("9.0.0");
    }

    /** Helper method to provide client configurations for parameterized tests */
    private static Stream<Arguments> getClients() {
        return Stream.of(Arguments.of("standalone", false), Arguments.of("cluster", true));
    }

    /** Helper method to provide database IDs for testing */
    private static Stream<Arguments> getDatabaseIds() {
        return Stream.of(
                Arguments.of(0), Arguments.of(1), Arguments.of(2), Arguments.of(5), Arguments.of(10));
    }

    @ParameterizedTest
    @MethodSource("getClients")
    @SneakyThrows
    public void testClientCreationWithDefaultDatabase(String clientType, boolean isCluster) {
        // Test that clients can be created without specifying database_id (defaults to DB 0)
        BaseClient client;
        if (isCluster) {
            client = GlideClusterClient.createClient(commonClusterClientConfig().build()).get();
        } else {
            client = GlideClient.createClient(commonClientConfig().build()).get();
        }

        try {
            // Verify we can perform operations (should be in database 0)
            assertEquals(OK, client.set("test_key", "test_value").get());
            assertEquals("test_value", client.get("test_key").get());
            assertEquals(1L, client.del(new String[] {"test_key"}).get());
        } finally {
            client.close();
        }
    }

    @ParameterizedTest
    @MethodSource("getClients")
    @SneakyThrows
    public void testClientCreationWithExplicitDatabase0(String clientType, boolean isCluster) {
        // Test that clients can be created with explicit database_id = 0
        BaseClient client;
        if (isCluster) {
            client =
                    GlideClusterClient.createClient(commonClusterClientConfig().databaseId(0).build()).get();
        } else {
            client = GlideClient.createClient(commonClientConfig().databaseId(0).build()).get();
        }

        try {
            // Verify we can perform operations in database 0
            assertEquals(OK, client.set("test_key_db0", "test_value_db0").get());
            assertEquals("test_value_db0", client.get("test_key_db0").get());
            assertEquals(1L, client.del(new String[] {"test_key_db0"}).get());
        } finally {
            client.close();
        }
    }

    @Test
    @SneakyThrows
    public void testStandaloneClientWithNonZeroDatabase() {
        // Test standalone client with non-zero database (should work regardless of server version)
        GlideClient client = GlideClient.createClient(commonClientConfig().databaseId(1).build()).get();

        try {
            // Verify we can perform operations in database 1
            assertEquals(OK, client.set("test_key_db1", "test_value_db1").get());
            assertEquals("test_value_db1", client.get("test_key_db1").get());
            assertEquals(1L, client.del(new String[] {"test_key_db1"}).get());
        } finally {
            client.close();
        }
    }

    @ParameterizedTest
    @MethodSource("getDatabaseIds")
    @EnabledIf("isServerVersionAtLeast9_0")
    @SneakyThrows
    public void testClusterClientWithNonZeroDatabase(int databaseId) {
        // Test cluster client with non-zero database (requires Valkey 9.0+)
        assumeTrue(isServerVersionAtLeast9_0(), "Multi-DB cluster mode requires Valkey 9.0+");

        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().databaseId(databaseId).build())
                        .get();

        try {
            // Verify we can perform operations in the specified database
            String testKey = "test_key_db" + databaseId;
            String testValue = "test_value_db" + databaseId;

            assertEquals(OK, client.set(testKey, testValue).get());
            assertEquals(testValue, client.get(testKey).get());
            assertEquals(1L, client.del(new String[] {testKey}).get());
        } finally {
            client.close();
        }
    }

    @Test
    @EnabledIf("isServerVersionAtLeast9_0")
    @SneakyThrows
    public void testClusterDatabaseIsolation() {
        // Test that different databases are isolated in cluster mode
        assumeTrue(isServerVersionAtLeast9_0(), "Multi-DB cluster mode requires Valkey 9.0+");

        GlideClusterClient client1 =
                GlideClusterClient.createClient(commonClusterClientConfig().databaseId(1).build()).get();

        GlideClusterClient client2 =
                GlideClusterClient.createClient(commonClusterClientConfig().databaseId(2).build()).get();

        try {
            String key = "isolation_test_key";
            String value1 = "value_in_db1";
            String value2 = "value_in_db2";

            // Set different values in different databases
            assertEquals(OK, client1.set(key, value1).get());
            assertEquals(OK, client2.set(key, value2).get());

            // Verify values are isolated
            assertEquals(value1, client1.get(key).get());
            assertEquals(value2, client2.get(key).get());

            // Clean up
            assertEquals(1L, client1.del(new String[] {key}).get());
            assertEquals(1L, client2.del(new String[] {key}).get());
        } finally {
            client1.close();
            client2.close();
        }
    }

    @Test
    @SneakyThrows
    public void testStandaloneDatabaseIsolation() {
        // Test that different databases are isolated in standalone mode
        GlideClient client1 =
                GlideClient.createClient(commonClientConfig().databaseId(1).build()).get();

        GlideClient client2 =
                GlideClient.createClient(commonClientConfig().databaseId(2).build()).get();

        try {
            String key = "isolation_test_key";
            String value1 = "value_in_db1";
            String value2 = "value_in_db2";

            // Set different values in different databases
            assertEquals(OK, client1.set(key, value1).get());
            assertEquals(OK, client2.set(key, value2).get());

            // Verify values are isolated
            assertEquals(value1, client1.get(key).get());
            assertEquals(value2, client2.get(key).get());

            // Clean up
            assertEquals(1L, client1.del(new String[] {key}).get());
            assertEquals(1L, client2.del(new String[] {key}).get());
        } finally {
            client1.close();
            client2.close();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -5, -10})
    @SneakyThrows
    public void testInvalidNegativeDatabaseId(int invalidDatabaseId) {
        // Test that negative database IDs result in configuration errors
        ConfigurationError exception =
                assertThrows(
                        ConfigurationError.class,
                        () -> {
                            GlideClient.createClient(commonClientConfig().databaseId(invalidDatabaseId).build())
                                    .get();
                        });

        // Should contain information about the invalid database ID
        assertTrue(exception.getMessage().toLowerCase().contains("non-negative"));
        assertTrue(exception.getMessage().contains(String.valueOf(invalidDatabaseId)));
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 20, 100})
    @SneakyThrows
    public void testInvalidLargeDatabaseId(int invalidDatabaseId) {
        // Test that database IDs beyond reasonable range result in configuration errors
        ConfigurationError exception =
                assertThrows(
                        ConfigurationError.class,
                        () -> {
                            GlideClient.createClient(commonClientConfig().databaseId(invalidDatabaseId).build())
                                    .get();
                        });

        // Should contain information about the reasonable range
        assertTrue(exception.getMessage().toLowerCase().contains("reasonable range"));
        assertTrue(exception.getMessage().contains(String.valueOf(invalidDatabaseId)));
    }

    @Test
    @SneakyThrows
    public void testClusterClientWithNonZeroDatabaseOnOlderServer() {
        // Test that cluster client with non-zero database fails gracefully on older servers
        if (isServerVersionAtLeast9_0()) {
            // Skip this test on Valkey 9.0+ as it should work
            assumeTrue(false, "Skipping test on Valkey 9.0+ where multi-DB cluster is supported");
        }

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            GlideClusterClient.createClient(commonClusterClientConfig().databaseId(1).build())
                                    .get();
                        });

        // Should get an error about SELECT not being allowed in cluster mode
        assertTrue(
                exception.getMessage().toLowerCase().contains("select")
                        || exception.getMessage().toLowerCase().contains("cluster")
                        || exception.getMessage().toLowerCase().contains("database"));
    }
}
