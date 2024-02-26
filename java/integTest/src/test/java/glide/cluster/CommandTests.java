/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.CLUSTER_PORTS;
import static glide.TestConfiguration.REDIS_VERSION;
import static glide.api.models.commands.InfoOptions.Section.CLIENTS;
import static glide.api.models.commands.InfoOptions.Section.CLUSTER;
import static glide.api.models.commands.InfoOptions.Section.COMMANDSTATS;
import static glide.api.models.commands.InfoOptions.Section.CPU;
import static glide.api.models.commands.InfoOptions.Section.EVERYTHING;
import static glide.api.models.commands.InfoOptions.Section.MEMORY;
import static glide.api.models.commands.InfoOptions.Section.REPLICATION;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.RANDOM;
import static glide.api.models.configuration.RequestRoutingConfiguration.SlotType.PRIMARY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.RedisClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
public class CommandTests {

    private static RedisClusterClient clusterClient = null;

    private static final String INITIAL_VALUE = "VALUE";

    public static final List<String> DEFAULT_INFO_SECTIONS =
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
    public static final List<String> EVERYTHING_INFO_SECTIONS =
            REDIS_VERSION.feature() >= 7
                    // Latencystats was added in redis 7
                    ? List.of(
                            "Server",
                            "Clients",
                            "Memory",
                            "Persistence",
                            "Stats",
                            "Replication",
                            "CPU",
                            "Modules",
                            "Commandstats",
                            "Errorstats",
                            "Latencystats",
                            "Cluster",
                            "Keyspace")
                    : List.of(
                            "Server",
                            "Clients",
                            "Memory",
                            "Persistence",
                            "Stats",
                            "Replication",
                            "CPU",
                            "Modules",
                            "Commandstats",
                            "Errorstats",
                            "Cluster",
                            "Keyspace");

