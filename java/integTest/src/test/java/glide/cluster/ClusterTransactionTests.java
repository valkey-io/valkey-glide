/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.REDIS_VERSION;
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
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
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

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("glide.TransactionTestUtilities#getCommonTransactionBuilders")
    public void transactions_with_group_of_commands(String testName, TransactionBuilder builder) {
        ClusterTransaction transaction = new ClusterTransaction();
        Object[] expectedResult = builder.apply(transaction);

        Object[] results = clusterClient.exec(transaction).get();
        assertArrayEquals(expectedResult, results);
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("glide.TransactionTestUtilities#getPrimaryNodeTransactionBuilders")
    public void keyless_transactions_with_group_of_commands(
            String testName, TransactionBuilder builder) {
        ClusterTransaction transaction = new ClusterTransaction();
        Object[] expectedResult = builder.apply(transaction);

        SingleNodeRoute route = new SlotIdRoute(1, SlotType.PRIMARY);
        Object[] results = clusterClient.exec(transaction, route).get();
        assertArrayEquals(expectedResult, results);
    }

    @Test
    @SneakyThrows
    public void lastsave() {
        var yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        var response = clusterClient.exec(new ClusterTransaction().lastsave()).get();
        assertTrue(Instant.ofEpochSecond((long) response[0]).isAfter(yesterday));
    }

    @Test
    @SneakyThrows
    public void objectFreq() {
        String objectFreqKey = "key";
        String maxmemoryPolicy = "maxmemory-policy";
        String oldPolicy =
                clusterClient.configGet(new String[] {maxmemoryPolicy}).get().get(maxmemoryPolicy);
        try {
            ClusterTransaction transaction = new ClusterTransaction();
            transaction.configSet(Map.of(maxmemoryPolicy, "allkeys-lfu"));
            transaction.set(objectFreqKey, "");
            transaction.objectFreq(objectFreqKey);
            var response = clusterClient.exec(transaction).get();
            assertEquals(OK, response[0]);
            assertEquals(OK, response[1]);
            assertTrue((long) response[2] >= 0L);
        } finally {
            clusterClient.configSet(Map.of(maxmemoryPolicy, oldPolicy));
        }
    }

    @Test
    @SneakyThrows
    public void objectIdletime() {
        String objectIdletimeKey = "key";
        ClusterTransaction transaction = new ClusterTransaction();
        transaction.set(objectIdletimeKey, "");
        transaction.objectIdletime(objectIdletimeKey);
        var response = clusterClient.exec(transaction).get();
        assertEquals(OK, response[0]);
        assertTrue((long) response[1] >= 0L);
    }

    @Test
    @SneakyThrows
    public void objectRefcount() {
        String objectRefcountKey = "key";
        ClusterTransaction transaction = new ClusterTransaction();
        transaction.set(objectRefcountKey, "");
        transaction.objectRefcount(objectRefcountKey);
        var response = clusterClient.exec(transaction).get();
        assertEquals(OK, response[0]);
        assertTrue((long) response[1] >= 0L);
    }

    @Test
    @SneakyThrows
    public void zrank_zrevrank_withscores() {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.2.0"));
        String zSetKey1 = "{key}:zsetKey1-" + UUID.randomUUID();
        ClusterTransaction transaction = new ClusterTransaction();
        transaction.zadd(zSetKey1, Map.of("one", 1.0, "two", 2.0, "three", 3.0));
        transaction.zrankWithScore(zSetKey1, "one");
        transaction.zrevrankWithScore(zSetKey1, "one");

        Object[] result = clusterClient.exec(transaction).get();
        assertEquals(3L, result[0]);
        assertArrayEquals(new Object[] {0L, 1.0}, (Object[]) result[1]);
        assertArrayEquals(new Object[] {2L, 1.0}, (Object[]) result[2]);
    }
}
