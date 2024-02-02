/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.api.models.commands.InfoOptions.Section.CLUSTER;
import static glide.api.models.commands.InfoOptions.Section.CPU;
import static glide.api.models.commands.InfoOptions.Section.EVERYTHING;
import static glide.api.models.commands.InfoOptions.Section.MEMORY;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.RANDOM;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.TestConfiguration;
import glide.api.RedisClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CommandTests {

    private static RedisClusterClient clusterClient = null;

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
            for (var section :
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
                            "Keyspace")) {
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
        for (var section :
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
                        "Keyspace")) {
            assertTrue(infoData.contains("# " + section), "Section " + section + " is missing");
        }
    }

    @Test
    @SneakyThrows
    public void info_with_multiple_options() {
        InfoOptions options =
                InfoOptions.builder().section(CLUSTER).section(CPU).section(MEMORY).build();
        ClusterValue<String> data = clusterClient.info(options).get(10, SECONDS);
        assertTrue(data.hasMultiData());
        for (var info : data.getMultiValue().values()) {

            for (var section : options.toArgs()) {
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
        ClusterValue<String> data = clusterClient.info(options).get(10, SECONDS);
        assertTrue(data.hasMultiData());
        for (var info : data.getMultiValue().values()) {

            for (var section :
                    List.of(
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
                            "Keyspace")) {
                assertTrue(info.contains("# " + section), "Section " + section + " is missing");
            }
        }
    }

    @Test
    @SneakyThrows
    public void info_with_everything_option_and_route() {
        InfoOptions options = InfoOptions.builder().section(EVERYTHING).build();
        ClusterValue<String> data = clusterClient.info(options, RANDOM).get(10, SECONDS);
        assertTrue(data.hasSingleData());
        String infoData = data.getSingleValue();
        for (var section :
                List.of(
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
                        "Keyspace")) {
            assertTrue(infoData.contains("# " + section), "Section " + section + " is missing");
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
}
