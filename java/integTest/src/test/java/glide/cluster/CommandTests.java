/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.api.models.commands.InfoOptions.Section.CLIENTS;
import static glide.api.models.commands.InfoOptions.Section.CLUSTER;
import static glide.api.models.commands.InfoOptions.Section.COMMANDSTATS;
import static glide.api.models.commands.InfoOptions.Section.CPU;
import static glide.api.models.commands.InfoOptions.Section.EVERYTHING;
import static glide.api.models.commands.InfoOptions.Section.MEMORY;
import static glide.api.models.commands.InfoOptions.Section.REPLICATION;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_EXISTS;
import static glide.api.models.commands.SetOptions.TimeToLiveType.KEEP_EXISTING;
import static glide.api.models.commands.SetOptions.TimeToLiveType.MILLISECONDS;
import static glide.api.models.commands.SetOptions.TimeToLiveType.UNIX_SECONDS;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.RANDOM;
import static glide.api.models.configuration.RequestRoutingConfiguration.SlotType.PRIMARY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.TestConfiguration;
import glide.api.RedisClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.TimeToLive;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CommandTests {

    private static RedisClusterClient clusterClient = null;

    private static final String KEY_NAME = "key";
    private static final String INITIAL_VALUE = "VALUE";
    private static final String ANOTHER_VALUE = "VALUE2";

    public static final List<String> DEFAULT_INFO_SECTIONS = List.of("Server", "Clients", "Memory", "Persistence", "Stats", "Replication", "CPU", "Modules", "Errorstats", "Cluster", "Keyspace");
    public static final List<String> EVERYTHING_INFO_SECTIONS = TestConfiguration.REDIS_VERSION.feature() >= 7
        // Latencystats was added in redis 7
        ? List.of("Server", "Clients", "Memory", "Persistence", "Stats", "Replication", "CPU", "Modules", "Commandstats", "Errorstats", "Latencystats", "Cluster", "Keyspace")
        : List.of("Server", "Clients", "Memory", "Persistence", "Stats", "Replication", "CPU", "Modules", "Commandstats", "Errorstats", "Cluster", "Keyspace");

    @BeforeAll
    @SneakyThrows
    public static void init() {
        clusterClient =
                RedisClusterClient.CreateClient(
                                RedisClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(TestConfiguration.CLUSTER_PORTS[0]).build())
                                        .requestTimeout(5000)
                                        .build())
                        .get(10, SECONDS);
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        clusterClient.close();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        ClusterValue<Object> data = clusterClient.customCommand(new String[] {"info"}).get(10, SECONDS);
        assertTrue(data.hasMultiData());
        for (var info : data.getMultiValue().values()) {
            assertTrue(((String) info).contains("# Stats"));
        }
    }

    @Test
    @SneakyThrows
    public void custom_command_ping() {
        var data = clusterClient.customCommand(new String[] {"ping"}).get(10, TimeUnit.SECONDS);
        assertEquals("PONG", data.getSingleValue());
    }

    @Test
    @SneakyThrows
    public void info_without_options() {
        ClusterValue<String> data = clusterClient.info().get(10, SECONDS);
        assertTrue(data.hasMultiData());
        for (var info : data.getMultiValue().values()) {
            for (var section : DEFAULT_INFO_SECTIONS) {
                assertTrue(info.contains("# " + section), "Section " + section + " is missing");
            }
        }
    }

    @Test
    @SneakyThrows
    public void info_with_route() {
        ClusterValue<String> data = clusterClient.info(RANDOM).get(10, SECONDS);
        assertTrue(data.hasSingleData());
        String infoData = data.getSingleValue();
        for (var section : DEFAULT_INFO_SECTIONS) {
            assertTrue(infoData.contains("# " + section), "Section " + section + " is missing");
        }
    }

    @Test
    @SneakyThrows
    public void info_with_multiple_options() {
        var builder = InfoOptions.builder().section(CLUSTER);
        if (TestConfiguration.REDIS_VERSION.feature() >= 7) {
            builder.section(CPU).section(MEMORY);
        }
        var options = builder.build();
        var data = clusterClient.info(options).get(10, SECONDS);
        for (var info : data.getMultiValue().values()) {
            for (var section :  options.toArgs()) {
                assertTrue(info.toLowerCase().contains("# " + section.toLowerCase()), "Section " + section + " is missing");
            }
        }
    }

    @Test
    @SneakyThrows
    public void info_with_everything_option() {
        InfoOptions options = InfoOptions.builder().section(EVERYTHING).build();
        ClusterValue<String> data = clusterClient.info(options).get(10, SECONDS);
        assertTrue(data.hasMultiData());
        for (var info : data.getMultiValue().values()) {
            for (var section : EVERYTHING_INFO_SECTIONS) {
                assertTrue(info.contains("# " + section), "Section " + section + " is missing");
            }
        }
    }

    @Test
    @SneakyThrows
    public void info_with_routing_and_options() {
        var slotData = clusterClient.customCommand(new String[] {"cluster", "slots"}).get(10, SECONDS);
        /*
        Nested Object arrays like
        1) 1) (integer) 0
           2) (integer) 5460
           3) 1) "127.0.0.1"
              2) (integer) 7000
              3) "92d73b6eb847604b63c7f7cbbf39b148acdd1318"
              4) (empty array)
        */
        // Extracting first slot key
        var slotKey = (String)((Object[])((Object[])((Object[])slotData.getSingleValue())[0])[2])[2];

        var builder = InfoOptions.builder().section(CLIENTS);
        if (TestConfiguration.REDIS_VERSION.feature() >= 7) {
            builder.section(COMMANDSTATS).section(REPLICATION);
        }
        var options = builder.build();
        var routing = new SlotKeyRoute(slotKey, PRIMARY);
        var data = clusterClient.info(options, routing).get(10, SECONDS);

        for (var section : options.toArgs()) {
            assertTrue(data.getSingleValue().toLowerCase().contains("# " + section.toLowerCase()), "Section " + section + " is missing");
        }
    }

    @Test
    @SneakyThrows
    public void ping() {
        String data = clusterClient.ping().get(10, SECONDS);
        assertEquals("PONG", data);
    }

    @Test
    @SneakyThrows
    public void ping_with_message() {
        String data = clusterClient.ping("H3LL0").get(10, SECONDS);
        assertEquals("H3LL0", data);
    }

    @Test
    @SneakyThrows
    public void ping_with_route() {
        String data = clusterClient.ping(ALL_NODES).get(10, SECONDS);
        assertEquals("PONG", data);
    }

    @Test
    @SneakyThrows
    public void ping_with_message_with_route() {
        String data = clusterClient.ping("H3LL0", ALL_PRIMARIES).get(10, SECONDS);
        assertEquals("H3LL0", data);
    }

    @Test
    @SneakyThrows
    public void custom_command_del_returns_a_number() {
        clusterClient.set("DELME", INITIAL_VALUE).get(10, SECONDS);
        var del = clusterClient.customCommand(new String[] {"DEL", "DELME"}).get(10, SECONDS);
        assertEquals(1L, del.getSingleValue());
        var data = clusterClient.get("DELME").get(10, SECONDS);
        assertNull(data);
    }

    @Test
    public void set_requires_a_value() {
        assertThrows(NullPointerException.class, () -> clusterClient.set("SET", null));
    }

    @Test
    public void set_requires_a_key() {
        assertThrows(NullPointerException.class, () -> clusterClient.set(null, INITIAL_VALUE));
    }

    @Test
    public void get_requires_a_key() {
        assertThrows(NullPointerException.class, () -> clusterClient.get(null));
    }

    @Test
    @SneakyThrows
    public void set_only_if_exists_overwrite() {
        var options = SetOptions.builder().conditionalSet(ONLY_IF_EXISTS).build();
        clusterClient.set("set_only_if_exists_overwrite", INITIAL_VALUE).get(10, SECONDS);
        clusterClient.set("set_only_if_exists_overwrite", ANOTHER_VALUE, options).get(10, SECONDS);
        var data = clusterClient.get("set_only_if_exists_overwrite").get(10, SECONDS);
        assertEquals(ANOTHER_VALUE, data);
    }

    @Test
    @SneakyThrows
    public void set_only_if_exists_missing_key() {
        var options = SetOptions.builder().conditionalSet(ONLY_IF_EXISTS).build();
        clusterClient.set("set_only_if_exists_missing_key", ANOTHER_VALUE, options).get(10, SECONDS);
        var data = clusterClient.get("set_only_if_exists_missing_key").get(10, SECONDS);
        assertNull(data);
    }

    @Test
    @SneakyThrows
    public void set_only_if_does_not_exists_missing_key() {
        var options = SetOptions.builder().conditionalSet(ONLY_IF_DOES_NOT_EXIST).build();
        clusterClient.set("set_only_if_does_not_exists_missing_key", ANOTHER_VALUE, options).get(10, SECONDS);
        var data = clusterClient.get("set_only_if_does_not_exists_missing_key").get(10, SECONDS);
        assertEquals(ANOTHER_VALUE, data);
    }

    @Test
    @SneakyThrows
    public void set_only_if_does_not_exists_existing_key() {
        var options = SetOptions.builder().conditionalSet(ONLY_IF_DOES_NOT_EXIST).build();
        clusterClient.set("set_only_if_does_not_exists_existing_key", INITIAL_VALUE).get(10, SECONDS);
        clusterClient.set("set_only_if_does_not_exists_existing_key", ANOTHER_VALUE, options).get(10, SECONDS);
        var data = clusterClient.get("set_only_if_does_not_exists_existing_key").get(10, SECONDS);
        assertEquals(INITIAL_VALUE, data);
    }

    @Test
    @SneakyThrows
    public void set_value_with_ttl_and_update_value_with_keeping_ttl() {
        var options = SetOptions.builder().expiry(
            SetOptions.TimeToLive.builder().count(2000).type(MILLISECONDS).build()).build();
        clusterClient.set("set_value_with_ttl_and_update_value_with_keeping_ttl", INITIAL_VALUE, options).get(10, SECONDS);
        var data = clusterClient.get("set_value_with_ttl_and_update_value_with_keeping_ttl").get(10, SECONDS);
        assertEquals(INITIAL_VALUE, data);

        options = SetOptions.builder().expiry(TimeToLive.builder().type(KEEP_EXISTING).build()).build();
        clusterClient.set("set_value_with_ttl_and_update_value_with_keeping_ttl", ANOTHER_VALUE, options).get(10, SECONDS);
        data = clusterClient.get("set_value_with_ttl_and_update_value_with_keeping_ttl").get(10, SECONDS);
        assertEquals(ANOTHER_VALUE, data);

        Thread.sleep(2222); // sleep a bit more than TTL

        data = clusterClient.get("set_value_with_ttl_and_update_value_with_keeping_ttl").get(10, SECONDS);
        assertNull(data);
    }

    @Test
    @SneakyThrows
    public void set_value_with_ttl_and_update_value_with_new_ttl() {
        var options = SetOptions.builder().expiry(TimeToLive.builder().count(100500).type(MILLISECONDS).build()).build();
        clusterClient.set("set_value_with_ttl_and_update_value_with_new_ttl", INITIAL_VALUE, options).get(10, SECONDS);
        var data = clusterClient.get("set_value_with_ttl_and_update_value_with_new_ttl").get(10, SECONDS);
        assertEquals(INITIAL_VALUE, data);

        options = SetOptions.builder().expiry(TimeToLive.builder().count(2000).type(MILLISECONDS).build()).build();
        clusterClient.set("set_value_with_ttl_and_update_value_with_new_ttl", ANOTHER_VALUE, options).get(10, SECONDS);
        data = clusterClient.get("set_value_with_ttl_and_update_value_with_new_ttl").get(10, SECONDS);
        assertEquals(ANOTHER_VALUE, data);

        Thread.sleep(2222); // sleep a bit more than new TTL

        data = clusterClient.get("set_value_with_ttl_and_update_value_with_new_ttl").get(10, SECONDS);
        assertNull(data);
    }

    @Test
    @SneakyThrows
    public void set_expired_value() { // expiration is in the past
        var options = SetOptions.builder().expiry(TimeToLive.builder().count(100500).type(UNIX_SECONDS).build()).build();
        clusterClient.set("set_expired_value", INITIAL_VALUE, options).get(10, SECONDS);
        var data = clusterClient.get("set_expired_value").get(10, SECONDS);
        assertNull(data);
    }
}
