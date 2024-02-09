/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestConfiguration.STANDALONE_PORTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.RedisClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
public class CommandTests {

    private static final String INITIAL_VALUE = "VALUE";

    private static RedisClient regularClient = null;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        regularClient =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(STANDALONE_PORTS[0]).build())
                                        .build())
                        .get();
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        regularClient.close();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        Object data = regularClient.customCommand(new String[] {"info"}).get();
        assertTrue(((String) data).contains("# Stats"));
    }

    @Test
    @SneakyThrows
    public void custom_command_del_returns_a_number() {
        regularClient.set("DELME", INITIAL_VALUE).get();
        var del = regularClient.customCommand(new String[] {"DEL", "DELME"}).get();
        assertEquals(1L, del);
        var data = regularClient.get("DELME").get();
        assertNull(data);
    }
}
