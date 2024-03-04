/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestConfiguration.STANDALONE_PORTS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.RedisClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.exceptions.ClosingException;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class ClientTests {
    @Test
    @SneakyThrows
    public void custom_command_info() {
        RedisClient client =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(STANDALONE_PORTS[0]).build())
                                        .clientName("TEST_CLIENT_NAME")
                                        .build())
                        .get();

        String clientInfo = (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get();
        assertTrue(clientInfo.contains("name=TEST_CLIENT_NAME"));
    }

    @Test
    @SneakyThrows
    public void close_client_throws_ExecutionException_with_ClosingException_cause() {
        RedisClient client =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(STANDALONE_PORTS[0]).build())
                                        .build())
                        .get();

        client.close();
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.set("key", "value").get());
        assertTrue(executionException.getCause() instanceof ClosingException);
    }
}
