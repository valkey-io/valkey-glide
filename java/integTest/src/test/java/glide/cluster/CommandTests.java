/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.CLUSTER_PORTS;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_PRIMARIES;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_EXISTS;
import static glide.api.models.commands.SetOptions.Expiry.KeepExisting;
import static glide.api.models.commands.SetOptions.Expiry.Milliseconds;
import static glide.api.models.commands.SetOptions.Expiry.UnixSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.RedisClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.commands.SetOptions;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
public class CommandTests {

    private static RedisClusterClient clusterClient = null;

    private static final String INITIAL_VALUE = "VALUE";
    private static final String ANOTHER_VALUE = "VALUE2";

    @BeforeAll
    @SneakyThrows
    public static void init() {
        clusterClient =
                RedisClusterClient.CreateClient(
                                RedisClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(CLUSTER_PORTS[0]).build())
                                        .requestTimeout(5000)
                                        .build())
                        .get();
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        clusterClient.close();
    }

    @Test
    @SneakyThrows
    public void custom_command_info() {
        ClusterValue<Object> data = clusterClient.customCommand(new String[] {"info"}).get();
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
    public void ping_with_route() {
        String data = clusterClient.ping(ALL_NODES).get();
        assertEquals("PONG", data);
    }

    @Test
    @SneakyThrows
    public void ping_with_message_with_route() {
        String data = clusterClient.ping("H3LL0", ALL_PRIMARIES).get();
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
        clusterClient
                .set("set_only_if_does_not_exists_missing_key", ANOTHER_VALUE, options)
                .get(10, SECONDS);
        var data = clusterClient.get("set_only_if_does_not_exists_missing_key").get(10, SECONDS);
        assertEquals(ANOTHER_VALUE, data);
    }

    @Test
    @SneakyThrows
    public void set_only_if_does_not_exists_existing_key() {
        var options = SetOptions.builder().conditionalSet(ONLY_IF_DOES_NOT_EXIST).build();
        clusterClient.set("set_only_if_does_not_exists_existing_key", INITIAL_VALUE).get(10, SECONDS);
        clusterClient
                .set("set_only_if_does_not_exists_existing_key", ANOTHER_VALUE, options)
                .get(10, SECONDS);
        var data = clusterClient.get("set_only_if_does_not_exists_existing_key").get(10, SECONDS);
        assertEquals(INITIAL_VALUE, data);
    }

    @Test
    @SneakyThrows
    public void set_value_with_ttl_and_update_value_with_keeping_ttl() {
        SetOptions options = SetOptions.builder().expiry(Milliseconds(2000L)).build();
        clusterClient
                .set("set_value_with_ttl_and_update_value_with_keeping_ttl", INITIAL_VALUE, options)
                .get(10, SECONDS);
        var data =
                clusterClient.get("set_value_with_ttl_and_update_value_with_keeping_ttl").get(10, SECONDS);
        assertEquals(INITIAL_VALUE, data);

        options = SetOptions.builder().expiry(KeepExisting()).build();
        clusterClient
                .set("set_value_with_ttl_and_update_value_with_keeping_ttl", ANOTHER_VALUE, options)
                .get(10, SECONDS);
        data =
                clusterClient.get("set_value_with_ttl_and_update_value_with_keeping_ttl").get(10, SECONDS);
        assertEquals(ANOTHER_VALUE, data);

        Thread.sleep(2222); // sleep a bit more than TTL

        data =
                clusterClient.get("set_value_with_ttl_and_update_value_with_keeping_ttl").get(10, SECONDS);
        assertNull(data);
    }

    @Test
    @SneakyThrows
    public void set_value_with_ttl_and_update_value_with_new_ttl() {
        SetOptions options = SetOptions.builder().expiry(Milliseconds(100500L)).build();
        clusterClient
                .set("set_value_with_ttl_and_update_value_with_new_ttl", INITIAL_VALUE, options)
                .get(10, SECONDS);
        String data =
                clusterClient.get("set_value_with_ttl_and_update_value_with_new_ttl").get(10, SECONDS);
        assertEquals(INITIAL_VALUE, data);

        options = SetOptions.builder().expiry(Milliseconds(2000L)).build();
        clusterClient
                .set("set_value_with_ttl_and_update_value_with_new_ttl", ANOTHER_VALUE, options)
                .get(10, SECONDS);
        data = clusterClient.get("set_value_with_ttl_and_update_value_with_new_ttl").get(10, SECONDS);
        assertEquals(ANOTHER_VALUE, data);

        Thread.sleep(2222); // sleep a bit more than new TTL

        data = clusterClient.get("set_value_with_ttl_and_update_value_with_new_ttl").get(10, SECONDS);
        assertNull(data);
    }

    @Test
    @SneakyThrows
    public void set_expired_value() { // expiration is in the past
        SetOptions options = SetOptions.builder().expiry(UnixSeconds(100500L)).build();
        clusterClient.set("set_expired_value", INITIAL_VALUE, options).get(10, SECONDS);
        String data = clusterClient.get("set_expired_value").get(10, SECONDS);
        assertNull(data);
    }
}
