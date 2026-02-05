/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.Java8Compat.repeat;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.TestUtilities.getRandomString;
import static glide.TestUtilities.isWindows;
import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Named.named;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.exceptions.RequestException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(35)
public class SharedClientTests {

    private static GlideClient standaloneClient = null;
    private static GlideClusterClient clusterClient = null;

    @Getter private static List<Arguments> clients;

    @SneakyThrows
    private static GlideClient createGlideClientWithTimeout() {
        return GlideClient.createClient(commonClientConfig().requestTimeout(10000).build()).get();
    }

    @SneakyThrows
    private static GlideClusterClient createGlideClusterClientWithTimeout() {
        return GlideClusterClient.createClient(
                        commonClusterClientConfig().requestTimeout(10000).build())
                .get();
    }

    @BeforeAll
    @SneakyThrows
    public static void init() {
        standaloneClient = GlideClient.createClient(commonClientConfig().build()).get();
        clusterClient =
                GlideClusterClient.createClient(commonClusterClientConfig().requestTimeout(10000).build())
                        .get();
        clients = Arrays.asList(Arguments.of(standaloneClient), Arguments.of(clusterClient));
    }

    @SneakyThrows
    public static Stream<Arguments> getTimeoutClients() {
        return Stream.of(
                Arguments.of(named("GlideClient", createGlideClientWithTimeout())),
                Arguments.of(named("GlideClusterClient", createGlideClusterClientWithTimeout())));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void validate_statistics(BaseClient client) {
        assertFalse(client.getStatistics().isEmpty());
        // we expect 10 items in the statistics map
        assertEquals(10, client.getStatistics().size());

        // Verify all expected keys are present
        Map<String, String> stats = client.getStatistics();
        assertTrue(stats.containsKey("total_connections"));
        assertTrue(stats.containsKey("total_clients"));
        assertTrue(stats.containsKey("total_values_compressed"));
        assertTrue(stats.containsKey("total_values_decompressed"));
        assertTrue(stats.containsKey("total_original_bytes"));
        assertTrue(stats.containsKey("total_bytes_compressed"));
        assertTrue(stats.containsKey("total_bytes_decompressed"));
        assertTrue(stats.containsKey("compression_skipped_count"));
        assertTrue(stats.containsKey("subscription_out_of_sync_count"));
        assertTrue(stats.containsKey("subscription_last_sync_timestamp"));

        // Verify subscription metrics are numeric
        assertDoesNotThrow(() -> Long.parseLong(stats.get("subscription_out_of_sync_count")));
        assertDoesNotThrow(() -> Long.parseLong(stats.get("subscription_last_sync_timestamp")));
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        standaloneClient.close();
        clusterClient.close();
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getTimeoutClients")
    public void send_and_receive_large_values(BaseClient client) {
        // Skip on macOS - the macOS tests run on self hosted VMs which have resource limits
        // making this test flaky with "no buffer space available" errors. See -
        // https://github.com/valkey-io/valkey-glide/issues/4902
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            return;
        }

        int length = 1 << 25; // 33mb
        String key = repeat("0", length);
        String value = repeat("0", length);

        assertEquals(length, key.length());
        assertEquals(length, value.length());
        assertEquals(OK, client.set(key, value).get());
        assertEquals(value, client.get(key).get());

        client.close();
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
                Arguments.of(createGlideClientWithTimeout(), 100),
                Arguments.of(createGlideClientWithTimeout(), 1 << 16),
                Arguments.of(createGlideClusterClientWithTimeout(), 100),
                Arguments.of(createGlideClusterClientWithTimeout(), 1 << 16));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("clientAndDataSize")
    public void client_can_handle_concurrent_workload(BaseClient client, int valueSize) {
        // Due to limited resources on Github Action when using a Windows runner with WSL, this test is
        // flaky.
        // It will be disabled.
        // TODO: Remove isWindows skip after flaky investigation
        // https://github.com/valkey-io/valkey-glide/issues/5210
        assumeTrue(!isWindows(), "Skip on Windows");
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
        client.close();
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
            assertInstanceOf(RequestException.class, e.getCause());
            assertTrue(e.getCause().getMessage().contains("maximum inflight requests"));
        }

        BaseClient cleanupClient;
        if (clusterMode) {
            cleanupClient =
                    GlideClient.createClient(
                                    commonClientConfig().inflightRequestsLimit(inflightRequestsLimit).build())
                            .get();
        } else {
            cleanupClient =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig().inflightRequestsLimit(inflightRequestsLimit).build())
                            .get();
        }

        for (int i = 0; i < inflightRequestsLimit; i++) {
            cleanupClient.lpush(keyName, new String[] {"val"}).get();
        }

        cleanupClient.close();
        testClient.close();
    }

    @Test
    @SneakyThrows
    public void test_cleanup_mechanism_works() {
        List<WeakReference<BaseClient>> clientRefs = new ArrayList<>();

        // Create and destroy multiple clients to test cleanup mechanism
        for (int i = 0; i < 5; i++) {
            GlideClient standaloneClient = GlideClient.createClient(commonClientConfig().build()).get();
            GlideClusterClient clusterClient =
                    GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

            clientRefs.add(new WeakReference<>(standaloneClient));
            clientRefs.add(new WeakReference<>(clusterClient));

            // Do simple operations to ensure clients are working
            assertEquals(OK, standaloneClient.set("cleanup_test_standalone_" + i, "value").get());
            assertEquals(OK, clusterClient.set("cleanup_test_cluster_" + i, "value").get());

            // Close clients
            standaloneClient.close();
            clusterClient.close();

            // Clear references
            standaloneClient = null;
            clusterClient = null;
        }

        // Force garbage collection multiple times
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(100);
        }

        // Wait for cleanup thread to process PhantomReferences
        Thread.sleep(1000);

        // Check that clients were garbage collected
        int collected = 0;
        for (WeakReference<BaseClient> ref : clientRefs) {
            if (ref.get() == null) {
                collected++;
            }
        }

        // At least 70% should be collected (allowing for GC timing variations)
        assertTrue(
                collected >= clientRefs.size() * 0.7,
                "Cleanup mechanism may not be working properly. Only "
                        + collected
                        + "/"
                        + clientRefs.size()
                        + " clients were garbage collected");
    }
}
