/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.CLUSTER_PORTS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.RedisClusterClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class ClientTest {
    @Test
    @SneakyThrows
    public void custom_command_info() {
        RedisClusterClient client =
                RedisClusterClient.CreateClient(
                                RedisClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(CLUSTER_PORTS[0]).build())
                                        .clientName("TEST_CLIENT_NAME")
                                        .build())
                        .get();

        String clientInfo =
                (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get().getSingleValue();
        assertTrue(clientInfo.contains("name=TEST_CLIENT_NAME"));
    }
}
