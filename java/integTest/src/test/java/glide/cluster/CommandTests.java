/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.CLUSTER_PORTS;
import static glide.TestConfiguration.REDIS_VERSION;
import static glide.TestUtilities.assertDeepEquals;
import static glide.TestUtilities.checkFunctionListResponse;
import static glide.TestUtilities.generateLuaLibCode;
import static glide.TestUtilities.getFirstEntryFromMultiValue;
import static glide.TestUtilities.getValueFromInfo;
import static glide.TestUtilities.parseInfoResponseToMap;
import static glide.api.BaseClient.OK;
import static glide.api.models.commands.FlushMode.ASYNC;
import static glide.api.models.commands.FlushMode.SYNC;
import static glide.api.models.commands.InfoOptions.Section.CLIENTS;
import static glide.api.models.commands.InfoOptions.Section.CLUSTER;
import static glide.api.models.commands.InfoOptions.Section.COMMANDSTATS;
import static glide.api.models.commands.InfoOptions.Section.CPU;
import static glide.api.models.commands.InfoOptions.Section.EVERYTHING;
import static glide.api.models.commands.InfoOptions.Section.MEMORY;
import static glide.api.models.commands.InfoOptions.Section.REPLICATION;
import static glide.api.models.commands.InfoOptions.Section.SERVER;
import static glide.api.models.commands.InfoOptions.Section.STATS;
import static glide.api.models.commands.ScoreFilter.MAX;
import static glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static glide.api.models.configuration.RequestRoutingConfiguration.SlotType.PRIMARY;
import static glide.api.models.configuration.RequestRoutingConfiguration.SlotType.REPLICA;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.RedisClusterClient;
import glide.api.models.ClusterTransaction;
import glide.api.models.ClusterValue;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.ListDirection;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.bitmap.BitwiseOperation;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import glide.api.models.exceptions.RedisException;
import glide.api.models.exceptions.RequestException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(10) // seconds
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
        ClusterValue<Object> data = clusterClient.customCommand(new String[] {"ping"}).get();
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
        var info = clusterClient.info(InfoOptions.builder().section(SERVER).build(), RANDOM).get();
        var configFile = parseInfoResponseToMap(info.getSingleValue()).get("config_file");

        if (configFile.isEmpty()) {
            ExecutionException executionException =
                    assertThrows(ExecutionException.class, () -> clusterClient.configRewrite().get());
            assertTrue(executionException.getCause() instanceof RequestException);
        } else {
            assertEquals(OK, clusterClient.configRewrite().get());
        }
    }

    // returns the line that contains the word "myself", up to that point. This is done because the
    // values after it might change with time.
    private String cleanResult(String value) {
        return Arrays.stream(value.split("\n"))
                .filter(line -> line.contains("myself"))
                .findFirst()
                .map(line -> line.substring(0, line.indexOf("myself") + "myself".length()))
                .orElse(null);
    }

    @Test
    @SneakyThrows
    public void configGet_with_no_args_returns_error() {
        var exception =
                assertThrows(
                        ExecutionException.class, () -> clusterClient.configGet(new String[] {}).get());
        assertTrue(exception.getCause() instanceof RedisException);
    }

    @Test
    @SneakyThrows
    public void configGet_with_wildcard() {
        var data = clusterClient.configGet(new String[] {"*file"}).get();
        assertTrue(data.size() > 5);
        assertTrue(data.containsKey("pidfile"));
        assertTrue(data.containsKey("logfile"));
    }

    @Test
    @SneakyThrows
    public void configGet_with_multiple_params() {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");
        var data = clusterClient.configGet(new String[] {"pidfile", "logfile"}).get();
        assertAll(
                () -> assertEquals(2, data.size()),
                () -> assertTrue(data.containsKey("pidfile")),
                () -> assertTrue(data.containsKey("logfile")));
    }

    @Test
    @SneakyThrows
    public void configGet_with_wildcard_and_multi_node_route() {
        var data = clusterClient.configGet(new String[] {"*file"}, ALL_PRIMARIES).get();
        assertTrue(data.hasMultiData());
        assertTrue(data.getMultiValue().size() > 1);
        Map<String, String> config =
                data.getMultiValue().get(data.getMultiValue().keySet().toArray(String[]::new)[0]);
        assertAll(
                () -> assertTrue(config.size() > 5),
                () -> assertTrue(config.containsKey("pidfile")),
                () -> assertTrue(config.containsKey("logfile")));
    }

    @Test
    @SneakyThrows
    public void configSet_a_parameter() {
        var oldValue = clusterClient.configGet(new String[] {"maxclients"}).get().get("maxclients");

        var response = clusterClient.configSet(Map.of("maxclients", "42")).get();
        assertEquals(OK, response);
        var newValue = clusterClient.configGet(new String[] {"maxclients"}).get();
        assertEquals("42", newValue.get("maxclients"));

        response = clusterClient.configSet(Map.of("maxclients", oldValue)).get();
        assertEquals(OK, response);
    }

    @Test
    @SneakyThrows
    public void configSet_a_parameter_with_routing() {
        var oldValue =
                clusterClient
                        .configGet(new String[] {"cluster-node-timeout"})
                        .get()
                        .get("cluster-node-timeout");

        var response =
                clusterClient.configSet(Map.of("cluster-node-timeout", "100500"), ALL_NODES).get();
        assertEquals(OK, response);

        var newValue = clusterClient.configGet(new String[] {"cluster-node-timeout"}).get();
        assertEquals("100500", newValue.get("cluster-node-timeout"));

        response = clusterClient.configSet(Map.of("cluster-node-timeout", oldValue), ALL_NODES).get();
        assertEquals(OK, response);
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

    @SneakyThrows
    @Test
    public void echo() {
        String message = "GLIDE";
        String response = clusterClient.echo(message).get();
        assertEquals(message, response);
    }

    @SneakyThrows
    @Test
    public void echo_with_route() {
        String message = "GLIDE";

        String singlePayload = clusterClient.echo(message, RANDOM).get().getSingleValue();
        assertEquals(message, singlePayload);

        Map<String, String> multiPayload = clusterClient.echo(message, ALL_NODES).get().getMultiValue();
        multiPayload.forEach((key, value) -> assertEquals(message, value));
    }

    @Test
    @SneakyThrows
    public void time() {
        // Take the time now, convert to 10 digits and subtract 1 second
        long now = Instant.now().getEpochSecond() - 1L;
        String[] result = clusterClient.time().get();
        assertEquals(2, result.length);
        assertTrue(
                Long.parseLong(result[0]) > now,
                "Time() result (" + result[0] + ") should be greater than now (" + now + ")");
        assertTrue(Long.parseLong(result[1]) < 1000000);
    }

    @Test
    @SneakyThrows
    public void time_with_route() {
        // Take the time now, convert to 10 digits and subtract 1 second
        long now = Instant.now().getEpochSecond() - 1L;

        ClusterValue<String[]> result = clusterClient.time(ALL_PRIMARIES).get();
        assertTrue(result.hasMultiData());
        assertTrue(result.getMultiValue().size() > 1);

        // check the first node's server time
        Object[] serverTime =
                result.getMultiValue().get(result.getMultiValue().keySet().toArray(String[]::new)[0]);

        assertEquals(2, serverTime.length);
        assertTrue(
                Long.parseLong((String) serverTime[0]) > now,
                "Time() result (" + serverTime[0] + ") should be greater than now (" + now + ")");
        assertTrue(Long.parseLong((String) serverTime[1]) < 1000000);
    }

    @Test
    @SneakyThrows
    public void lastsave() {
        long result = clusterClient.lastsave().get();
        var yesterday = Instant.now().minus(1, ChronoUnit.DAYS);

        assertTrue(Instant.ofEpochSecond(result).isAfter(yesterday));

        ClusterValue<Long> data = clusterClient.lastsave(ALL_NODES).get();
        for (var value : data.getMultiValue().values()) {
            assertTrue(Instant.ofEpochSecond(value).isAfter(yesterday));
        }
    }

    @Test
    @SneakyThrows
    public void lolwut_lolwut() {
        var response = clusterClient.lolwut().get();
        System.out.printf("%nLOLWUT cluster client standard response%n%s%n", response);
        assertTrue(response.contains("Redis ver. " + REDIS_VERSION));

        response = clusterClient.lolwut(new int[] {50, 20}).get();
        System.out.printf(
                "%nLOLWUT cluster client standard response with params 50 20%n%s%n", response);
        assertTrue(response.contains("Redis ver. " + REDIS_VERSION));

        response = clusterClient.lolwut(6).get();
        System.out.printf("%nLOLWUT cluster client ver 6 response%n%s%n", response);
        assertTrue(response.contains("Redis ver. " + REDIS_VERSION));

        response = clusterClient.lolwut(5, new int[] {30, 4, 4}).get();
        System.out.printf("%nLOLWUT cluster client ver 5 response with params 30 4 4%n%s%n", response);
        assertTrue(response.contains("Redis ver. " + REDIS_VERSION));

        var clusterResponse = clusterClient.lolwut(ALL_NODES).get();
        for (var nodeResponse : clusterResponse.getMultiValue().values()) {
            assertTrue(nodeResponse.contains("Redis ver. " + REDIS_VERSION));
        }

        clusterResponse = clusterClient.lolwut(new int[] {10, 20}, ALL_NODES).get();
        for (var nodeResponse : clusterResponse.getMultiValue().values()) {
            assertTrue(nodeResponse.contains("Redis ver. " + REDIS_VERSION));
        }

        clusterResponse = clusterClient.lolwut(2, RANDOM).get();
        assertTrue(clusterResponse.getSingleValue().contains("Redis ver. " + REDIS_VERSION));

        clusterResponse = clusterClient.lolwut(2, new int[] {10, 20}, RANDOM).get();
        assertTrue(clusterResponse.getSingleValue().contains("Redis ver. " + REDIS_VERSION));
    }

    @Test
    @SneakyThrows
    public void dbsize() {
        assertEquals(OK, clusterClient.flushall().get());
        // dbsize should be 0 after flushall() because all keys have been deleted
        assertEquals(0L, clusterClient.dbsize().get());

        int numKeys = 10;
        for (int i = 0; i < numKeys; i++) {
            assertEquals(OK, clusterClient.set(UUID.randomUUID().toString(), "foo").get());
        }
        assertEquals(10L, clusterClient.dbsize(ALL_PRIMARIES).get());

        // test dbsize with routing - flush the database first to ensure the set() call is directed to a
        // node with 0 keys.
        assertEquals(OK, clusterClient.flushall().get());
        assertEquals(0L, clusterClient.dbsize().get());
        String key = UUID.randomUUID().toString();
        assertEquals(OK, clusterClient.set(key, "foo").get());
        assertEquals(1L, clusterClient.dbsize(new SlotKeyRoute(key, PRIMARY)).get());
    }

    @Test
    @SneakyThrows
    public void objectFreq() {
        String key = UUID.randomUUID().toString();
        String maxmemoryPolicy = "maxmemory-policy";
        String oldPolicy =
                clusterClient.configGet(new String[] {maxmemoryPolicy}).get().get(maxmemoryPolicy);
        try {
            assertEquals(OK, clusterClient.configSet(Map.of(maxmemoryPolicy, "allkeys-lfu")).get());
            assertEquals(OK, clusterClient.set(key, "").get());
            assertTrue(clusterClient.objectFreq(key).get() >= 0L);
        } finally {
            clusterClient.configSet(Map.of(maxmemoryPolicy, oldPolicy)).get();
        }
    }

    public static Stream<Arguments> callCrossSlotCommandsWhichShouldFail() {
        return Stream.of(
                Arguments.of("smove", null, clusterClient.smove("abc", "zxy", "lkn")),
                Arguments.of("rename", null, clusterClient.rename("abc", "xyz")),
                Arguments.of("renamenx", null, clusterClient.renamenx("abc", "zxy")),
                Arguments.of(
                        "sinterstore", null, clusterClient.sinterstore("abc", new String[] {"zxy", "lkn"})),
                Arguments.of("sdiff", null, clusterClient.sdiff(new String[] {"abc", "zxy", "lkn"})),
                Arguments.of(
                        "sdiffstore", null, clusterClient.sdiffstore("abc", new String[] {"zxy", "lkn"})),
                Arguments.of("sinter", null, clusterClient.sinter(new String[] {"abc", "zxy", "lkn"})),
                Arguments.of(
                        "sunionstore", null, clusterClient.sunionstore("abc", new String[] {"zxy", "lkn"})),
                Arguments.of("zdiff", null, clusterClient.zdiff(new String[] {"abc", "zxy", "lkn"})),
                Arguments.of(
                        "zdiffWithScores",
                        null,
                        clusterClient.zdiffWithScores(new String[] {"abc", "zxy", "lkn"})),
                Arguments.of(
                        "zdiffstore", null, clusterClient.zdiffstore("abc", new String[] {"zxy", "lkn"})),
                Arguments.of(
                        "zunion", null, clusterClient.zunion(new KeyArray(new String[] {"abc", "zxy", "lkn"}))),
                Arguments.of(
                        "zinter",
                        "6.2.0",
                        clusterClient.zinter(new KeyArray(new String[] {"abc", "zxy", "lkn"}))),
                Arguments.of(
                        "zrangestore", null, clusterClient.zrangestore("abc", "zxy", new RangeByIndex(3, 1))),
                Arguments.of(
                        "zinterstore",
                        null,
                        clusterClient.zinterstore("foo", new KeyArray(new String[] {"abc", "zxy", "lkn"}))),
                Arguments.of(
                        "zintercard", "7.0.0", clusterClient.zintercard(new String[] {"abc", "zxy", "lkn"})),
                Arguments.of("brpop", null, clusterClient.brpop(new String[] {"abc", "zxy", "lkn"}, .1)),
                Arguments.of("blpop", null, clusterClient.blpop(new String[] {"abc", "zxy", "lkn"}, .1)),
                Arguments.of("pfcount", null, clusterClient.pfcount(new String[] {"abc", "zxy", "lkn"})),
                Arguments.of("pfmerge", null, clusterClient.pfmerge("abc", new String[] {"zxy", "lkn"})),
                Arguments.of(
                        "bzpopmax", "5.0.0", clusterClient.bzpopmax(new String[] {"abc", "zxy", "lkn"}, .1)),
                Arguments.of(
                        "bzpopmin", "5.0.0", clusterClient.bzpopmin(new String[] {"abc", "zxy", "lkn"}, .1)),
                Arguments.of(
                        "zmpop", "7.0.0", clusterClient.zmpop(new String[] {"abc", "zxy", "lkn"}, MAX)),
                Arguments.of(
                        "bzmpop", "7.0.0", clusterClient.bzmpop(new String[] {"abc", "zxy", "lkn"}, MAX, .1)),
                Arguments.of(
                        "lmpop",
                        "7.0.0",
                        clusterClient.lmpop(new String[] {"abc", "def"}, ListDirection.LEFT, 1L)),
                Arguments.of(
                        "bitop",
                        null,
                        clusterClient.bitop(BitwiseOperation.OR, "abc", new String[] {"zxy", "lkn"})),
                Arguments.of(
                        "blmpop",
                        "7.0.0",
                        clusterClient.blmpop(new String[] {"abc", "def"}, ListDirection.LEFT, 1L, 0.1)),
                Arguments.of(
                        "lmove",
                        "6.2.0",
                        clusterClient.lmove("abc", "def", ListDirection.LEFT, ListDirection.LEFT)),
                Arguments.of(
                        "blmove",
                        "6.2.0",
                        clusterClient.blmove("abc", "def", ListDirection.LEFT, ListDirection.LEFT, 1)),
                Arguments.of("sintercard", "7.0.0", clusterClient.sintercard(new String[] {"abc", "def"})),
                Arguments.of(
                        "sintercard", "7.0.0", clusterClient.sintercard(new String[] {"abc", "def"}, 1)),
                Arguments.of(
                        "fcall",
                        "7.0.0",
                        clusterClient.fcall("func", new String[] {"abc", "zxy", "lkn"}, new String[0])),
                Arguments.of(
                        "fcallReadOnly",
                        "7.0.0",
                        clusterClient.fcallReadOnly("func", new String[] {"abc", "zxy", "lkn"}, new String[0])),
                Arguments.of(
                        "xread", null, clusterClient.xread(Map.of("abc", "stream1", "zxy", "stream2"))),
                Arguments.of("copy", "6.2.0", clusterClient.copy("abc", "def", true)),
                Arguments.of("msetnx", null, clusterClient.msetnx(Map.of("abc", "def", "ghi", "jkl"))));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0} cross slot keys will throw RequestException")
    @MethodSource("callCrossSlotCommandsWhichShouldFail")
    public void check_throws_cross_slot_error(
            String testName, String minVer, CompletableFuture<?> future) {
        if (minVer != null) {
            assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo(minVer));
        }
        var executionException = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(executionException.getMessage().toLowerCase().contains("crossslot"));
    }

    public static Stream<Arguments> callCrossSlotCommandsWhichShouldPass() {
        return Stream.of(
                Arguments.of("exists", clusterClient.exists(new String[] {"abc", "zxy", "lkn"})),
                Arguments.of("unlink", clusterClient.unlink(new String[] {"abc", "zxy", "lkn"})),
                Arguments.of("del", clusterClient.del(new String[] {"abc", "zxy", "lkn"})),
                Arguments.of("mget", clusterClient.mget(new String[] {"abc", "zxy", "lkn"})),
                Arguments.of("mset", clusterClient.mset(Map.of("abc", "1", "zxy", "2", "lkn", "3"))),
                Arguments.of("touch", clusterClient.touch(new String[] {"abc", "zxy", "lkn"})));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0} cross slot keys are allowed")
    @MethodSource("callCrossSlotCommandsWhichShouldPass")
    public void check_does_not_throw_cross_slot_error(String testName, CompletableFuture<?> future) {
        future.get();
    }

    @Test
    @SneakyThrows
    public void flushall() {
        assertEquals(OK, clusterClient.flushall(FlushMode.SYNC).get());

        // TODO replace with KEYS command when implemented
        Object[] keysAfter =
                (Object[]) clusterClient.customCommand(new String[] {"keys", "*"}).get().getSingleValue();
        assertEquals(0, keysAfter.length);

        var route = new SlotKeyRoute("key", PRIMARY);
        assertEquals(OK, clusterClient.flushall().get());
        assertEquals(OK, clusterClient.flushall(route).get());
        assertEquals(OK, clusterClient.flushall(ASYNC).get());
        assertEquals(OK, clusterClient.flushall(ASYNC, route).get());

        var replicaRoute = new SlotKeyRoute("key", REPLICA);
        // command should fail on a replica, because it is read-only
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> clusterClient.flushall(replicaRoute).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(
                executionException
                        .getMessage()
                        .toLowerCase()
                        .contains("can't write against a read only replica"));
    }

    @SneakyThrows
    @ParameterizedTest(name = "functionLoad: singleNodeRoute = {0}")
    @ValueSource(booleans = {true, false})
    public void function_commands_without_keys_with_route(boolean singleNodeRoute) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");

        String libName = "mylib1c_" + singleNodeRoute;
        String funcName = "myfunc1c_" + singleNodeRoute;
        // function $funcName returns first argument
        String code = generateLuaLibCode(libName, Map.of(funcName, "return args[1]"), true);
        Route route = singleNodeRoute ? new SlotKeyRoute("1", PRIMARY) : ALL_PRIMARIES;

        assertEquals(OK, clusterClient.functionFlush(SYNC, route).get());
        assertEquals(libName, clusterClient.functionLoad(code, false, route).get());

        var fcallResult = clusterClient.fcall(funcName, new String[] {"one", "two"}, route).get();
        if (route instanceof SingleNodeRoute) {
            assertEquals("one", fcallResult.getSingleValue());
        } else {
            for (var nodeResponse : fcallResult.getMultiValue().values()) {
                assertEquals("one", nodeResponse);
            }
        }
        fcallResult = clusterClient.fcallReadOnly(funcName, new String[] {"one", "two"}, route).get();
        if (route instanceof SingleNodeRoute) {
            assertEquals("one", fcallResult.getSingleValue());
        } else {
            for (var nodeResponse : fcallResult.getMultiValue().values()) {
                assertEquals("one", nodeResponse);
            }
        }

        var expectedDescription =
                new HashMap<String, String>() {
                    {
                        put(funcName, null);
                    }
                };
        var expectedFlags =
                new HashMap<String, Set<String>>() {
                    {
                        put(funcName, Set.of("no-writes"));
                    }
                };

        var response = clusterClient.functionList(false, route).get();
        if (singleNodeRoute) {
            var flist = response.getSingleValue();
            checkFunctionListResponse(
                    flist, libName, expectedDescription, expectedFlags, Optional.empty());
        } else {
            for (var flist : response.getMultiValue().values()) {
                checkFunctionListResponse(
                        flist, libName, expectedDescription, expectedFlags, Optional.empty());
            }
        }

        response = clusterClient.functionList(true, route).get();
        if (singleNodeRoute) {
            var flist = response.getSingleValue();
            checkFunctionListResponse(
                    flist, libName, expectedDescription, expectedFlags, Optional.of(code));
        } else {
            for (var flist : response.getMultiValue().values()) {
                checkFunctionListResponse(
                        flist, libName, expectedDescription, expectedFlags, Optional.of(code));
            }
        }

        // re-load library without overwriting
        var executionException =
                assertThrows(
                        ExecutionException.class, () -> clusterClient.functionLoad(code, false, route).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(
                executionException.getMessage().contains("Library '" + libName + "' already exists"));

        // re-load library with overwriting
        assertEquals(libName, clusterClient.functionLoad(code, true, route).get());
        String newFuncName = "myfunc2c_" + singleNodeRoute;
        // function $funcName returns first argument
        // function $newFuncName returns argument array len
        String newCode =
                generateLuaLibCode(
                        libName, Map.of(funcName, "return args[1]", newFuncName, "return #args"), true);

        assertEquals(libName, clusterClient.functionLoad(newCode, true, route).get());

        expectedDescription.put(newFuncName, null);
        expectedFlags.put(newFuncName, Set.of("no-writes"));

        response = clusterClient.functionList(false, route).get();
        if (singleNodeRoute) {
            var flist = response.getSingleValue();
            checkFunctionListResponse(
                    flist, libName, expectedDescription, expectedFlags, Optional.empty());
        } else {
            for (var flist : response.getMultiValue().values()) {
                checkFunctionListResponse(
                        flist, libName, expectedDescription, expectedFlags, Optional.empty());
            }
        }

        // load new lib and delete it - first lib remains loaded
        String anotherLib = generateLuaLibCode("anotherLib", Map.of("anotherFunc", ""), false);
        assertEquals("anotherLib", clusterClient.functionLoad(anotherLib, true, route).get());
        assertEquals(OK, clusterClient.functionDelete("anotherLib", route).get());

        // delete missing lib returns a error
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> clusterClient.functionDelete("anotherLib", route).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(executionException.getMessage().contains("Library not found"));

        response = clusterClient.functionList(true, route).get();
        if (singleNodeRoute) {
            var flist = response.getSingleValue();
            checkFunctionListResponse(
                    flist, libName, expectedDescription, expectedFlags, Optional.of(newCode));
        } else {
            for (var flist : response.getMultiValue().values()) {
                checkFunctionListResponse(
                        flist, libName, expectedDescription, expectedFlags, Optional.of(newCode));
            }
        }

        fcallResult = clusterClient.fcall(newFuncName, new String[] {"one", "two"}, route).get();
        if (route instanceof SingleNodeRoute) {
            assertEquals(2L, fcallResult.getSingleValue());
        } else {
            for (var nodeResponse : fcallResult.getMultiValue().values()) {
                assertEquals(2L, nodeResponse);
            }
        }
        fcallResult =
                clusterClient.fcallReadOnly(newFuncName, new String[] {"one", "two"}, route).get();
        if (route instanceof SingleNodeRoute) {
            assertEquals(2L, fcallResult.getSingleValue());
        } else {
            for (var nodeResponse : fcallResult.getMultiValue().values()) {
                assertEquals(2L, nodeResponse);
            }
        }

        assertEquals(OK, clusterClient.functionFlush(route).get());
    }

    @SneakyThrows
    @Test
    public void function_commands_without_keys_and_without_route() {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");

        assertEquals(OK, clusterClient.functionFlush(SYNC).get());

        String libName = "mylib1c";
        String funcName = "myfunc1c";
        // function $funcName returns first argument
        // generating RO functions to execution on a replica (default routing goes to RANDOM including
        // replicas)
        String code = generateLuaLibCode(libName, Map.of(funcName, "return args[1]"), true);

        assertEquals(libName, clusterClient.functionLoad(code, false).get());

        assertEquals("one", clusterClient.fcall(funcName, new String[] {"one", "two"}).get());
        assertEquals("one", clusterClient.fcallReadOnly(funcName, new String[] {"one", "two"}).get());

        var flist = clusterClient.functionList(false).get();
        var expectedDescription =
                new HashMap<String, String>() {
                    {
                        put(funcName, null);
                    }
                };
        var expectedFlags =
                new HashMap<String, Set<String>>() {
                    {
                        put(funcName, Set.of("no-writes"));
                    }
                };
        checkFunctionListResponse(flist, libName, expectedDescription, expectedFlags, Optional.empty());

        flist = clusterClient.functionList(true).get();
        checkFunctionListResponse(
                flist, libName, expectedDescription, expectedFlags, Optional.of(code));

        // re-load library without overwriting
        var executionException =
                assertThrows(ExecutionException.class, () -> clusterClient.functionLoad(code, false).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(
                executionException.getMessage().contains("Library '" + libName + "' already exists"));

        // re-load library with overwriting
        assertEquals(libName, clusterClient.functionLoad(code, true).get());
        String newFuncName = "myfunc2c";
        // function $funcName returns first argument
        // function $newFuncName returns argument array len
        String newCode =
                generateLuaLibCode(
                        libName, Map.of(funcName, "return args[1]", newFuncName, "return #args"), true);

        assertEquals(libName, clusterClient.functionLoad(newCode, true).get());

        // load new lib and delete it - first lib remains loaded
        String anotherLib = generateLuaLibCode("anotherLib", Map.of("anotherFunc", ""), false);
        assertEquals("anotherLib", clusterClient.functionLoad(anotherLib, true).get());
        assertEquals(OK, clusterClient.functionDelete("anotherLib").get());

        // delete missing lib returns a error
        executionException =
                assertThrows(
                        ExecutionException.class, () -> clusterClient.functionDelete("anotherLib").get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(executionException.getMessage().contains("Library not found"));

        flist = clusterClient.functionList(libName, false).get();
        expectedDescription.put(newFuncName, null);
        expectedFlags.put(newFuncName, Set.of("no-writes"));
        checkFunctionListResponse(flist, libName, expectedDescription, expectedFlags, Optional.empty());

        flist = clusterClient.functionList(libName, true).get();
        checkFunctionListResponse(
                flist, libName, expectedDescription, expectedFlags, Optional.of(newCode));

        assertEquals(2L, clusterClient.fcall(newFuncName, new String[] {"one", "two"}).get());
        assertEquals(2L, clusterClient.fcallReadOnly(newFuncName, new String[] {"one", "two"}).get());

        assertEquals(OK, clusterClient.functionFlush(ASYNC).get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "xyz", "kln"})
    @SneakyThrows
    public void fcall_with_keys(String prefix) {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");

        String key = "{" + prefix + "}-fcall_with_keys-";
        SingleNodeRoute route = new SlotKeyRoute(key, PRIMARY);
        String libName = "mylib_with_keys";
        String funcName = "myfunc_with_keys";
        // function $funcName returns array with first two arguments
        String code = generateLuaLibCode(libName, Map.of(funcName, "return {keys[1], keys[2]}"), true);

        // loading function to the node where key is stored
        assertEquals(libName, clusterClient.functionLoad(code, false, route).get());

        // due to common prefix, all keys are mapped to the same hash slot
        var functionResult =
                clusterClient.fcall(funcName, new String[] {key + 1, key + 2}, new String[0]).get();
        assertArrayEquals(new Object[] {key + 1, key + 2}, (Object[]) functionResult);
        functionResult =
                clusterClient.fcallReadOnly(funcName, new String[] {key + 1, key + 2}, new String[0]).get();
        assertArrayEquals(new Object[] {key + 1, key + 2}, (Object[]) functionResult);

        var transaction =
                new ClusterTransaction()
                        .fcall(funcName, new String[] {key + 1, key + 2}, new String[0])
                        .fcallReadOnly(funcName, new String[] {key + 1, key + 2}, new String[0]);

        // check response from a routed transaction request
        assertDeepEquals(
                new Object[][] {{key + 1, key + 2}, {key + 1, key + 2}},
                clusterClient.exec(transaction, route).get());
        // if no route given, GLIDE should detect it automatically
        assertDeepEquals(
                new Object[][] {{key + 1, key + 2}, {key + 1, key + 2}},
                clusterClient.exec(transaction).get());

        assertEquals(OK, clusterClient.functionDelete(libName, route).get());
    }

    @SneakyThrows
    @Test
    public void fcall_readonly_function() {
        assumeTrue(REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0"), "This feature added in redis 7");

        String libName = "fcall_readonly_function";
        // intentionally using a REPLICA route
        Route replicaRoute = new SlotKeyRoute(libName, REPLICA);
        Route primaryRoute = new SlotKeyRoute(libName, PRIMARY);
        String funcName = "fcall_readonly_function";

        // function $funcName returns a magic number
        String code = generateLuaLibCode(libName, Map.of(funcName, "return 42"), false);

        assertEquals(libName, clusterClient.functionLoad(code, false).get());

        // fcall on a replica node should fail, because a function isn't guaranteed to be RO
        var executionException =
                assertThrows(
                        ExecutionException.class, () -> clusterClient.fcall(funcName, replicaRoute).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(
                executionException.getMessage().contains("You can't write against a read only replica."));

        // fcall_ro also fails
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> clusterClient.fcallReadOnly(funcName, replicaRoute).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(
                executionException.getMessage().contains("You can't write against a read only replica."));

        // fcall_ro also fails to run it even on primary - another error
        executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> clusterClient.fcallReadOnly(funcName, primaryRoute).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(
                executionException
                        .getMessage()
                        .contains("Can not execute a script with write flag using *_ro command."));

        // create the same function, but with RO flag
        code = generateLuaLibCode(libName, Map.of(funcName, "return 42"), true);

        assertEquals(libName, clusterClient.functionLoad(code, true).get());

        // fcall should succeed now
        assertEquals(42L, clusterClient.fcall(funcName, replicaRoute).get().getSingleValue());

        assertEquals(OK, clusterClient.functionDelete(libName).get());
    }
}
