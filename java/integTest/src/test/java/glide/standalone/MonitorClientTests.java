/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.commonClientConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.GlideClient;
import glide.api.MonitorClient;
import glide.api.models.commands.MonitorMsg;
import glide.api.models.configuration.GlideClientConfiguration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
public class MonitorClientTests {

    private static final String CLIENT_SETINFO_MIN_VERSION = "7.2.0";

    @Test
    @SneakyThrows
    public void monitorReceivesCommands() {
        GlideClientConfiguration config = commonClientConfig().requestTimeout(5000).build();
        try (GlideClient client = GlideClient.createClient(config).get()) {

            String key = UUID.randomUUID().toString();
            String value = UUID.randomUUID().toString();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<MonitorMsg> received = new AtomicReference<>();

            try (MonitorClient monitorWithCb =
                    MonitorClient.create(
                            config,
                            msg -> {
                                if ("SET".equalsIgnoreCase(msg.getCommand())) {
                                    received.compareAndSet(null, msg);
                                    latch.countDown();
                                }
                            })) {

                Thread.sleep(200);
                client.set(key, value).get();

                assertTrue(latch.await(5, TimeUnit.SECONDS), "Expected a SET monitor message");
                assertNotNull(received.get());
                assertEquals(key, received.get().getArgs().get(0));
                assertEquals(value, received.get().getArgs().get(1));
            }
        }
    }

    @Test
    @SneakyThrows
    public void monitorQueueMode() {
        GlideClientConfiguration config = commonClientConfig().requestTimeout(5000).build();
        try (MonitorClient monitor = MonitorClient.create(config);
                GlideClient client = GlideClient.createClient(config).get()) {

            Thread.sleep(200);
            client.ping().get();

            MonitorMsg msg = monitor.getMonitorMessage(10000);
            assertNotNull(msg, "Expected a monitor message after PING");
        }
    }

    @Test
    @SneakyThrows
    public void monitorClose() {
        GlideClientConfiguration config = commonClientConfig().requestTimeout(5000).build();
        MonitorClient monitor = MonitorClient.create(config);

        assertFalse(monitor.isClosed());
        monitor.stop();
        assertTrue(monitor.isClosed());
    }

    @Test
    @SneakyThrows
    public void monitorStopIdempotent() {
        GlideClientConfiguration config = commonClientConfig().requestTimeout(5000).build();
        MonitorClient monitor = MonitorClient.create(config);

        monitor.stop();
        monitor.stop(); // Should not throw
    }

