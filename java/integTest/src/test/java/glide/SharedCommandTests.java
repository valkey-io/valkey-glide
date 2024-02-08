/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.CLUSTER_PORTS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.BaseClient;
import glide.api.RedisClient;
import glide.api.RedisClusterClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import java.util.List;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SharedCommandTests {

    private static RedisClient standaloneClient = null;
    private static RedisClusterClient clusterClient = null;

    @Getter private static List<Arguments> clients;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        standaloneClient =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(STANDALONE_PORTS[0]).build())
                                        .build())
                        .get(10, SECONDS);

        clusterClient =
                RedisClusterClient.CreateClient(
                                RedisClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(CLUSTER_PORTS[0]).build())
                                        .requestTimeout(5000)
                                        .build())
                        .get(10, SECONDS);

        clients = List.of(Arguments.of(standaloneClient), Arguments.of(clusterClient));
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        standaloneClient.close();
        clusterClient.close();
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void ping(BaseClient client) {
        var data = client.ping("H3LL0").get(10, SECONDS);
        assertEquals("H3LL0", data);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void ping_with_message(BaseClient client) {
        var data = client.ping("H3LL0").get(10, SECONDS);
        assertEquals("H3LL0", data);
    }
}
