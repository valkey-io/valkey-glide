/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.REDIS_VERSION;
import static glide.TransactionTestUtilities.ConnectionManagementCommandsTransactionBuilder;
import static glide.TransactionTestUtilities.GenericCommandsTransactionBuilder;
import static glide.TransactionTestUtilities.HashCommandsTransactionBuilder;
import static glide.TransactionTestUtilities.HyperLogLogCommandsTransactionBuilder;
import static glide.TransactionTestUtilities.ListCommandsTransactionBuilder;
import static glide.TransactionTestUtilities.ServerManagementCommandsTransactionBuilder;
import static glide.TransactionTestUtilities.SetCommandsTransactionBuilder;
import static glide.TransactionTestUtilities.SortedSetCommandsTransactionBuilder;
import static glide.TransactionTestUtilities.StreamCommandsTransactionBuilder;
import static glide.TransactionTestUtilities.StringCommandsTransactionBuilder;
import static glide.TransactionTestUtilities.redisV7plusCommands;
import static glide.api.BaseClient.OK;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.TestConfiguration;
import glide.TransactionTestUtilities.TransactionBuilder;
import glide.api.RedisClusterClient;
import glide.api.models.ClusterTransaction;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(10) // seconds
public class ClusterTransactionTests {

    private static RedisClusterClient clusterClient = null;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        clusterClient =
                RedisClusterClient.CreateClient(
                                RedisClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(TestConfiguration.CLUSTER_PORTS[0]).build())
                                        .requestTimeout(5000)
                                        .build())
                        .get();
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        clusterClient.close();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        ClusterTransaction transaction = new ClusterTransaction().customCommand(new String[] {"info"});
        Object[] result = clusterClient.exec(transaction).get();
        assertTrue(((String) result[0]).contains("# Stats"));
    }

    @Test
    @SneakyThrows
    public void WATCH_transaction_failure_returns_null() {
        ClusterTransaction transaction = new ClusterTransaction();
        transaction.get("key");
        assertEquals(
                OK, clusterClient.customCommand(new String[] {"WATCH", "key"}).get().getSingleValue());
        assertEquals(OK, clusterClient.set("key", "foo").get());
        assertNull(clusterClient.exec(transaction).get());
    }

    @Test
    @SneakyThrows
    public void info_simple_route_test() {
        ClusterTransaction transaction = new ClusterTransaction().info().info();
        Object[] result = clusterClient.exec(transaction, RANDOM).get();

        assertTrue(((String) result[0]).contains("# Stats"));
        assertTrue(((String) result[1]).contains("# Stats"));
    }

    public static Stream<Arguments> getTransactionBuilders() {
        return Stream.of(
                Arguments.of("Generic Commands", GenericCommandsTransactionBuilder),
                Arguments.of("String Commands", StringCommandsTransactionBuilder),
                Arguments.of("Hash Commands", HashCommandsTransactionBuilder),
                Arguments.of("List Commands", ListCommandsTransactionBuilder),
                Arguments.of("Set Commands", SetCommandsTransactionBuilder),
                Arguments.of("Sorted Set Commands", SortedSetCommandsTransactionBuilder),
                Arguments.of("Server Management Commands", ServerManagementCommandsTransactionBuilder),
                Arguments.of("HyperLogLog Commands", HyperLogLogCommandsTransactionBuilder),
                Arguments.of("Stream Commands", StreamCommandsTransactionBuilder),
                Arguments.of(
                        "Connection Management Commands", ConnectionManagementCommandsTransactionBuilder));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("getTransactionBuilders")
    public void transactions_with_group_of_commands(String testName, TransactionBuilder builder) {
        ClusterTransaction transaction = new ClusterTransaction();
        Object[] expectedResult = builder.apply(transaction);

        Object[] results = clusterClient.exec(transaction, RANDOM).get();
        assertArrayEquals(expectedResult, results);
    }

    // Test commands supported by redis >= 7 only
    @SneakyThrows
    @Test
    public void test_cluster_transactions_redis_7() {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        ClusterTransaction transaction = new ClusterTransaction();
        Object[] expectedResult = redisV7plusCommands(transaction);

        Object[] results = clusterClient.exec(transaction).get();
        assertArrayEquals(expectedResult, results);
    }
}