    @BeforeAll
    @SneakyThrows
    public static void init() {
        clusterClient =
                RedisClusterClient.CreateClient(
                                RedisClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(CLUSTER_PORTS[0]).build())
                                        .requestTimeout(5000)
                                        .build())
                        .get();
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        clusterClient.close();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        ClusterValue<Object> data = clusterClient.customCommand(new String[] {"info"}).get();
        assertTrue(data.hasMultiData());
        for (Object info : data.getMultiValue().values()) {
            assertTrue(((String) info).contains("# Stats"));
        }
    }

    @Test
    @SneakyThrows
    public void custom_command_ping() {
        ClusterValue<Object> data =
                clusterClient.customCommand(new String[] {"ping"}).get(10, TimeUnit.SECONDS);
        assertEquals("PONG", data.getSingleValue());
    }

    @Test
    @SneakyThrows
    public void custom_command_del_returns_a_number() {
        String key = "custom_command_del_returns_a_number";
        clusterClient.set(key, INITIAL_VALUE).get();
        var del = clusterClient.customCommand(new String[] {"DEL", key}).get();
        assertEquals(1L, del.getSingleValue());
        var data = clusterClient.get(key).get();
        assertNull(data);
    }

    @Test
    @SneakyThrows
    public void ping() {
        String data = clusterClient.ping().get();
        assertEquals("PONG", data);
    }

    @Test
    @SneakyThrows
    public void ping_with_message() {
        String data = clusterClient.ping("H3LL0").get();
        assertEquals("H3LL0", data);
    }

    @Test
    @SneakyThrows
    public void ping_with_route() {
        String data = clusterClient.ping(ALL_NODES).get();
        assertEquals("PONG", data);
    }

    @Test
    @SneakyThrows
    public void ping_with_message_with_route() {
        String data = clusterClient.ping("H3LL0", ALL_PRIMARIES).get();
        assertEquals("H3LL0", data);
    }

    @Test
    @SneakyThrows
    public void info_without_options() {
        ClusterValue<String> data = clusterClient.info().get();
        assertTrue(data.hasMultiData());
        for (String info : data.getMultiValue().values()) {
            for (String section : DEFAULT_INFO_SECTIONS) {
                assertTrue(info.contains("# " + section), "Section " + section + " is missing");
            }
        }
    }

    @Test
    @SneakyThrows
    public void info_with_single_node_route() {
        ClusterValue<String> data = clusterClient.info(RANDOM).get();
        assertTrue(data.hasSingleData());
        String infoData = data.getSingleValue();
        for (String section : DEFAULT_INFO_SECTIONS) {
            assertTrue(infoData.contains("# " + section), "Section " + section + " is missing");
        }
    }

    @Test
    @SneakyThrows
    public void info_with_multi_node_route() {
        ClusterValue<String> data = clusterClient.info(ALL_NODES).get();
        assertTrue(data.hasMultiData());
        for (String info : data.getMultiValue().values()) {
            for (String section : DEFAULT_INFO_SECTIONS) {
                assertTrue(info.contains("# " + section), "Section " + section + " is missing");
            }
        }
    }

    @Test
    @SneakyThrows
    public void info_with_multiple_options() {
        InfoOptions.InfoOptionsBuilder builder = InfoOptions.builder().section(CLUSTER);
        if (REDIS_VERSION.feature() >= 7) {
            builder.section(CPU).section(MEMORY);
        }
        InfoOptions options = builder.build();
        ClusterValue<String> data = clusterClient.info(options).get();
        for (String info : data.getMultiValue().values()) {
            for (String section : options.toArgs()) {
                assertTrue(
                        info.toLowerCase().contains("# " + section.toLowerCase()),
                        "Section " + section + " is missing");
            }
        }
    }

    @Test
    @SneakyThrows
    public void info_with_everything_option() {
        InfoOptions options = InfoOptions.builder().section(EVERYTHING).build();
        ClusterValue<String> data = clusterClient.info(options).get();
        assertTrue(data.hasMultiData());
        for (String info : data.getMultiValue().values()) {
            for (String section : EVERYTHING_INFO_SECTIONS) {
                assertTrue(info.contains("# " + section), "Section " + section + " is missing");
            }
        }
    }

    @Test
    @SneakyThrows
    public void info_with_single_node_route_and_options() {
        ClusterValue<Object> slotData =
                clusterClient.customCommand(new String[] {"cluster", "slots"}).get();

        // Nested Object arrays like
        // 1) 1) (integer) 0
        //    2) (integer) 5460
        //    3) 1) "127.0.0.1"
        //       2) (integer) 7000
        //       3) "92d73b6eb847604b63c7f7cbbf39b148acdd1318"
        //       4) (empty array)
        // Extracting first slot key
        var slotKey =
                (String) ((Object[]) ((Object[]) ((Object[]) slotData.getSingleValue())[0])[2])[2];

        InfoOptions.InfoOptionsBuilder builder = InfoOptions.builder().section(CLIENTS);
        if (REDIS_VERSION.feature() >= 7) {
            builder.section(COMMANDSTATS).section(REPLICATION);
        }
        InfoOptions options = builder.build();
        SlotKeyRoute routing = new SlotKeyRoute(slotKey, PRIMARY);
        ClusterValue<String> data = clusterClient.info(options, routing).get();

        for (String section : options.toArgs()) {
            assertTrue(
                    data.getSingleValue().toLowerCase().contains("# " + section.toLowerCase()),
                    "Section " + section + " is missing");
        }
    }

    @Test
    @SneakyThrows
    public void info_with_multi_node_route_and_options() {
        InfoOptions.InfoOptionsBuilder builder = InfoOptions.builder().section(CLIENTS);
        if (REDIS_VERSION.feature() >= 7) {
            builder.section(COMMANDSTATS).section(REPLICATION);
        }
        InfoOptions options = builder.build();
        ClusterValue<String> data = clusterClient.info(options, ALL_NODES).get();

        for (String info : data.getMultiValue().values()) {
            for (String section : options.toArgs()) {
                assertTrue(
                        info.toLowerCase().contains("# " + section.toLowerCase()),
                        "Section " + section + " is missing");
            }
        }
    }
}
