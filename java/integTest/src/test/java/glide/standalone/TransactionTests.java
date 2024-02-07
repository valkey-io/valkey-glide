/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestUtilities.transactionTest;
import static glide.TestUtilities.transactionTestResult;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_EXISTS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.TestConfiguration;
import glide.api.RedisClient;
import glide.api.models.Transaction;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
        Transaction transaction = new Transaction().customCommand("info");
        Object[] result = client.exec(transaction).get(10, TimeUnit.SECONDS);
        assertTrue(((String) result[0]).contains("# Stats"));
    }

    @Test
    @SneakyThrows
    public void info_test() {
        Transaction transaction =
                new Transaction()
                        .info()
                        .info(InfoOptions.builder().section(InfoOptions.Section.CLUSTER).build())
                        .info(
                                InfoOptions.builder()
                                        .section(InfoOptions.Section.STATS)
                                        .section(InfoOptions.Section.CLUSTER)
                                        .build())
                        .info(InfoOptions.builder().section(InfoOptions.Section.DEFAULT).build())
                        .info(InfoOptions.builder().section(InfoOptions.Section.EVERYTHING).build());
        Object[] result = client.exec(transaction).get(10, TimeUnit.SECONDS);

        // sanity check
        assertTrue(((String) result[0]).contains("# Stats"));
        assertFalse(((String) result[1]).contains("# Stats"));
        assertTrue(((String) result[2]).contains("# Stats"));
        assertTrue(((String) result[3]).contains("# Stats"));
        assertTrue(((String) result[4]).contains("# Stats"));

        // And we only get a single section
        List<String> clusterInfoOptionsResultSections =
                Arrays.stream(((String) result[1]).split(System.lineSeparator()))
                        .filter(s -> s.startsWith("#"))
                        .collect(Collectors.toList());
        assertEquals(1, clusterInfoOptionsResultSections.size());
        List<String> statsInfoOptionsResultSections =
                Arrays.stream(((String) result[2]).split(System.lineSeparator()))
                        .filter(s -> s.startsWith("#"))
                        .collect(Collectors.toList());
        assertEquals(2, statsInfoOptionsResultSections.size());

        // ensure that empty and DEFAULT contains the same headers
        List<String> emptyInfoOptionsResultSections =
                Arrays.stream(((String) result[0]).split(System.lineSeparator()))
                        .filter(s -> s.startsWith("#"))
                        .collect(Collectors.toList());
        List<String> defaultInfoOptionsResultSections =
                Arrays.stream(((String) result[3]).split(System.lineSeparator()))
                        .filter(s -> s.startsWith("#"))
                        .collect(Collectors.toList());
        assertEquals(emptyInfoOptionsResultSections, defaultInfoOptionsResultSections);

        // ensure that EVERYTHING has more headers
        List<String> everythingInfoOptionsResultSections =
                Arrays.stream(((String) result[4]).split(System.lineSeparator()))
                        .filter(s -> s.startsWith("#"))
                        .collect(Collectors.toList());
        assertTrue(
                everythingInfoOptionsResultSections.size() > defaultInfoOptionsResultSections.size());
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

    @Test
    @SneakyThrows
    public void get_set_tests() {
        Transaction transaction =
                new Transaction()
                        .set("key", "0")
                        .get("key")
                        .set("key", "1")
                        .get("key")
                        .set("key", "2", SetOptions.builder().conditionalSet(ONLY_IF_EXISTS).build())
                        .get("key")
                        .set("key", "3", SetOptions.builder().conditionalSet(ONLY_IF_DOES_NOT_EXIST).build())
                        .get("key");
        Object[] result = client.exec(transaction).get(10, TimeUnit.SECONDS);
        assertEquals("OK", result[0]);
        assertEquals("0", result[1]);
        assertEquals("OK", result[2]);
        assertEquals("1", result[3]);
        assertEquals("OK", result[4]);
        assertEquals("2", result[5]);
        assertNull(result[6]);
        assertEquals("2", result[7]);
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
