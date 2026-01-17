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
import static org.junit.jupiter.api.Named.named;

import glide.BatchTestUtilities.BatchBuilder;
import glide.api.GlideClient;
import glide.api.models.Batch;
import glide.api.models.GlideString;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.SortOptions;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.configuration.ProtocolVersion;
import glide.api.models.exceptions.RequestException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(20) // seconds
public class BatchTests {

    @SneakyThrows
    public static Stream<Arguments> getClients() {
        return Stream.of(
                Arguments.of(
                        named(
                                "RESP2",
                                GlideClient.createClient(
                                                commonClientConfig()
                                                        .requestTimeout(7000)
                                                        .protocol(ProtocolVersion.RESP2)
                                                        .build())
                                        .get())),
                Arguments.of(
                        named(
                                "RESP3",
                                GlideClient.createClient(
                                                commonClientConfig()
                                                        .requestTimeout(7000)
                                                        .protocol(ProtocolVersion.RESP3)
                                                        .build())
                                        .get())));
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void custom_command_info(GlideClient client, boolean isAtomic) {
        Batch batch = new Batch(isAtomic).customCommand(new String[] {"info"});
        Object[] result = client.exec(batch, true).get();
        assertTrue(((String) result[0]).contains("# Stats"));
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void info_test(GlideClient client, boolean isAtomic) {
        Batch batch = new Batch(isAtomic).info().info(new Section[] {CLUSTER});
        Object[] result = client.exec(batch, true).get();

        // sanity check
        assertTrue(((String) result[0]).contains("# Stats"));
        assertFalse(((String) result[1]).contains("# Stats"));
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void ping_tests(GlideClient client, boolean isAtomic) {
        Batch batch = new Batch(isAtomic);
        int numberOfPings = 100;
        for (int idx = 0; idx < numberOfPings; idx++) {
            if ((idx % 2) == 0) {
                batch.ping();
            } else {
                batch.ping(Integer.toString(idx));
            }
        }
        Object[] result = client.exec(batch, true).get();
        for (int idx = 0; idx < numberOfPings; idx++) {
            if ((idx % 2) == 0) {
                assertEquals("PONG", result[idx]);
            } else {
                assertEquals(Integer.toString(idx), result[idx]);
            }
        }
    }

    public static Stream<Arguments> getCommonBatchBuilders() {
        return glide.BatchTestUtilities.getCommonBatchBuilders()
                .flatMap(
                        test ->
                                getClients()
                                        .flatMap(
                                                client ->
                                                        Stream.of(true, false)
                                                                .map(
                                                                        isAtomic ->
                                                                                Arguments.of(
                                                                                        test.get()[0], // test name (String)
                                                                                        test.get()[1], // BatchBuilder
                                                                                        client.get()[0], // GlideClient or GlideClusterClient
                                                                                        isAtomic // boolean isAtomic
                                                                                        ))));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0} - isAtomic: {3}")
    @MethodSource("getCommonBatchBuilders")
    public void batches_with_group_of_commands(
            String testName, BatchBuilder builder, GlideClient client, boolean isAtomic) {
        Batch batches = new Batch(isAtomic);
        Object[] expectedResult = builder.apply(batches, isAtomic);

        Object[] results = client.exec(batches, true).get();
        assertDeepEquals(expectedResult, results);
    }

    public static Stream<Arguments> getPrimaryNodeBatchBuilders() {
        return glide.BatchTestUtilities.getPrimaryNodeBatchBuilders()
                .flatMap(
                        test ->
                                getClients()
                                        .flatMap(
                                                client ->
                                                        Stream.of(true, false)
                                                                .map(
                                                                        isAtomic ->
                                                                                Arguments.of(
                                                                                        test.get()[0], // test name
                                                                                        test.get()[1], // BatchBuilder
                                                                                        client.get()[0], // GlideClient
                                                                                        isAtomic // boolean isAtomic
                                                                                        ))));
    }

    public static Stream<Arguments> getClientsWithAtomic() {
        return getClients()
                .flatMap(
                        client ->
                                Stream.of(true, false)
                                        .map(
                                                isAtomic ->
                                                        Arguments.of(
                                                                client.get()[0],
                                                                isAtomic) // Providing both the client and isAtomic flag
                                                ));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0} - isAtomic: {3}")
    @MethodSource("getPrimaryNodeBatchBuilders")
    public void keyless_batches_with_group_of_commands(
            String testName, BatchBuilder builder, GlideClient client, boolean isAtomic) {
        Batch batches = new Batch(isAtomic);
        Object[] expectedResult = builder.apply(batches, isAtomic);
        if (expectedResult.length == 0 && !isAtomic) {
            // Empty pipelines returns an error
            return;
        }
        Object[] results = client.exec(batches, true).get();
        assertDeepEquals(expectedResult, results);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    public void test_batch_large_values(GlideClient client, boolean isAtomic) {
        // Skip on macOS - the macOS tests run on self hosted VMs which have resource limits
        // making this test flaky with "no buffer space available" errors. See -
        // https://github.com/valkey-io/valkey-glide/issues/4902
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            return;
        }

        int length = 1 << 25; // 33mb
        String key = "0".repeat(length);
        String value = "0".repeat(length);

        Batch batch = new Batch(isAtomic);
        batch.set(key, value);
        batch.get(key);

        Object[] expectedResult =
                new Object[] {
                    OK, // batch.set(key, value);
                    value, // batch.get(key);
                };

        Object[] result = client.exec(batch, true).get();
        assertArrayEquals(expectedResult, result);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    public void test_standalone_batch(GlideClient client, boolean isAtomic) {
        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();

        Batch batch = new Batch(isAtomic);
        batch.select(0);
        batch.set(key, value);
        batch.get(key);
        batch.move(key, 1L);
        batch.get(key);
        batch.select(1);
        batch.get(key);

        Object[] expectedResult =
                new Object[] {
                    OK, // batch.select(0);
                    OK, // batch.set(key, value);
                    value, // batch.get(key);
                    true, // batch.move(key, 1L);
                    null, // batch.get(key);
                    OK, // batch.select(1);
                    value // batch.get(key);
                };

        Object[] result = client.exec(batch, true).get();
        assertArrayEquals(expectedResult, result);
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void lastsave(GlideClient client, boolean isAtomic) {
        var yesterday = Instant.now().minus(1, ChronoUnit.DAYS);

        var response = client.exec(new Batch(isAtomic).lastsave(), true).get();
        assertTrue(Instant.ofEpochSecond((long) response[0]).isAfter(yesterday));
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void objectFreq(GlideClient client, boolean isAtomic) {
        String objectFreqKey = "key";
        String maxmemoryPolicy = "maxmemory-policy";

        String oldPolicy = client.configGet(new String[] {maxmemoryPolicy}).get().get(maxmemoryPolicy);
        try {
            Batch batch = new Batch(isAtomic);
            batch.configSet(Map.of(maxmemoryPolicy, "allkeys-lfu"));
            batch.set(objectFreqKey, "");
            batch.objectFreq(objectFreqKey);
            var response = client.exec(batch, true).get();
            assertEquals(OK, response[0]);
            assertEquals(OK, response[1]);
            assertTrue((long) response[2] >= 0L);
        } finally {
            client.configSet(Map.of(maxmemoryPolicy, oldPolicy)).get();
        }
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void objectIdletime(GlideClient client, boolean isAtomic) {
        String objectIdletimeKey = "key";
        Batch batch = new Batch(isAtomic);
        batch.set(objectIdletimeKey, "");
        batch.objectIdletime(objectIdletimeKey);
        var response = client.exec(batch, true).get();
        assertEquals(OK, response[0]);
        assertTrue((long) response[1] >= 0L);
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void objectRefcount(GlideClient client, boolean isAtomic) {
        String objectRefcountKey = "key";
        Batch batch = new Batch(isAtomic);
        batch.set(objectRefcountKey, "");
        batch.objectRefcount(objectRefcountKey);
        var response = client.exec(batch, true).get();
        assertEquals(OK, response[0]);
        assertTrue((long) response[1] >= 0L);
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void zrank_zrevrank_withscores(GlideClient client, boolean isAtomic) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.2.0"));
        String zSetKey1 = "{key}:zsetKey1-" + UUID.randomUUID();
        Batch batch = new Batch(isAtomic);
        batch.zadd(zSetKey1, Map.of("one", 1.0, "two", 2.0, "three", 3.0));
        batch.zrankWithScore(zSetKey1, "one");
        batch.zrevrankWithScore(zSetKey1, "one");

        Object[] result = client.exec(batch, true).get();
        assertEquals(3L, result[0]);
        assertArrayEquals(new Object[] {0L, 1.0}, (Object[]) result[1]);
        assertArrayEquals(new Object[] {2L, 1.0}, (Object[]) result[2]);
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void copy(GlideClient client, boolean isAtomic) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"));
        // setup
        String copyKey1 = "{CopyKey}-1-" + UUID.randomUUID();
        String copyKey2 = "{CopyKey}-2-" + UUID.randomUUID();
        Batch batch =
                new Batch(isAtomic)
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

        Object[] result = client.exec(batch, true).get();
        assertArrayEquals(expectedResult, result);
    }

    @ParameterizedTest
    @MethodSource("getClients")
    @SneakyThrows
    public void watch(GlideClient client) {
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String key3 = "{key}-3" + UUID.randomUUID();
        String key4 = "{key}-4" + UUID.randomUUID();
        String foobarString = "foobar";
        String helloString = "hello";
        String[] keys = new String[] {key1, key2, key3};
        Batch setFoobarTransaction = new Batch(true);
        Batch setHelloTransaction = new Batch(true);
        String[] expectedExecResponse = new String[] {OK, OK, OK};

        // Returns null when a watched key is modified before it is executed in a transaction command.
        // Batch commands are not performed.
        assertEquals(OK, client.watch(keys).get());
        assertEquals(OK, client.set(key2, helloString).get());
        setFoobarTransaction.set(key1, foobarString).set(key2, foobarString).set(key3, foobarString);
        assertNull(client.exec(setFoobarTransaction, true).get());
        assertNull(client.get(key1).get()); // Sanity check
        assertEquals(helloString, client.get(key2).get());
        assertNull(client.get(key3).get());

        // Batch executes command successfully with a read command on the watch key before
        // transaction is executed.
        assertEquals(OK, client.watch(keys).get());
        assertEquals(helloString, client.get(key2).get());
        assertArrayEquals(expectedExecResponse, client.exec(setFoobarTransaction, true).get());
        assertEquals(foobarString, client.get(key1).get()); // Sanity check
        assertEquals(foobarString, client.get(key2).get());
        assertEquals(foobarString, client.get(key3).get());

        // Batch executes command successfully with unmodified watched keys
        assertEquals(OK, client.watch(keys).get());
        assertArrayEquals(expectedExecResponse, client.exec(setFoobarTransaction, true).get());
        assertEquals(foobarString, client.get(key1).get()); // Sanity check
        assertEquals(foobarString, client.get(key2).get());
        assertEquals(foobarString, client.get(key3).get());

        // Batch executes command successfully with a modified watched key but is not in the
        // transaction.
        assertEquals(OK, client.watch(new String[] {key4}).get());
        setHelloTransaction.set(key1, helloString).set(key2, helloString).set(key3, helloString);
        assertArrayEquals(expectedExecResponse, client.exec(setHelloTransaction, true).get());
        assertEquals(helloString, client.get(key1).get()); // Sanity check
        assertEquals(helloString, client.get(key2).get());
        assertEquals(helloString, client.get(key3).get());

        // WATCH can not have an empty String array parameter
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.watch(new String[] {}).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @ParameterizedTest
    @MethodSource("getClients")
    @SneakyThrows
    public void watch_binary(GlideClient client) {
        GlideString key1 = gs("{key}-1" + UUID.randomUUID());
        GlideString key2 = gs("{key}-2" + UUID.randomUUID());
        GlideString key3 = gs("{key}-3" + UUID.randomUUID());
        GlideString key4 = gs("{key}-4" + UUID.randomUUID());
        String foobarString = "foobar";
        String helloString = "hello";
        GlideString[] keys = new GlideString[] {key1, key2, key3};
        Batch setFoobarTransaction = new Batch(true);
        Batch setHelloTransaction = new Batch(true);
        String[] expectedExecResponse = new String[] {OK, OK, OK};

        // Returns null when a watched key is modified before it is executed in a transaction command.
        // Batch commands are not performed.
        assertEquals(OK, client.watch(keys).get());
        assertEquals(OK, client.set(key2, gs(helloString)).get());
        setFoobarTransaction
                .set(key1.toString(), foobarString)
                .set(key2.toString(), foobarString)
                .set(key3.toString(), foobarString);
        assertNull(client.exec(setFoobarTransaction, true).get());
        assertNull(client.get(key1).get()); // Sanity check
        assertEquals(gs(helloString), client.get(key2).get());
        assertNull(client.get(key3).get());

        // Batch executes command successfully with a read command on the watch key before
        // transaction is executed.
        assertEquals(OK, client.watch(keys).get());
        assertEquals(gs(helloString), client.get(key2).get());
        assertArrayEquals(expectedExecResponse, client.exec(setFoobarTransaction, true).get());
        assertEquals(gs(foobarString), client.get(key1).get()); // Sanity check
        assertEquals(gs(foobarString), client.get(key2).get());
        assertEquals(gs(foobarString), client.get(key3).get());

        // Batch executes command successfully with unmodified watched keys
        assertEquals(OK, client.watch(keys).get());
        assertArrayEquals(expectedExecResponse, client.exec(setFoobarTransaction, true).get());
        assertEquals(gs(foobarString), client.get(key1).get()); // Sanity check
        assertEquals(gs(foobarString), client.get(key2).get());
        assertEquals(gs(foobarString), client.get(key3).get());

        // Batch executes command successfully with a modified watched key but is not in the
        // transaction.
        assertEquals(OK, client.watch(new GlideString[] {key4}).get());
        setHelloTransaction
                .set(key1.toString(), helloString)
                .set(key2.toString(), helloString)
                .set(key3.toString(), helloString);
        assertArrayEquals(expectedExecResponse, client.exec(setHelloTransaction, true).get());
        assertEquals(gs(helloString), client.get(key1).get()); // Sanity check
        assertEquals(gs(helloString), client.get(key2).get());
        assertEquals(gs(helloString), client.get(key3).get());

        // WATCH can not have an empty String array parameter
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.watch(new GlideString[] {}).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
    }

    @ParameterizedTest
    @MethodSource("getClients")
    @SneakyThrows
    public void unwatch(GlideClient client) {
        String key1 = "{key}-1" + UUID.randomUUID();
        String key2 = "{key}-2" + UUID.randomUUID();
        String foobarString = "foobar";
        String helloString = "hello";
        String[] keys = new String[] {key1, key2};
        Batch setFoobarTransaction = new Batch(true);
        String[] expectedExecResponse = new String[] {OK, OK};

        // UNWATCH returns OK when there no watched keys
        assertEquals(OK, client.unwatch().get());

        // Batch executes successfully after modifying a watched key then calling UNWATCH
        assertEquals(OK, client.watch(keys).get());
        assertEquals(OK, client.set(key2, helloString).get());
        assertEquals(OK, client.unwatch().get());
        setFoobarTransaction.set(key1, foobarString).set(key2, foobarString);
        assertArrayEquals(expectedExecResponse, client.exec(setFoobarTransaction, true).get());
        assertEquals(foobarString, client.get(key1).get());
        assertEquals(foobarString, client.get(key2).get());
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void sort_and_sortReadOnly(GlideClient client, boolean isAtomic) {
        Batch batch1 = new Batch(isAtomic);
        Batch batch2 = new Batch(isAtomic);
        var prefix = UUID.randomUUID();
        String genericKey1 = "{GenericKey}-1-" + prefix;
        String genericKey2 = "{GenericKey}-2-" + prefix;
        String[] ascendingListByAge = new String[] {"Bob", "Alice"};
        String[] descendingListByAge = new String[] {"Alice", "Bob"};

        batch1
                .hset(prefix + "user:1", Map.of("name", "Alice", "age", "30"))
                .hset(prefix + "user:2", Map.of("name", "Bob", "age", "25"))
                .lpush(genericKey1, new String[] {"2", "1"})
                .sort(
                        genericKey1,
                        SortOptions.builder()
                                .byPattern(prefix + "user:*->age")
                                .getPattern(prefix + "user:*->name")
                                .build())
                .sort(
                        genericKey1,
                        SortOptions.builder()
                                .orderBy(DESC)
                                .byPattern(prefix + "user:*->age")
                                .getPattern(prefix + "user:*->name")
                                .build())
                .sortStore(
                        genericKey1,
                        genericKey2,
                        SortOptions.builder()
                                .byPattern(prefix + "user:*->age")
                                .getPattern(prefix + "user:*->name")
                                .build())
                .lrange(genericKey2, 0, -1)
                .sortStore(
                        genericKey1,
                        genericKey2,
                        SortOptions.builder()
                                .orderBy(DESC)
                                .byPattern(prefix + "user:*->age")
                                .getPattern(prefix + "user:*->name")
                                .build())
                .lrange(genericKey2, 0, -1);

        var expectedResults =
                new Object[] {
                    2L, // hset(prefix + "user:1", ...);
                    2L, // hset(prefix + "user:2", ...);
                    2L, // lpush(genericKey1, ...);
                    ascendingListByAge, // sort(genericKey1, SortOptions)
                    descendingListByAge, // sort(genericKey1, SortOptions)
                    2L, // sortStore(genericKey1, genericKey2, SortOptions)
                    ascendingListByAge, // lrange(genericKey2, 0, -1)
                    2L, // sortStore(genericKey1, genericKey2, SortOptions)
                    descendingListByAge, // lrange(genericKey2, 0, -1)
                };

        assertArrayEquals(expectedResults, client.exec(batch1, true).get());

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            batch2
                    .sortReadOnly(
                            genericKey1,
                            SortOptions.builder()
                                    .byPattern(prefix + "user:*->age")
                                    .getPattern(prefix + "user:*->name")
                                    .build())
                    .sortReadOnly(
                            genericKey1,
                            SortOptions.builder()
                                    .orderBy(DESC)
                                    .byPattern(prefix + "user:*->age")
                                    .getPattern(prefix + "user:*->name")
                                    .build());

            expectedResults =
                    new Object[] {
                        ascendingListByAge, // sortReadOnly(genericKey1, SortOptions)
                        descendingListByAge, // sortReadOnly(genericKey1, SortOptions)
                    };

            assertArrayEquals(expectedResults, client.exec(batch2, true).get());
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    public void waitTest(GlideClient client, boolean isAtomic) {
        // setup
        String key = UUID.randomUUID().toString();
        long numreplicas = 1L;
        long timeout = 1000L;
        Batch batch = new Batch(isAtomic);

        batch.set(key, "value");
        batch.wait(numreplicas, timeout);

        Object[] results = client.exec(batch, true).get();
        Object[] expectedResult =
                new Object[] {
                    OK, // set(key,  "value")
                    0L, // wait(numreplicas, timeout)
                };
        assertEquals(expectedResult[0], results[0]);
        assertTrue((long) expectedResult[1] <= (long) results[1]);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    public void scan_test(GlideClient client, boolean isAtomic) {
        assertEquals(OK, client.flushall().get());
        // setup
        String key = UUID.randomUUID().toString();
        Map<String, String> msetMap = Map.of(key, UUID.randomUUID().toString());
        assertEquals(OK, client.mset(msetMap).get());

        String cursor = "0";
        Object[] keysFound = new Object[0];
        do {
            Batch batch = new Batch(isAtomic);
            batch.scan(cursor);
            Object[] results = client.exec(batch, true).get();
            cursor = (String) ((Object[]) results[0])[0];
            keysFound = ArrayUtils.addAll(keysFound, (Object[]) ((Object[]) results[0])[1]);
        } while (!cursor.equals("0"));

        assertTrue(ArrayUtils.contains(keysFound, key));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    public void scan_binary_test(GlideClient client, boolean isAtomic) {
        assertEquals(OK, client.flushall().get());
        // setup
        String key = UUID.randomUUID().toString();
        Map<String, String> msetMap = Map.of(key, UUID.randomUUID().toString());
        assertEquals(OK, client.mset(msetMap).get());

        GlideString cursor = gs("0");
        Object[] keysFound = new Object[0];
        do {
            Batch batch = new Batch(isAtomic).withBinaryOutput();
            batch.scan(cursor);
            Object[] results = client.exec(batch, true).get();
            cursor = (GlideString) ((Object[]) results[0])[0];
            keysFound = ArrayUtils.addAll(keysFound, (Object[]) ((Object[]) results[0])[1]);
        } while (!cursor.equals(gs("0")));

        assertTrue(ArrayUtils.contains(keysFound, gs(key)));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    public void scan_with_options_test(GlideClient client, boolean isAtomic) {
        assertEquals(OK, client.flushall().get());
        // setup
        Batch setupBatch = new Batch(isAtomic);

        Map<ScanOptions.ObjectType, String> typeKeys =
                Map.of(
                        STRING, "{string}-" + UUID.randomUUID(),
                        LIST, "{list}-" + UUID.randomUUID(),
                        SET, "{set}-" + UUID.randomUUID(),
                        ZSET, "{zset}-" + UUID.randomUUID(),
                        HASH, "{hash}-" + UUID.randomUUID(),
                        STREAM, "{stream}-" + UUID.randomUUID());

        setupBatch.set(typeKeys.get(STRING), UUID.randomUUID().toString());
        setupBatch.lpush(typeKeys.get(LIST), new String[] {UUID.randomUUID().toString()});
        setupBatch.sadd(typeKeys.get(SET), new String[] {UUID.randomUUID().toString()});
        setupBatch.zadd(typeKeys.get(ZSET), Map.of(UUID.randomUUID().toString(), 1.0));
        setupBatch.hset(
                typeKeys.get(HASH), Map.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        setupBatch.xadd(
                typeKeys.get(STREAM), Map.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        assertNotNull(client.exec(setupBatch, true).get());

        for (var type : ScanOptions.ObjectType.values()) {
            ScanOptions options = ScanOptions.builder().type(type).count(99L).build();

            String cursor = "0";
            Object[] keysFound = new Object[0];
            do {
                Batch batch = new Batch(isAtomic);
                batch.scan(cursor, options);
                Object[] results = client.exec(batch, true).get();
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
                Batch batch = new Batch(isAtomic);
                batch.scan(cursor, options);
                Object[] results = client.exec(batch, true).get();
                cursor = (String) ((Object[]) results[0])[0];
                keysFound = ArrayUtils.addAll(keysFound, (Object[]) ((Object[]) results[0])[1]);
            } while (!cursor.equals("0"));

            assertTrue(
                    ArrayUtils.contains(keysFound, typeKeys.get(type)),
                    "Unable to find " + typeKeys.get(type) + " in a scan by match pattern");
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    public void scan_binary_with_options_test(GlideClient client, boolean isAtomic) {
        assertEquals(OK, client.flushall().get());
        // setup
        Batch setupBatch = new Batch(isAtomic).withBinaryOutput();

        Map<ScanOptions.ObjectType, GlideString> typeKeys =
                Map.of(
                        STRING, gs("{string}-" + UUID.randomUUID()),
                        LIST, gs("{list}-" + UUID.randomUUID()),
                        SET, gs("{set}-" + UUID.randomUUID()),
                        ZSET, gs("{zset}-" + UUID.randomUUID()),
                        HASH, gs("{hash}-" + UUID.randomUUID()),
                        STREAM, gs("{stream}-" + UUID.randomUUID()));

        setupBatch.set(typeKeys.get(STRING), UUID.randomUUID().toString());
        setupBatch.lpush(typeKeys.get(LIST), new String[] {UUID.randomUUID().toString()});
        setupBatch.sadd(typeKeys.get(SET), new String[] {UUID.randomUUID().toString()});
        setupBatch.zadd(typeKeys.get(ZSET), Map.of(UUID.randomUUID().toString(), 1.0));
        setupBatch.hset(
                typeKeys.get(HASH), Map.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        setupBatch.xadd(
                typeKeys.get(STREAM), Map.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        assertNotNull(client.exec(setupBatch, true).get());

        final GlideString initialCursor = gs("0");

        for (var type : ScanOptions.ObjectType.values()) {
            ScanOptions options = ScanOptions.builder().type(type).count(99L).build();

            GlideString cursor = initialCursor;
            Object[] keysFound = new Object[0];
            do {
                Batch batch = new Batch(isAtomic).withBinaryOutput();
                batch.scan(cursor, options);
                Object[] results = client.exec(batch, true).get();
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
                Batch batch = new Batch(isAtomic).withBinaryOutput();
                batch.scan(cursor, options);
                Object[] results = client.exec(batch, true).get();
                cursor = (GlideString) ((Object[]) results[0])[0];
                keysFound = ArrayUtils.addAll(keysFound, (Object[]) ((Object[]) results[0])[1]);
            } while (!cursor.equals(initialCursor));

            assertTrue(
                    ArrayUtils.contains(keysFound, typeKeys.get(type)),
                    "Unable to find " + typeKeys.get(type) + " in a scan by match pattern");
        }
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void test_batch_dump_restore(GlideClient client, boolean isAtomic) {
        GlideString key1 = gs("{key}-1" + UUID.randomUUID());
        GlideString key2 = gs("{key}-2" + UUID.randomUUID());
        String value = UUID.randomUUID().toString();

        // Setup
        assertEquals(OK, client.set(key1, gs(value)).get());

        // Verify dump
        Batch batch = new Batch(isAtomic).withBinaryOutput().dump(key1);
        Object[] result = client.exec(batch, true).get();
        GlideString payload = (GlideString) result[0];

        // Verify restore
        batch = new Batch(isAtomic);
        batch.restore(key2, 0, payload.getBytes());
        batch.get(key2);
        Object[] response = client.exec(batch, true).get();
        assertEquals(OK, response[0]);
        assertEquals(value, response[1]);
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void test_batch_function_dump_restore(GlideClient client, boolean isAtomic) {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"));
        String libName = "mylib";
        String funcName = "myfun";
        String code = generateLuaLibCode(libName, Map.of(funcName, "return args[1]"), true);

        // Setup
        client.functionLoad(code, true).get();

        // Verify functionDump
        Batch batch = new Batch(isAtomic).withBinaryOutput().functionDump();
        Object[] result = client.exec(batch, true).get();
        GlideString payload = (GlideString) result[0];

        // Verify functionRestore
        batch = new Batch(isAtomic);
        batch.functionRestore(payload.getBytes(), FunctionRestorePolicy.REPLACE);
        Object[] response = client.exec(batch, true).get();
        assertEquals(OK, response[0]);
    }

    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    @SneakyThrows
    public void test_batch_xinfoStream(GlideClient client, boolean isAtomic) {
        Batch batch = new Batch(isAtomic);
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

        batch
                .xadd(streamKey, Map.of("field1", "value1"), StreamAddOptions.builder().id("0-1").build())
                .xinfoStream(streamKey)
                .xinfoStreamFull(streamKey);

        Object[] results = client.exec(batch, true).get();

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
                    "0-1", // xadd(streamKey, Map.of("field1", "value1"), ... .id("0-1").build());
                    expectedStreamInfo, // xinfoStream(streamKey)
                    expectedStreamFullInfo, // xinfoStreamFull(streamKey)
                },
                results);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClientsWithAtomic")
    public void binary_strings(GlideClient client, boolean isAtomic) {
        String key = UUID.randomUUID().toString();
        client.set(key, "_").get();
        // use dump to ensure that we have non-string convertible bytes
        var bytes = client.dump(gs(key)).get();

        var batch = new Batch(isAtomic).withBinaryOutput().set(gs(key), gs(bytes)).get(gs(key));

        var responses = client.exec(batch, true).get();

        assertDeepEquals(
                new Object[] {
                    OK, gs(bytes),
                },
                responses);
    }
}
