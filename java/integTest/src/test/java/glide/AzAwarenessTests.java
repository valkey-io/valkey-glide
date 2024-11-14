package glide;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.commonClusterClientConfig;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import glide.api.GlideClusterClient;
import lombok.SneakyThrows;

public class AzAwarenessTests {

    private static GlideClusterClient client = null;

    private static GlideClusterClient azclient = null;

    @BeforeEach
    @SneakyThrows
    public void init() {
        client =
            GlideClusterClient.createClient(commonClusterClientConfig().requestTimeout(7000).build())
                .get();
    }

    @AfterEach
    @SneakyThrows
    public void teardown() {
        client.close();
        azclient.close();
    }

    /**
     * Test that the client with AZ affinity strategy routes in a round-robin manner to all replicas within the specified AZ.
     */
    @SneakyThrows
    @Test
    public void test_routing_by_slot_to_replica_with_az_affinity_strategy_to_all_replicas() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");
        int nReplicas = 4;
        int getTotalCalls = 3*nReplicas;
        String getCmdstat = String.format("cmdstat_get:calls=%d", getTotalCalls / nReplicas);
    }
}
