/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.api.models.commands.InfoOptions.Section.CLUSTER;
import static glide.api.models.commands.InfoOptions.Section.CPU;
import static glide.api.models.commands.InfoOptions.Section.EVERYTHING;
import static glide.api.models.commands.InfoOptions.Section.MEMORY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.TestConfiguration;
import glide.api.RedisClient;
import glide.api.models.Ok;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CommandTests {
    private static RedisClient regularClient = null;

    private static final String KEY_NAME = "key";
    private static final String INITIAL_VALUE = "VALUE";
    private static final String ANOTHER_VALUE = "VALUE2";

    private static final List<String> DEFAULT_INFO_SECTIONS =
            List.of(
                    "Server",
                    "Clients",
                    "Memory",
                    "Persistence",
                    "Stats",
                    "Replication",
                    "CPU",
                    "Modules",
                    "Errorstats",
                    "Cluster",
                    "Keyspace");

    @BeforeAll
    @SneakyThrows
    public static void init() {
        regularClient =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(
                                                NodeAddress.builder().port(TestConfiguration.STANDALONE_PORTS[0]).build())
                                        .build())
                        .get(10, SECONDS);
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        regularClient.close();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        var data = regularClient.customCommand(new String[] {"info"}).get(10, SECONDS);
        assertTrue(((String) data).contains("# Stats"));
    }

    @Test
    @SneakyThrows
    public void info_without_options() {
        String data = regularClient.info().get(10, SECONDS);
        for (var section : DEFAULT_INFO_SECTIONS) {
            assertTrue(data.contains("# " + section), "Section " + section + " is missing");
        }
    }

    @Test
    @SneakyThrows
    public void info_with_multiple_options() {
        InfoOptions options =
                InfoOptions.builder().section(CLUSTER).section(CPU).section(MEMORY).build();
        String data = regularClient.info(options).get(10, SECONDS);
        for (var section : options.toArgs()) {
            assertTrue(
                    data.toLowerCase().contains("# " + section.toLowerCase()),
                    "Section " + section + " is missing");
        }
    }

    @Test
    @SneakyThrows
    public void set_and_get_without_options() {
        Ok ok = regularClient.set(KEY_NAME, INITIAL_VALUE).get(10, SECONDS);
        assertEquals(Ok.INSTANCE, ok);

        String data = regularClient.get(KEY_NAME).get(10, SECONDS);
        assertEquals(INITIAL_VALUE, data);
    }

    @Test
    @SneakyThrows
    public void get_missing_value() {
        var data = regularClient.get("invalid").get(10, SECONDS);
        assertNull(data);
    }

    @Test
    @SneakyThrows
    public void set_overwrite_value_and_returnOldValue_returns_string() {
        var ok = regularClient.set(KEY_NAME, INITIAL_VALUE).get(10, SECONDS);
        assertEquals(Ok.INSTANCE, ok);

        var options = SetOptions.builder().returnOldValue(true).build();
        var data = regularClient.set(KEY_NAME, ANOTHER_VALUE, options).get(10, SECONDS);
        assertEquals(INITIAL_VALUE, data);
    }

    @Test
    @SneakyThrows
    public void set_missing_value_and_returnOldValue_is_null() {
        var ok = regularClient.set(KEY_NAME, INITIAL_VALUE).get(10, SECONDS);
        assertEquals(Ok.INSTANCE, ok);

        var options = SetOptions.builder().returnOldValue(true).build();
        var data = regularClient.set("another", ANOTHER_VALUE, options).get(10, SECONDS);
        assertNull(data);
    }

    @Test
    @SneakyThrows
    public void ping() {
        var data = regularClient.ping().get(10, SECONDS);
        assertEquals("PONG", data);
    }

    @Test
    @SneakyThrows
    public void ping_with_message() {
        var data = regularClient.ping("H3LL0").get(10, SECONDS);
        assertEquals("H3LL0", data);
    }
}
