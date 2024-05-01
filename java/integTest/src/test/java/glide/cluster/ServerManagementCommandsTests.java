/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.api.BaseClient.OK;
import static glide.api.models.configuration.RequestRoutingConfiguration.SlotType.PRIMARY;
import static glide.api.models.configuration.RequestRoutingConfiguration.SlotType.REPLICA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.RedisClusterClient;
import glide.api.models.commands.FlushOption;
import glide.api.models.configuration.RequestRoutingConfiguration;
import glide.api.models.exceptions.RequestException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// TODO: we need to implement a way to manage redis instances (start, stop) from IT from java code
// https://stackoverflow.com/a/49877200/21176342
@Disabled("Cluster management in IT is not implemented")
public class ServerManagementCommandsTests {

    @BeforeAll
    public static void startCluster() {}

    @AfterAll
    public static void stopCluster() {}

    @SneakyThrows
    public static Stream<Arguments> getClusterClients() {
        return Stream.of(
                // TODO create new clients for new server
                // TODO create 2 clients - for RESP 2 and RESP 3
                );
    }

    @ParameterizedTest
    @MethodSource("getClusterClients")
    @SneakyThrows
    public void flushall(RedisClusterClient clusterClient) {
        assertEquals(OK, clusterClient.flushall(FlushOption.SYNC).get());

        // TODO replace with KEYS command when implemented
        Object[] keysAfter =
                (Object[]) clusterClient.customCommand(new String[] {"keys", "*"}).get().getSingleValue();
        assertEquals(0, keysAfter.length);

        var route = new RequestRoutingConfiguration.SlotKeyRoute("key", PRIMARY);
        assertEquals(OK, clusterClient.flushall().get());
        assertEquals(OK, clusterClient.flushall(route).get());
        assertEquals(OK, clusterClient.flushall(FlushOption.ASYNC).get());
        assertEquals(OK, clusterClient.flushall(FlushOption.ASYNC, route).get());

        var replicaRoute = new RequestRoutingConfiguration.SlotKeyRoute("key", REPLICA);
        // command should fail on a replica, because it is read-only
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> clusterClient.flushall(replicaRoute).get());
        assertInstanceOf(RequestException.class, executionException.getCause());
        assertTrue(
                executionException
                        .getMessage()
                        .toLowerCase()
                        .contains("can't write against a read only replica"));
    }

    // TODO transaction test
}
