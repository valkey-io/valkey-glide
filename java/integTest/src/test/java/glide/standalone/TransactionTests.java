/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.assertDeepEquals;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.generateLuaLibCode;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.commands.InfoOptions.Section.CLUSTER;
import static glide.api.models.commands.SortBaseOptions.OrderBy.DESC;
import static glide.api.models.commands.scan.ScanOptions.ObjectType.HASH;
import static glide.api.models.commands.scan.ScanOptions.ObjectType.LIST;
import static glide.api.models.commands.scan.ScanOptions.ObjectType.SET;
import static glide.api.models.commands.scan.ScanOptions.ObjectType.STREAM;
import static glide.api.models.commands.scan.ScanOptions.ObjectType.STRING;
import static glide.api.models.commands.scan.ScanOptions.ObjectType.ZSET;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.TransactionTestUtilities.TransactionBuilder;
import glide.api.GlideClient;
import glide.api.models.GlideString;
import glide.api.models.Transaction;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.SortOptions;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.exceptions.RequestException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(10) // seconds
public class TransactionTests {

    private static GlideClient client = null;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        client = GlideClient.createClient(commonClientConfig().requestTimeout(7000).build()).get();
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
        Object[] result = client.exec(transaction).get();
        assertTrue(((String) result[0]).contains("# Stats"));
    }

    @Test
    @SneakyThrows
    public void info_test() {
        Transaction transaction = new Transaction().info().info(new Section[] {CLUSTER});
        Object[] result = client.exec(transaction).get();

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
        Object[] result = client.exec(transaction).get();
        for (int idx = 0; idx < numberOfPings; idx++) {
            if ((idx % 2) == 0) {
                assertEquals("PONG", result[idx]);
            } else {
                assertEquals(Integer.toString(idx), result[idx]);
            }
        }
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("glide.TransactionTestUtilities#getCommonTransactionBuilders")
    public void transactions_with_group_of_commands(String testName, TransactionBuilder builder) {
        Transaction transaction = new Transaction();
        Object[] expectedResult = builder.apply(transaction);

        Object[] results = client.exec(transaction).get();
        assertDeepEquals(expectedResult, results);
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("glide.TransactionTestUtilities#getPrimaryNodeTransactionBuilders")
    public void keyless_transactions_with_group_of_commands(
            String testName, TransactionBuilder builder) {
        Transaction transaction = new Transaction();
        Object[] expectedResult = builder.apply(transaction);

        Object[] results = client.exec(transaction).get();
        assertDeepEquals(expectedResult, results);
    }

    @SneakyThrows
    @Test
    public void test_transaction_large_values() {
        int length = 1 << 25; // 33mb
        String key = "0".repeat(length);
        String value = "0".repeat(length);

        Transaction transaction = new Transaction();
        transaction.set(key, value);
        transaction.get(key);

        Object[] expectedResult =
                new Object[] {
                    OK, // transaction.set(key, value);
                    value, // transaction.get(key);
                };

        Object[] result = client.exec(transaction).get();
        assertArrayEquals(expectedResult, result);
    }

    @SneakyThrows
    @Test
    public void test_standalone_transaction() {
        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();

        Transaction transaction = new Transaction();
        transaction.set(key, value);
        transaction.get(key);
        transaction.move(key, 1L);
        transaction.get(key);
        transaction.select(1);
        transaction.get(key);

        Object[] expectedResult =
                new Object[] {
                    OK, // transaction.set(key, value);
                    value, // transaction.get(key);
                    true, // transaction.move(key, 1L);
                    null, // transaction.get(key);
                    OK, // transaction.select(1);
                    value // transaction.get(key);
                };

        Object[] result = client.exec(transaction).get();
        assertArrayEquals(expectedResult, result);
    }

    @Test
    @SneakyThrows
    public void lastsave() {
        var yesterday = Instant.now().minus(1, ChronoUnit.DAYS);

        var response = client.exec(new Transaction().lastsave()).get();
        assertTrue(Instant.ofEpochSecond((long) response[0]).isAfter(yesterday));
    }

    @Test
    @SneakyThrows
    public void objectFreq() {
        String objectFreqKey = "key";
        String maxmemoryPolicy = "maxmemory-policy";

        String oldPolicy = client.configGet(new String[] {maxmemoryPolicy}).get().get(maxmemoryPolicy);
        try {
            Transaction transaction = new Transaction();
            transaction.configSet(Map.of(maxmemoryPolicy, "allkeys-lfu"));
            transaction.set(objectFreqKey, "");
            transaction.objectFreq(objectFreqKey);
            var response = client.exec(transaction).get();
            assertEquals(OK, response[0]);
            assertEquals(OK, response[1]);
            assertTrue((long) response[2] >= 0L);
        } finally {
            client.configSet(Map.of(maxmemoryPolicy, oldPolicy)).get();
        }
    }

    @Test
    @SneakyThrows
    public void objectIdletime() {
        String objectIdletimeKey = "key";
        Transaction transaction = new Transaction();
        transaction.set(objectIdletimeKey, "");
        transaction.objectIdletime(objectIdletimeKey);
        var response = client.exec(transaction).get();
        assertEquals(OK, response[0]);
        assertTrue((long) response[1] >= 0L);
    }

    @Test
    @SneakyThrows
    public void objectRefcount() {
        String objectRefcountKey = "key";
        Transaction transaction = new Transaction();
        transaction.set(objectRefcountKey, "");
        transaction.objectRefcount(objectRefcountKey);
        var response = client.exec(transaction).get();
        assertEquals(OK, response[0]);
        assertTrue((long) response[1] >= 0L);
    }

    @Test
    @SneakyThrows
    public void zrank_zrevrank_withscores() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.2.0"));
        String zSetKey1 = "{key}:zsetKey1-" + UUID.randomUUID();
        Transaction transaction = new Transaction();
        transaction.zadd(zSetKey1, Map.of("one", 1.0, "two", 2.0, "three", 3.0));
        transaction.zrankWithScore(zSetKey1, "one");
        transaction.zrevrankWithScore(zSetKey1, "one");

        Object[] result = client.exec(transaction).get();
        assertEquals(3L, result[0]);
        assertArrayEquals(new Object[] {0L, 1.0}, (Object[]) result[1]);
        assertArrayEquals(new Object[] {2L, 1.0}, (Object[]) result[2]);
    }

    @Test
    @SneakyThrows
    public void copy() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"));
        // setup
        String copyKey1 = "{CopyKey}-1-" + UUID.randomUUID();
        String copyKey2 = "{CopyKey}-2-" + UUID.randomUUID();
        Transaction transaction =
                new Transaction()
                        .copy(copyKey1, copyKey2, 1, false)
                        .set(copyKey1, "one")
                        .set(copyKey2, "two")
                        .copy(copyKey1, copyKey2, 1, false)
                        .copy(copyKey1, copyKey2, 1, true)
                        .copy(copyKey1, copyKey2, 2, true)
                        .select(1)
                        .get(copyKey2)
                        .select(2)
                        .get(copyKey2);
        Object[] expectedResult =
                new Object[] {
                    false, // copy(copyKey1, copyKey2, 1, false)
                    OK, // set(copyKey1, "one")
                    OK, // set(copyKey2, "two")
                    true, // copy(copyKey1, copyKey2, 1, false)
                    true, // copy(copyKey1, copyKey2, 1, true)
                    true, // copy(copyKey1, copyKey2, 2, true)
                    OK, // select(1)
                    "one", // get(copyKey2)
                    OK, // select(2)
                    "one", // get(copyKey2)
                };

        Object[] result = client.exec(transaction).get();
        assertArrayEquals(expectedResult, result);
    }

    @Test
    @SneakyThrows
    public void watch() {
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String key3 = "{key}-3" + UUID.randomUUID();
        String key4 = "{key}-4" + UUID.randomUUID();
        String foobarString = "foobar";
        String helloString = "hello";
        String[] keys = new String[] {key1, key2, key3};
        Transaction setFoobarTransaction = new Transaction();
        Transaction setHelloTransaction = new Transaction();
        String[] expectedExecResponse = new String[] {OK, OK, OK};

        // Returns null when a watched key is modified before it is executed in a transaction command.
        // Transaction commands are not performed.
        assertEquals(OK, client.watch(keys).get());
        assertEquals(OK, client.set(key2, helloString).get());
        setFoobarTransaction.set(key1, foobarString).set(key2, foobarString).set(key3, foobarString);
        assertNull(client.exec(setFoobarTransaction).get());
        assertNull(client.get(key1).get()); // Sanity check
        assertEquals(helloString, client.get(key2).get());
        assertNull(client.get(key3).get());

        // Transaction executes command successfully with a read command on the watch key before
        // transaction is executed.
        assertEquals(OK, client.watch(keys).get());
        assertEquals(helloString, client.get(key2).get());
        assertArrayEquals(expectedExecResponse, client.exec(setFoobarTransaction).get());
        assertEquals(foobarString, client.get(key1).get()); // Sanity check
        assertEquals(foobarString, client.get(key2).get());
        assertEquals(foobarString, client.get(key3).get());

        // Transaction executes command successfully with unmodified watched keys
        assertEquals(OK, client.watch(keys).get());
        assertArrayEquals(expectedExecResponse, client.exec(setFoobarTransaction).get());
        assertEquals(foobarString, client.get(key1).get()); // Sanity check
        assertEquals(foobarString, client.get(key2).get());
        assertEquals(foobarString, client.get(key3).get());

        // Transaction executes command successfully with a modified watched key but is not in the
        // transaction.
        assertEquals(OK, client.watch(new String[] {key4}).get());
        setHelloTransaction.set(key1, helloString).set(key2, helloString).set(key3, helloString);
        assertArrayEquals(expectedExecResponse, client.exec(setHelloTransaction).get());
        assertEquals(helloString, client.get(key1).get()); // Sanity check
        assertEquals(helloString, client.get(key2).get());
        assertEquals(helloString, client.get(key3).get());

        // WATCH can not have an empty String array parameter
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.watch(new String[] {}).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @Test
    @SneakyThrows
    public void watch_binary() {
        GlideString key1 = gs("{key}-1" + UUID.randomUUID());
        GlideString key2 = gs("{key}-2" + UUID.randomUUID());
        GlideString key3 = gs("{key}-3" + UUID.randomUUID());
        GlideString key4 = gs("{key}-4" + UUID.randomUUID());
        String foobarString = "foobar";
        String helloString = "hello";
        GlideString[] keys = new GlideString[] {key1, key2, key3};
        Transaction setFoobarTransaction = new Transaction();
        Transaction setHelloTransaction = new Transaction();
        String[] expectedExecResponse = new String[] {OK, OK, OK};

        // Returns null when a watched key is modified before it is executed in a transaction command.
        // Transaction commands are not performed.
        assertEquals(OK, client.watch(keys).get());
        assertEquals(OK, client.set(key2, gs(helloString)).get());
        setFoobarTransaction
                .set(key1.toString(), foobarString)
                .set(key2.toString(), foobarString)
                .set(key3.toString(), foobarString);
        assertNull(client.exec(setFoobarTransaction).get());
        assertNull(client.get(key1).get()); // Sanity check
        assertEquals(gs(helloString), client.get(key2).get());
        assertNull(client.get(key3).get());

        // Transaction executes command successfully with a read command on the watch key before
        // transaction is executed.
        assertEquals(OK, client.watch(keys).get());
        assertEquals(gs(helloString), client.get(key2).get());
        assertArrayEquals(expectedExecResponse, client.exec(setFoobarTransaction).get());
        assertEquals(gs(foobarString), client.get(key1).get()); // Sanity check
        assertEquals(gs(foobarString), client.get(key2).get());
        assertEquals(gs(foobarString), client.get(key3).get());

        // Transaction executes command successfully with unmodified watched keys
        assertEquals(OK, client.watch(keys).get());
        assertArrayEquals(expectedExecResponse, client.exec(setFoobarTransaction).get());
        assertEquals(gs(foobarString), client.get(key1).get()); // Sanity check
        assertEquals(gs(foobarString), client.get(key2).get());
        assertEquals(gs(foobarString), client.get(key3).get());

        // Transaction executes command successfully with a modified watched key but is not in the
        // transaction.
        assertEquals(OK, client.watch(new GlideString[] {key4}).get());
        setHelloTransaction
                .set(key1.toString(), helloString)
                .set(key2.toString(), helloString)
                .set(key3.toString(), helloString);
        assertArrayEquals(expectedExecResponse, client.exec(setHelloTransaction).get());
        assertEquals(gs(helloString), client.get(key1).get()); // Sanity check
        assertEquals(gs(helloString), client.get(key2).get());
        assertEquals(gs(helloString), client.get(key3).get());

        // WATCH can not have an empty String array parameter
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.watch(new GlideString[] {}).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @Test
    @SneakyThrows
    public void unwatch() {
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String foobarString = "foobar";
        String helloString = "hello";
        String[] keys = new String[] {key1, key2};
        Transaction setFoobarTransaction = new Transaction();
        String[] expectedExecResponse = new String[] {OK, OK};

        // UNWATCH returns OK when there no watched keys
        assertEquals(OK, client.unwatch().get());

        // Transaction executes successfully after modifying a watched key then calling UNWATCH
        assertEquals(OK, client.watch(keys).get());
        assertEquals(OK, client.set(key2, helloString).get());
        assertEquals(OK, client.unwatch().get());
        setFoobarTransaction.set(key1, foobarString).set(key2, foobarString);
        assertArrayEquals(expectedExecResponse, client.exec(setFoobarTransaction).get());
        assertEquals(foobarString, client.get(key1).get());
        assertEquals(foobarString, client.get(key2).get());
    }

    @Test
    @SneakyThrows
    public void sort_and_sortReadOnly() {
        Transaction transaction1 = new Transaction();
        Transaction transaction2 = new Transaction();
        String genericKey1 = "{GenericKey}-1-" + UUID.randomUUID();
        String genericKey2 = "{GenericKey}-2-" + UUID.randomUUID();
        String[] ascendingListByAge = new String[] {"Bob", "Alice"};
        String[] descendingListByAge = new String[] {"Alice", "Bob"};

        transaction1
                .hset("user:1", Map.of("name", "Alice", "age", "30"))
                .hset("user:2", Map.of("name", "Bob", "age", "25"))
                .lpush(genericKey1, new String[] {"2", "1"})
                .sort(
                        genericKey1,
                        SortOptions.builder().byPattern("user:*->age").getPattern("user:*->name").build())
                .sort(
                        genericKey1,
                        SortOptions.builder()
                                .orderBy(DESC)
                                .byPattern("user:*->age")
                                .getPattern("user:*->name")
                                .build())
                .sortStore(
                        genericKey1,
                        genericKey2,
                        SortOptions.builder().byPattern("user:*->age").getPattern("user:*->name").build())
                .lrange(genericKey2, 0, -1)
                .sortStore(
                        genericKey1,
                        genericKey2,
                        SortOptions.builder()
                                .orderBy(DESC)
                                .byPattern("user:*->age")
                                .getPattern("user:*->name")
                                .build())
                .lrange(genericKey2, 0, -1);

        var expectedResults =
                new Object[] {
                    2L, // hset("user:1", Map.of("name", "Alice", "age", "30"))
                    2L, // hset("user:2", Map.of("name", "Bob", "age", "25"))
                    2L, // lpush(genericKey1, new String[] {"2", "1"})
                    ascendingListByAge, // sort(genericKey1, SortOptions)
                    descendingListByAge, // sort(genericKey1, SortOptions)
                    2L, // sortStore(genericKey1, genericKey2, SortOptions)
                    ascendingListByAge, // lrange(genericKey4, 0, -1)
                    2L, // sortStore(genericKey1, genericKey2, SortOptions)
                    descendingListByAge, // lrange(genericKey2, 0, -1)
                };

        assertArrayEquals(expectedResults, client.exec(transaction1).get());

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            transaction2
                    .sortReadOnly(
                            genericKey1,
                            SortOptions.builder().byPattern("user:*->age").getPattern("user:*->name").build())
                    .sortReadOnly(
                            genericKey1,
                            SortOptions.builder()
                                    .orderBy(DESC)
                                    .byPattern("user:*->age")
                                    .getPattern("user:*->name")
                                    .build());

            expectedResults =
                    new Object[] {
                        ascendingListByAge, // sortReadOnly(genericKey1, SortOptions)
                        descendingListByAge, // sortReadOnly(genericKey1, SortOptions)
                    };

            assertArrayEquals(expectedResults, client.exec(transaction2).get());
        }
    }

    @SneakyThrows
    @Test
    public void waitTest() {
        // setup
        String key = UUID.randomUUID().toString();
        long numreplicas = 1L;
        long timeout = 1000L;
        Transaction transaction = new Transaction();

        transaction.set(key, "value");
        transaction.wait(numreplicas, timeout);

        Object[] results = client.exec(transaction).get();
        Object[] expectedResult =
                new Object[] {
                    OK, // set(key,  "value")
                    0L, // wait(numreplicas, timeout)
                };
        assertEquals(expectedResult[0], results[0]);
        assertTrue((long) expectedResult[1] <= (long) results[1]);
    }

    @SneakyThrows
    @Test
    public void scan_test() {
        // setup
        String key = UUID.randomUUID().toString();
        Map<String, String> msetMap = Map.of(key, UUID.randomUUID().toString());
        assertEquals(OK, client.mset(msetMap).get());

        String cursor = "0";
        Object[] keysFound = new Object[0];
        do {
            Transaction transaction = new Transaction();
            transaction.scan(cursor);
            Object[] results = client.exec(transaction).get();
            cursor = (String) ((Object[]) results[0])[0];
            keysFound = ArrayUtils.addAll(keysFound, (Object[]) ((Object[]) results[0])[1]);
        } while (!cursor.equals("0"));

        assertTrue(ArrayUtils.contains(keysFound, key));
    }

    @SneakyThrows
    @Test
    public void scan_binary_test() {
        // setup
        String key = UUID.randomUUID().toString();
        Map<String, String> msetMap = Map.of(key, UUID.randomUUID().toString());
        assertEquals(OK, client.mset(msetMap).get());

        GlideString cursor = gs("0");
        Object[] keysFound = new Object[0];
        do {
            Transaction transaction = new Transaction().withBinaryOutput().scan(cursor);
            Object[] results = client.exec(transaction).get();
            cursor = (GlideString) ((Object[]) results[0])[0];
            keysFound = ArrayUtils.addAll(keysFound, (Object[]) ((Object[]) results[0])[1]);
        } while (!cursor.equals(gs("0")));

        assertTrue(ArrayUtils.contains(keysFound, gs(key)));
    }

    @SneakyThrows
    @Test
    public void scan_with_options_test() {
        // setup
        Transaction setupTransaction = new Transaction();

        Map<ScanOptions.ObjectType, String> typeKeys =
                Map.of(
                        STRING, "{string}-" + UUID.randomUUID(),
                        LIST, "{list}-" + UUID.randomUUID(),
                        SET, "{set}-" + UUID.randomUUID(),
                        ZSET, "{zset}-" + UUID.randomUUID(),
                        HASH, "{hash}-" + UUID.randomUUID(),
                        STREAM, "{stream}-" + UUID.randomUUID());

        setupTransaction.set(typeKeys.get(STRING), UUID.randomUUID().toString());
        setupTransaction.lpush(typeKeys.get(LIST), new String[] {UUID.randomUUID().toString()});
        setupTransaction.sadd(typeKeys.get(SET), new String[] {UUID.randomUUID().toString()});
        setupTransaction.zadd(typeKeys.get(ZSET), Map.of(UUID.randomUUID().toString(), 1.0));
        setupTransaction.hset(
                typeKeys.get(HASH), Map.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        setupTransaction.xadd(
                typeKeys.get(STREAM), Map.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        assertNotNull(client.exec(setupTransaction).get());

        for (var type : ScanOptions.ObjectType.values()) {
            ScanOptions options = ScanOptions.builder().type(type).count(99L).build();

            String cursor = "0";
            Object[] keysFound = new Object[0];
            do {
                Transaction transaction = new Transaction();
                transaction.scan(cursor, options);
                Object[] results = client.exec(transaction).get();
                cursor = (String) ((Object[]) results[0])[0];
                keysFound = ArrayUtils.addAll(keysFound, (Object[]) ((Object[]) results[0])[1]);
            } while (!cursor.equals("0"));

            assertTrue(
                    ArrayUtils.contains(keysFound, typeKeys.get(type)),
                    "Unable to find " + typeKeys.get(type) + " in a scan by type");

            options = ScanOptions.builder().matchPattern(typeKeys.get(type)).count(42L).build();
            cursor = "0";
            keysFound = new Object[0];
            do {
                Transaction transaction = new Transaction();
                transaction.scan(cursor, options);
                Object[] results = client.exec(transaction).get();
                cursor = (String) ((Object[]) results[0])[0];
                keysFound = ArrayUtils.addAll(keysFound, (Object[]) ((Object[]) results[0])[1]);
            } while (!cursor.equals("0"));

            assertTrue(
                    ArrayUtils.contains(keysFound, typeKeys.get(type)),
                    "Unable to find " + typeKeys.get(type) + " in a scan by match pattern");
        }
    }

    @SneakyThrows
    @Test
    public void scan_binary_with_options_test() {
        // setup
        Transaction setupTransaction = new Transaction().withBinaryOutput();

        Map<ScanOptions.ObjectType, GlideString> typeKeys =
                Map.of(
                        STRING, gs("{string}-" + UUID.randomUUID()),
                        LIST, gs("{list}-" + UUID.randomUUID()),
                        SET, gs("{set}-" + UUID.randomUUID()),
                        ZSET, gs("{zset}-" + UUID.randomUUID()),
                        HASH, gs("{hash}-" + UUID.randomUUID()),
                        STREAM, gs("{stream}-" + UUID.randomUUID()));

        setupTransaction.set(typeKeys.get(STRING), UUID.randomUUID().toString());
        setupTransaction.lpush(typeKeys.get(LIST), new String[] {UUID.randomUUID().toString()});
        setupTransaction.sadd(typeKeys.get(SET), new String[] {UUID.randomUUID().toString()});
        setupTransaction.zadd(typeKeys.get(ZSET), Map.of(UUID.randomUUID().toString(), 1.0));
        setupTransaction.hset(
                typeKeys.get(HASH), Map.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        setupTransaction.xadd(
                typeKeys.get(STREAM), Map.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        assertNotNull(client.exec(setupTransaction).get());

        final GlideString initialCursor = gs("0");

        for (var type : ScanOptions.ObjectType.values()) {
            ScanOptions options = ScanOptions.builder().type(type).count(99L).build();

            GlideString cursor = initialCursor;
            Object[] keysFound = new Object[0];
            do {
                Transaction transaction = new Transaction().withBinaryOutput().scan(cursor, options);
                Object[] results = client.exec(transaction).get();
                cursor = (GlideString) ((Object[]) results[0])[0];
                keysFound = ArrayUtils.addAll(keysFound, (Object[]) ((Object[]) results[0])[1]);
            } while (!cursor.equals(initialCursor));

            assertTrue(
                    ArrayUtils.contains(keysFound, typeKeys.get(type)),
                    "Unable to find " + typeKeys.get(type) + " in a scan by type");

            options =
                    ScanOptions.builder().matchPattern(typeKeys.get(type).toString()).count(42L).build();
            cursor = initialCursor;
            keysFound = new Object[0];
            do {
                Transaction transaction = new Transaction().withBinaryOutput().scan(cursor, options);
                Object[] results = client.exec(transaction).get();
                cursor = (GlideString) ((Object[]) results[0])[0];
                keysFound = ArrayUtils.addAll(keysFound, (Object[]) ((Object[]) results[0])[1]);
            } while (!cursor.equals(initialCursor));

            assertTrue(
                    ArrayUtils.contains(keysFound, typeKeys.get(type)),
                    "Unable to find " + typeKeys.get(type) + " in a scan by match pattern");
        }
    }

    @Test
    @SneakyThrows
    public void test_transaction_dump_restore() {
        GlideString key1 = gs("{key}-1" + UUID.randomUUID());
        GlideString key2 = gs("{key}-2" + UUID.randomUUID());
        String value = UUID.randomUUID().toString();

        // Setup
        assertEquals(OK, client.set(key1, gs(value)).get());

        // Verify dump
        Transaction transaction = new Transaction().withBinaryOutput().dump(key1);
        Object[] result = client.exec(transaction).get();
        GlideString payload = (GlideString) (result[0]);

        // Verify restore
        transaction = new Transaction();
        transaction.restore(key2, 0, payload.getBytes());
        transaction.get(key2);
        Object[] response = client.exec(transaction).get();
        assertEquals(OK, response[0]);
        assertEquals(value, response[1]);
    }

    @Test
    @SneakyThrows
    public void test_transaction_function_dump_restore() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"));
        String libName = "mylib";
        String funcName = "myfun";
        String code = generateLuaLibCode(libName, Map.of(funcName, "return args[1]"), true);

        // Setup
        client.functionLoad(code, true).get();

        // Verify functionDump
        Transaction transaction = new Transaction().withBinaryOutput().functionDump();
        Object[] result = client.exec(transaction).get();
        GlideString payload = (GlideString) (result[0]);

        // Verify functionRestore
        transaction = new Transaction();
        transaction.functionRestore(payload.getBytes(), FunctionRestorePolicy.REPLACE);
        Object[] response = client.exec(transaction).get();
        assertEquals(OK, response[0]);
    }

    @Test
    @SneakyThrows
    public void test_transaction_xinfoStream() {
        Transaction transaction = new Transaction();
        final String streamKey = "{streamKey}-" + UUID.randomUUID();
        LinkedHashMap<String, Object> expectedStreamInfo =
                new LinkedHashMap<>() {
                    {
                        put("radix-tree-keys", 1L);
                        put("radix-tree-nodes", 2L);
                        put("length", 1L);
                        put("groups", 0L);
                        put("first-entry", new Object[] {"0-1", new Object[] {"field1", "value1"}});
                        put("last-generated-id", "0-1");
                        put("last-entry", new Object[] {"0-1", new Object[] {"field1", "value1"}});
                    }
                };
        LinkedHashMap<String, Object> expectedStreamFullInfo =
                new LinkedHashMap<>() {
                    {
                        put("radix-tree-keys", 1L);
                        put("radix-tree-nodes", 2L);
                        put("entries", new Object[][] {{"0-1", new Object[] {"field1", "value1"}}});
                        put("length", 1L);
                        put("groups", new Object[0]);
                        put("last-generated-id", "0-1");
                    }
                };

        transaction
                .xadd(streamKey, Map.of("field1", "value1"), StreamAddOptions.builder().id("0-1").build())
                .xinfoStream(streamKey)
                .xinfoStreamFull(streamKey);

        Object[] results = client.exec(transaction).get();

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            expectedStreamInfo.put("max-deleted-entry-id", "0-0");
            expectedStreamInfo.put("entries-added", 1L);
            expectedStreamInfo.put("recorded-first-entry-id", "0-1");
            expectedStreamFullInfo.put("max-deleted-entry-id", "0-0");
            expectedStreamFullInfo.put("entries-added", 1L);
            expectedStreamFullInfo.put("recorded-first-entry-id", "0-1");
        }

        assertDeepEquals(
                new Object[] {
                    "0-1", // xadd(streamKey1, Map.of("field1", "value1"), ... .id("0-1").build());
                    expectedStreamInfo, // xinfoStream(streamKey)
                    expectedStreamFullInfo, // xinfoStreamFull(streamKey1)
                },
                results);
    }

    @SneakyThrows
    @Test
    public void binary_strings() {
        String key = UUID.randomUUID().toString();
        client.set(key, "_").get();
        // use dump to ensure that we have non-string convertible bytes
        var bytes = client.dump(gs(key)).get();

        var transaction = new Transaction().withBinaryOutput().set(gs(key), gs(bytes)).get(gs(key));

        var responses = client.exec(transaction).get();

        assertDeepEquals(
                new Object[] {
                    OK, gs(bytes),
                },
                responses);
    }
}
