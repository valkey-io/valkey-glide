/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.TestUtilities.getRandomString;
import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.exceptions.RequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.SneakyThrows;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(35) // seconds
public class SharedClientTests {

    private static GlideClient standaloneClient = null;
    private static GlideClusterClient clusterClient = null;

    @Getter private static List<Arguments> clients;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        standaloneClient = GlideClient.createClient(commonClientConfig().build()).get();
        clusterClient =
                GlideClusterClient.createClient(commonClusterClientConfig().requestTimeout(10000).build())
                        .get();
        clients = List.of(Arguments.of(standaloneClient), Arguments.of(clusterClient));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void validate_statistics(BaseClient client) {
        assertFalse(client.getStatistics().isEmpty());
        // we expect 2 items in the statistics map
        assertEquals(2, client.getStatistics().size());
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
        int length = 1 << 25; // 33mb
        String key = "0".repeat(length);
        String value = "0".repeat(length);

        assertEquals(length, key.length());
        assertEquals(length, value.length());
        assertEquals(OK, client.set(key, value).get());
        assertEquals(value, client.get(key).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void send_and_receive_non_ascii_unicode(BaseClient client) {
        String key = "foo";
        String value = "\u05E9\u05DC\u05D5\u05DD hello \u6C49\u5B57";

        assertEquals(OK, client.set(key, value).get());
        assertEquals(value, client.get(key).get());
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

    private static Stream<Arguments> inflightRequestsLimitSizeAndClusterMode() {
        return Stream.of(
                Arguments.of(false, 5),
                Arguments.of(false, 100),
                Arguments.of(false, 1000),
                Arguments.of(true, 5),
                Arguments.of(true, 100),
                Arguments.of(true, 1000));
    }

    @SneakyThrows
    @ParameterizedTest()
    @MethodSource("inflightRequestsLimitSizeAndClusterMode")
    public void inflight_requests_limit(boolean clusterMode, int inflightRequestsLimit) {
        BaseClient testClient;
        String keyName = "nonexistkeylist" + RandomString.make(4);

        if (clusterMode) {
            testClient =
                    GlideClient.createClient(
                                    commonClientConfig().inflightRequestsLimit(inflightRequestsLimit).build())
                            .get();
        } else {
            testClient =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig().inflightRequestsLimit(inflightRequestsLimit).build())
                            .get();
        }

        // exercise
        List<CompletableFuture<String[]>> responses = new ArrayList<>();
        for (int i = 0; i < inflightRequestsLimit + 1; i++) {
            responses.add(testClient.blpop(new String[] {keyName}, 0));
        }

        // verify
        // Check that all requests except the last one are still pending
        for (int i = 0; i < inflightRequestsLimit; i++) {
            assertFalse(responses.get(i).isDone(), "Request " + i + " should still be pending");
        }

        // The last request should complete exceptionally
        try {
            responses.get(inflightRequestsLimit).get(100, TimeUnit.MILLISECONDS);
            fail("Expected the last request to throw an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof RequestException);
            assertTrue(e.getCause().getMessage().contains("maximum inflight requests"));
        }

        testClient.close();
    }
}
