/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestConfiguration.STANDALONE_PORTS;
import static glide.api.BaseClient.OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.RedisClient;
import glide.api.models.commands.SetOptions;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
public class CommandTests {

    private static final String KEY_NAME = "key";
    private static final String INITIAL_VALUE = "VALUE";
    private static final String ANOTHER_VALUE = "VALUE2";

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
    public void set_and_get_without_options() {
        String ok = regularClient.set(KEY_NAME, INITIAL_VALUE).get(10, SECONDS);
        assertEquals(OK, ok);

        String data = regularClient.get(KEY_NAME).get(10, SECONDS);
        assertEquals(INITIAL_VALUE, data);
    }

    @Test
    @SneakyThrows
    public void get_missing_value() {
        var data = regularClient.get("invalid").get(10, SECONDS);
        assertNull(data);
    }

    @Test
    @SneakyThrows
    public void set_overwrite_value_and_returnOldValue_returns_string() {
        String ok = regularClient.set(KEY_NAME, INITIAL_VALUE).get(10, SECONDS);
        assertEquals(OK, ok);

        var options = SetOptions.builder().returnOldValue(true).build();
        var data = regularClient.set(KEY_NAME, ANOTHER_VALUE, options).get(10, SECONDS);
        assertEquals(INITIAL_VALUE, data);
    }

    @Test
    @SneakyThrows
    public void set_missing_value_and_returnOldValue_is_null() {
        String ok = regularClient.set(KEY_NAME, INITIAL_VALUE).get(10, SECONDS);
        assertEquals(OK, ok);

        var options = SetOptions.builder().returnOldValue(true).build();
        var data = regularClient.set("another", ANOTHER_VALUE, options).get(10, SECONDS);
        assertNull(data);
    }
}
