/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.TestConfiguration;
import glide.api.RedisClusterClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
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
                        .get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    @SneakyThrows
    public static void deinit() {
        clusterClient.close();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        var data = clusterClient.customCommand(new String[] {"info"}).get(10, TimeUnit.SECONDS);
        for (var info : data.getMultiValue().values()) {
            assertTrue(((String) info).contains("# Stats"));
        }
    }
}