    @Test
    public void monitorRejectsNullConfig() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    // @NonNull on the config parameter throws NullPointerException for null input.
                    MonitorClient.create(null);
                });
    }

    @Test
    @SneakyThrows
    public void monitorMsgFieldTypes() {
        GlideClientConfiguration config = commonClientConfig().requestTimeout(5000).build();
        try (MonitorClient monitor = MonitorClient.create(config);
                GlideClient client = GlideClient.createClient(config).get()) {

            Thread.sleep(200);
            String key = UUID.randomUUID().toString();
            client.set(key, "value").get();

            // Wait for the SET to appear
            MonitorMsg msg = null;
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                MonitorMsg candidate = monitor.tryGetMonitorMessage();
                if (candidate != null && "SET".equalsIgnoreCase(candidate.getCommand())) {
                    msg = candidate;
                    break;
                }
                Thread.sleep(100);
            }

            assertNotNull(msg, "Expected a SET monitor message");
            assertTrue(msg.getTimestamp() > 0.0, "timestamp should be positive");
            assertTrue(msg.getDb() >= 0, "db should be non-negative");
            assertNotNull(msg.getClientAddr(), "clientAddr should not be null");
            assertNotNull(msg.getCommand(), "command should not be null");
            assertNotNull(msg.getArgs(), "args should not be null");
        }
    }

    @Test
    @SneakyThrows
    public void monitorReportsDefaultLibraryName() {
        assertMonitorReportsLibraryName(null, null, "GlideJava");
    }

    @Test
    @SneakyThrows
    public void monitorReportsConfiguredLibraryName() {
        assertMonitorReportsLibraryName("custom-client", null, "custom-client");
    }

    @Test
    @SneakyThrows
    public void monitorReportsClientInfoTag() {
        assertMonitorReportsLibraryName(null, "framework:1.2", "GlideJava(framework:1.2)");
    }

    @Test
    @SneakyThrows
    public void monitorReportsCombinedLibraryName() {
        assertMonitorReportsLibraryName(
                "custom-client", "framework:1.2", "custom-client(framework:1.2)");
    }

    private void assertMonitorReportsLibraryName(
            String libName, String clientInfoTag, String expectedLibName) throws Exception {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(CLIENT_SETINFO_MIN_VERSION),
                "Valkey version required >= " + CLIENT_SETINFO_MIN_VERSION);

        GlideClientConfiguration.GlideClientConfigurationBuilder<?, ?> monitorConfigBuilder =
                commonClientConfig().requestTimeout(5000);
        if (libName != null) {
            monitorConfigBuilder.libName(libName);
        }
        if (clientInfoTag != null) {
            monitorConfigBuilder.clientInfoTag(clientInfoTag);
        }
        GlideClientConfiguration monitorConfig = monitorConfigBuilder.build();
        GlideClientConfiguration observerConfig = commonClientConfig().requestTimeout(5000).build();

        try (GlideClient observer = GlideClient.createClient(observerConfig).get(5, TimeUnit.SECONDS)) {
            Set<String> baselineMonitorIds = getMonitorClientIds(clientList(observer));

            // A no-op callback prevents CLIENT LIST polling events from accumulating in the queue.
            try (MonitorClient monitor = MonitorClient.create(monitorConfig, ignored -> {})) {
                String monitorInfo = null;
                String latestClientList = "";
                long deadline = System.currentTimeMillis() + 5000;
                while (monitorInfo == null && System.currentTimeMillis() < deadline) {
                    latestClientList = clientList(observer);
                    monitorInfo = findNewMonitorClient(latestClientList, baselineMonitorIds, expectedLibName);
                    if (monitorInfo == null) {
                        Thread.sleep(50);
                    }
                }

                assertNotNull(
                        monitorInfo,
                        "Expected a new dedicated monitor connection, but CLIENT LIST returned: "
                                + latestClientList);
                assertTrue(
                        hasClientInfoField(monitorInfo, "lib-name", expectedLibName),
                        "Expected monitor lib-name="
                                + expectedLibName
                                + ", but CLIENT LIST returned: "
                                + monitorInfo);
            }
        }
    }

    private static String clientList(GlideClient observer) throws Exception {
        return (String)
                observer.customCommand(new String[] {"CLIENT", "LIST"}).get(5, TimeUnit.SECONDS);
    }

    private static Set<String> getMonitorClientIds(String clientList) {
        Set<String> monitorIds = new HashSet<>();
        for (String clientInfo : clientList.split("\\R")) {
            if (isMonitorClient(clientInfo)) {
                String id = getClientInfoField(clientInfo, "id");
                if (id != null) {
                    monitorIds.add(id);
                }
            }
        }
        return monitorIds;
    }

    private static String findNewMonitorClient(
            String clientList, Set<String> baselineMonitorIds, String expectedLibName) {
        for (String clientInfo : clientList.split("\\R")) {
            if (!isMonitorClient(clientInfo)) {
                continue;
            }
            String id = getClientInfoField(clientInfo, "id");
            if (id != null
                    && !baselineMonitorIds.contains(id)
                    && hasClientInfoField(clientInfo, "lib-name", expectedLibName)) {
                return clientInfo;
            }
        }
        return null;
    }

    private static boolean isMonitorClient(String clientInfo) {
        String command = getClientInfoField(clientInfo, "cmd");
        String flags = getClientInfoField(clientInfo, "flags");
        return "monitor".equalsIgnoreCase(command) || (flags != null && flags.contains("O"));
    }

    private static boolean hasClientInfoField(
            String clientInfo, String fieldName, String expectedValue) {
        return expectedValue.equals(getClientInfoField(clientInfo, fieldName));
    }

    private static String getClientInfoField(String clientInfo, String fieldName) {
        String prefix = fieldName + "=";
        for (String field : clientInfo.trim().split("\\s+")) {
            if (field.startsWith(prefix)) {
                return field.substring(prefix.length());
            }
        }
        return null;
    }
}
