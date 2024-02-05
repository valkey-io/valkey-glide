/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.TestConfiguration;
import glide.api.RedisClusterClient;
import glide.api.models.ClusterTransaction;
import glide.api.models.ClusterValue;
import glide.api.models.Transaction;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ClusterTransactionTests {

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
                        .get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        clusterClient.close();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        ClusterTransaction transaction = new ClusterTransaction().customCommand("info");
        Object[] result = clusterClient.exec(transaction).get(10, TimeUnit.SECONDS);
        assertTrue(((String) result[0]).contains("# Stats"));
    }

    @Test
    @SneakyThrows
    public void info_simple_route_test() {
        ClusterTransaction transaction = new ClusterTransaction().info().info();
        ClusterValue<String>[] result = clusterClient.exec(transaction, RANDOM).get(10, TimeUnit.SECONDS);

        // check single-value result
        assertTrue(result[0].hasSingleData());
        assertTrue((result[0].getSingleValue()).contains("# Stats"));

        assertTrue(result[1].hasSingleData());
        assertTrue((result[1].getSingleValue()).contains("# Stats"));
    }

    @Test
    @SneakyThrows
    public void info_multi_route_test() {
        ClusterTransaction transaction = new ClusterTransaction().info().info().info().info();
        ClusterValue<String>[] result = clusterClient.exec(transaction, ALL_PRIMARIES).get(10, TimeUnit.SECONDS);

        // check single-value result
        for (int idx = 0; idx < 4; idx++) {
            assertTrue(result[idx].hasMultiData());
            Set<String> keyset = result[idx].getMultiValue().keySet();
            for (String key : keyset) {
                assertTrue((result[idx].getMultiValue().get(key)).contains("# Stats"));
            }
        }
    }
}
