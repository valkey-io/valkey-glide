/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.RedisClient;
import glide.api.models.commands.FlushOption;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// same as ServerManagementCommandsTests for cluster client
@Disabled("Cluster management in IT is not implemented")
public class ServerManagementCommandsTests {

    @BeforeAll
    public static void startCluster() {}

    @AfterAll
    public static void stopCluster() {}

    @SneakyThrows
    public static Stream<Arguments> getStandaloneClients() {
        return Stream.of(
                // TODO create new clients for new server
                // TODO create 2 clients - for RESP 2 and RESP 3
                );
    }

    @ParameterizedTest
    @MethodSource("getStandaloneClients")
    @SneakyThrows
    public void flushall(RedisClient regularClient) {
        assertEquals(OK, regularClient.flushall(FlushOption.SYNC).get());

        // TODO replace with KEYS command when implemented
        Object[] keysAfter = (Object[]) regularClient.customCommand(new String[] {"keys", "*"}).get();
        assertEquals(0, keysAfter.length);

        assertEquals(OK, regularClient.flushall().get());
        assertEquals(OK, regularClient.flushall(FlushOption.ASYNC).get());
    }

    // TODO transaction test
}
