/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.RedisClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CommandTests {

    private static RedisClient regularClient = null;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        regularClient =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(
                                                NodeAddress.builder().port(TestConfiguration.STANDALONE_PORTS[0]).build())
                                        .build())
                        .get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    @SneakyThrows
    public static void deinit() {
        regularClient.close();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        var data = regularClient.customCommand(new String[] {"info"}).get(10, TimeUnit.SECONDS);
        assertTrue(((String) data).contains("# Stats"));
    }
}
