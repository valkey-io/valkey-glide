/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.REDIS_VERSION;
import static glide.TestUtilities.createDefaultClusterClient;
import static glide.TestUtilities.tryCommandWithExpectedError;
import static glide.TransactionTestUtilities.transactionTest;
import static glide.TransactionTestUtilities.transactionTestResult;
import static glide.api.BaseClient.OK;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.RedisClusterClient;
import glide.api.models.ClusterTransaction;
import glide.api.models.exceptions.RequestException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10) // seconds
public class ClusterTransactionTests {

    private static RedisClusterClient clusterClient = null;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        clusterClient = createDefaultClusterClient();
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
    @Test
    public void test_cluster_transactions() {
        ClusterTransaction transaction = (ClusterTransaction) transactionTest(new ClusterTransaction());
        Object[] expectedResult = transactionTestResult();

        Object[] results = clusterClient.exec(transaction, RANDOM).get();
        assertArrayEquals(expectedResult, results);
    }

    @Test
    @SneakyThrows
    public void save() {
        String error = "Background save already in progress";
        // use another client, because it could be blocked
        try (var testClient = createDefaultClusterClient()) {

            if (REDIS_VERSION.isLowerThan("7.0.0")) {
                var transactionResponse =
                        tryCommandWithExpectedError(
                                () -> testClient.exec(new ClusterTransaction().save()), error);
                assertTrue(
                        transactionResponse.getValue() != null || transactionResponse.getKey()[0].equals(OK));
            } else {
                Exception ex =
                        assertThrows(
                                ExecutionException.class,
                                () -> testClient.exec(new ClusterTransaction().save()).get());
                assertInstanceOf(RequestException.class, ex.getCause());
                assertTrue(ex.getCause().getMessage().contains("Command not allowed inside a transaction"));
            }
        }
    }

    @Test
    @SneakyThrows
    public void lastsave() {
        var yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        var response = clusterClient.exec(new ClusterTransaction().lastsave()).get();
        assertTrue(Instant.ofEpochSecond((long) response[0]).isAfter(yesterday));
    }
}
