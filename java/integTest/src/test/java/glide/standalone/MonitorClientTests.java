/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestUtilities.commonClientConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClient;
import glide.api.MonitorClient;
import glide.api.models.commands.MonitorMsg;
import glide.api.models.configuration.GlideClientConfiguration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
public class MonitorClientTests {

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
}
