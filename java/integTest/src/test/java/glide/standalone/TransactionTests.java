/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TransactionTestUtilities.transactionTest;
import static glide.TransactionTestUtilities.transactionTestResult;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.TestConfiguration;
import glide.api.RedisClient;
import glide.api.models.Transaction;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TransactionTests {

    private static RedisClient client = null;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        client =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(
                                                NodeAddress.builder().port(TestConfiguration.STANDALONE_PORTS[0]).build())
                                        .build())
                        .get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        client.close();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        Transaction transaction = new Transaction().customCommand(new String[] {"info"});
        Object[] result = client.exec(transaction).get(10, TimeUnit.SECONDS);
        assertTrue(((String) result[0]).contains("# Stats"));
    }

    @Test
    @SneakyThrows
    public void info_test() {
        Transaction transaction =
                new Transaction()
                        .info()
                        .info(InfoOptions.builder().section(InfoOptions.Section.CLUSTER).build());
        Object[] result = client.exec(transaction).get(10, TimeUnit.SECONDS);

        // sanity check
        assertTrue(((String) result[0]).contains("# Stats"));
        assertFalse(((String) result[1]).contains("# Stats"));
    }

    @Test
    @SneakyThrows
    public void ping_tests() {
        Transaction transaction = new Transaction();
        int numberOfPings = 100;
        for (int idx = 0; idx < numberOfPings; idx++) {
            if ((idx % 2) == 0) {
                transaction.ping();
            } else {
                transaction.ping(Integer.toString(idx));
            }
        }
        Object[] result = client.exec(transaction).get(10, TimeUnit.SECONDS);
        for (int idx = 0; idx < numberOfPings; idx++) {
            if ((idx % 2) == 0) {
                assertEquals("PONG", result[idx]);
            } else {
                assertEquals(Integer.toString(idx), result[idx]);
            }
        }
    }

    @SneakyThrows
    @Test
    public void test_standalone_transactions() {
        Transaction transaction = (Transaction) transactionTest(new Transaction());
        Object[] expectedResult = transactionTestResult();

        Object[] result = client.exec(transaction).get(10, TimeUnit.SECONDS);
        assertArrayEquals(expectedResult, result);
    }
}
