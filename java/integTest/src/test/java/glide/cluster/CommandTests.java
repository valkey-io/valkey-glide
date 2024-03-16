/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.CLUSTER_PORTS;
import static glide.TestConfiguration.REDIS_VERSION;
import static glide.TestUtilities.getFirstEntryFromMultiValue;
import static glide.TestUtilities.getValueFromInfo;
import static glide.api.BaseClient.OK;
import static glide.api.models.commands.InfoOptions.Section.CLIENTS;
import static glide.api.models.commands.InfoOptions.Section.CLUSTER;
import static glide.api.models.commands.InfoOptions.Section.COMMANDSTATS;
import static glide.api.models.commands.InfoOptions.Section.CPU;
import static glide.api.models.commands.InfoOptions.Section.EVERYTHING;
import static glide.api.models.commands.InfoOptions.Section.MEMORY;
import static glide.api.models.commands.InfoOptions.Section.REPLICATION;
import static glide.api.models.commands.InfoOptions.Section.STATS;
import static glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.RANDOM;
import static glide.api.models.configuration.RequestRoutingConfiguration.SlotType.PRIMARY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.RedisClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import glide.api.models.exceptions.RequestException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
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
            REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")
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
        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
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
        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
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
        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
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

    @Test
    @SneakyThrows
    public void clientId() {
        var id = clusterClient.clientId().get();
        assertTrue(id > 0);
    }

    @Test
    @SneakyThrows
    public void clientId_with_single_node_route() {
        var data = clusterClient.clientId(RANDOM).get();
        assertTrue(data.getSingleValue() > 0L);
    }

    @Test
    @SneakyThrows
    public void clientId_with_multi_node_route() {
        var data = clusterClient.clientId(ALL_NODES).get();
        data.getMultiValue().values().forEach(id -> assertTrue(id > 0));
    }

    @Test
    @SneakyThrows
    public void clientGetName() {
        // TODO replace with the corresponding command once implemented
        clusterClient.customCommand(new String[] {"client", "setname", "clientGetName"}).get();

        var name = clusterClient.clientGetName().get();

        assertEquals("clientGetName", name);
    }

    @Test
    @SneakyThrows
    public void clientGetName_with_single_node_route() {
        // TODO replace with the corresponding command once implemented
        clusterClient
                .customCommand(
                        new String[] {"client", "setname", "clientGetName_with_single_node_route"}, ALL_NODES)
                .get();

        var name = clusterClient.clientGetName(RANDOM).get();

        assertEquals("clientGetName_with_single_node_route", name.getSingleValue());
    }

    @Test
    @SneakyThrows
    public void clientGetName_with_multi_node_route() {
        // TODO replace with the corresponding command once implemented
        clusterClient
                .customCommand(
                        new String[] {"client", "setname", "clientGetName_with_multi_node_route"}, ALL_NODES)
                .get();

        var name = clusterClient.clientGetName(ALL_NODES).get();

        assertEquals("clientGetName_with_multi_node_route", getFirstEntryFromMultiValue(name));
    }

    @Test
    @SneakyThrows
    public void config_reset_stat() {
        var data = clusterClient.info(InfoOptions.builder().section(STATS).build()).get();
        String firstNodeInfo = getFirstEntryFromMultiValue(data);
        int value_before = getValueFromInfo(firstNodeInfo, "total_net_input_bytes");

        var result = clusterClient.configResetStat().get();
        assertEquals(OK, result);

        data = clusterClient.info(InfoOptions.builder().section(STATS).build()).get();
        firstNodeInfo = getFirstEntryFromMultiValue(data);
        int value_after = getValueFromInfo(firstNodeInfo, "total_net_input_bytes");
        assertTrue(value_after < value_before);
    }

    @Test
    @SneakyThrows
    public void config_rewrite_non_existent_config_file() {
        // The setup for the Integration Tests server does not include a configuration file for Redis.
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> clusterClient.configRewrite().get());
        assertTrue(executionException.getCause() instanceof RequestException);
    }

    // returns the line that contains the word "myself", up to that point. This is done because the
    // values after it might change with time.
    private String cleanResult(String value) {
        return Arrays.stream(value.split("\n"))
                .filter(line -> line.contains("myself"))
                .findFirst()
                .orElse(null);
    }

    @Test
    @SneakyThrows
    public void cluster_route_by_address_reaches_correct_node() {
        // Masks timestamps in the cluster nodes output to avoid flakiness due to dynamic values.
        String initialNode =
                cleanResult(
                        (String)
                                clusterClient
                                        .customCommand(new String[] {"cluster", "nodes"}, RANDOM)
                                        .get()
                                        .getSingleValue());

        String host = initialNode.split(" ")[1].split("@")[0];
        assertNotNull(host);

        String specifiedClusterNode1 =
                cleanResult(
                        (String)
                                clusterClient
                                        .customCommand(new String[] {"cluster", "nodes"}, new ByAddressRoute(host))
                                        .get()
                                        .getSingleValue());
        assertEquals(initialNode, specifiedClusterNode1);

        String[] splitHost = host.split(":");
        String specifiedClusterNode2 =
                cleanResult(
                        (String)
                                clusterClient
                                        .customCommand(
                                                new String[] {"cluster", "nodes"},
                                                new ByAddressRoute(splitHost[0], Integer.parseInt(splitHost[1])))
                                        .get()
                                        .getSingleValue());
        assertEquals(initialNode, specifiedClusterNode2);
    }

    @Test
    @SneakyThrows
    public void cluster_fail_routing_by_address_if_no_port_is_provided() {
        assertThrows(RequestException.class, () -> clusterClient.info(new ByAddressRoute("foo")).get());
    }
}
