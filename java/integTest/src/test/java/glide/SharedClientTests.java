/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.TestUtilities.getRandomString;
import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.BaseClient;
import glide.api.RedisClient;
import glide.api.RedisClusterClient;
import glide.api.models.GlideString;
import glide.api.models.commands.SetOptions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// @Timeout(25) // seconds
public class SharedClientTests {

    private static RedisClient standaloneClient = null;
    private static RedisClusterClient clusterClient = null;

    @Getter private static List<Arguments> clients;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        standaloneClient = RedisClient.CreateClient(commonClientConfig().build()).get();
        clusterClient =
                RedisClusterClient.CreateClient(commonClusterClientConfig().requestTimeout(10000).build())
                        .get();

        clients = List.of(Arguments.of(standaloneClient), Arguments.of(clusterClient));
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        standaloneClient.close();
        clusterClient.close();
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void send_and_receive_large_values(BaseClient client) {
        int length = 1 << 16;
        String key = getRandomString(length);
        String value = getRandomString(length);

        assertEquals(length, key.length());
        assertEquals(length, value.length());
        assertEquals(OK, client.set(key, value).get());
        assertEquals(value, client.get(key).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void send_and_receive_non_ascii_unicode(BaseClient client) {
        GlideString key = GlideString.of("foo");
        GlideString value1 = GlideString.of("\u05E9\u05DC\u05D5\u05DD hello \u6C49\u5B57");

        assertEquals(OK, client.setBinary(key, value1).get());
        assertEquals(value1, client.getBinary(key).get());

        // Test all possible 1 byte symbols (some of them are ASCII, some of them - not)
        byte[] arr = new byte[255];
        for (var i = 0; i < arr.length; i++) {
            arr[i] = (byte) i;
        }
        GlideString value2 = GlideString.of(arr);

        assertEquals(OK, client.setBinary(key, value2).get());
        assertEquals(value2, client.getBinary(key).get());

        // Test all possible 2 byte symbols (most of them are UTF-8, some of them - not)
        StringBuilder value3builder = new StringBuilder();
        for (var i = 0; i < 0xFFFF; i++) {
            // code points in the range of 0xD800 to 0xDFFF are removed from the Unicode character set
            // redis doesn't store them (replaced by `?` signs = 0x3F)
            if (i < 0xD800 || i > 0xDFFF) {
                value3builder.append(Character.toChars(i));
            }
        }
        GlideString value3 = GlideString.of(value3builder.toString());

        assertEquals(OK, client.setBinary(key, value3).get());
        var res = client.getBinary(key).get();
        assertEquals(value3, res);

        // test set with options
        var options = SetOptions.builder().returnOldValue(true).build();
        assertEquals(value3, client.setBinary(key, value2, options).get());
        assertEquals(value2, client.getBinary(key).get());

        // test mset
        var data =
                Map.of(
                        GlideString.of("unicode1"),
                        value1,
                        GlideString.of("unicode2"),
                        value2,
                        GlideString.of("unicode3"),
                        value3);
        assertEquals(OK, client.msetBinary(data).get());

        // test mget
        assertArrayEquals(
                data.values().toArray(GlideString[]::new),
                client.mgetBinary(data.keySet().toArray(new GlideString[0])).get());

        // test dump-restore
        // TODO replace with commands when implemented
        // var dumpCmd = new String[] { "DUMP", "unicode2" };
        var dumpCmd = new GlideString[] {GlideString.of("DUMP"), key};
        if (client instanceof RedisClient) {
            var dump = ((RedisClient) client).customCommandBinary(dumpCmd).get();
            var restoreCmd =
                    new GlideString[] {
                        GlideString.of("RESTORE"),
                        GlideString.of("unicode4"),
                        GlideString.of("0"),
                        (GlideString) dump,
                        GlideString.of("REPLACE")
                    };
            assertEquals(
                    GlideString.of(OK), ((RedisClient) client).customCommandBinary(restoreCmd).get());
        } else {
            var dump = ((RedisClusterClient) client).customCommandBinary(dumpCmd).get().getSingleValue();
            var restoreCmd =
                    new GlideString[] {
                        GlideString.of("RESTORE"),
                        GlideString.of("unicode4"),
                        GlideString.of("0"),
                        (GlideString) dump,
                        GlideString.of("REPLACE")
                    };
            assertEquals(
                    GlideString.of(OK),
                    ((RedisClusterClient) client).customCommandBinary(restoreCmd).get().getSingleValue());
        }
        // compare values
        assertEquals(
                client.getBinary(GlideString.of("unicode4")).get(),
                client.getBinary(GlideString.of("unicode2")).get());
    }

    private static Stream<Arguments> clientAndDataSize() {
        return Stream.of(
                Arguments.of(standaloneClient, 100),
                Arguments.of(standaloneClient, 1 << 16),
                Arguments.of(clusterClient, 100),
                Arguments.of(clusterClient, 1 << 16));
    }

    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("clientAndDataSize")
    public void client_can_handle_concurrent_workload(BaseClient client, int valueSize) {
        ExecutorService executorService = Executors.newCachedThreadPool();
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[100];

        for (int i = 0; i < 100; i++) {
            futures[i] =
                    CompletableFuture.runAsync(
                            () -> {
                                String key = getRandomString(valueSize);
                                String value = getRandomString(valueSize);
                                try {
                                    assertEquals(OK, client.set(key, value).get());
                                    assertEquals(value, client.get(key).get());
                                } catch (InterruptedException | ExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            executorService);
        }

        CompletableFuture.allOf(futures).join();

        executorService.shutdown();
    }
}
