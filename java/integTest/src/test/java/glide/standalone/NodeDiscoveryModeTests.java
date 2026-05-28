/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestConfiguration.TLS;
import static glide.TestUtilities.commonClientConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.NodeDiscoveryMode;
import glide.api.models.exceptions.ClosingException;
import glide.cluster.ValkeyCluster;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(50)
public class NodeDiscoveryModeTests {

    private static ValkeyCluster discoveryCluster;
    private final List<GlideClient> clients = new ArrayList<>();

    @BeforeAll
    public static void setupDiscoveryCluster() throws Exception {
        discoveryCluster = new ValkeyCluster(false, false, 1, 3, null, null);
    }

    @AfterAll
    public static void teardownDiscoveryCluster() throws Exception {
        if (discoveryCluster != null) discoveryCluster.close();
    }

    @AfterEach
    public void closeClients() {
        for (GlideClient client : clients) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        clients.clear();
    }

    private GlideClient createAndRegister(GlideClientConfiguration config) throws Exception {
        GlideClient client = GlideClient.createClient(config).get();
        clients.add(client);
        return client;
    }

    // T2: STATIC tests (shared server)

    @SneakyThrows
    @Test
    public void test_skip_info_replication_connects_and_reads() {
        GlideClient client =
                createAndRegister(commonClientConfig().nodeDiscoveryMode(NodeDiscoveryMode.STATIC).build());
        assertNull(client.get("nonexistent").get());
    }

    @SneakyThrows
    @Test
    public void test_skip_info_replication_allows_writes() {
        GlideClient client =
                createAndRegister(commonClientConfig().nodeDiscoveryMode(NodeDiscoveryMode.STATIC).build());
        String key = "test_skip_info_write_" + System.currentTimeMillis();
        client.set(key, "value").get();
        assertEquals("value", client.get(key).get());
        client.del(new String[] {key}).get();
    }

    // T3: read_only + DISCOVER_ALL rejection (shared server)

    @SneakyThrows
    @Test
    public void test_read_only_rejects_discover_replicas() {
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                GlideClient.createClient(
                                                commonClientConfig()
                                                        .readOnly(true)
                                                        .nodeDiscoveryMode(NodeDiscoveryMode.DISCOVER_ALL)
                                                        .build())
                                        .get());
        assertInstanceOf(ClosingException.class, exception.getCause());
    }

    // T4/T5/T6: Discovery tests (ad-hoc server)

    @SneakyThrows
    @Test
    public void test_discover_replicas_from_primary() {
        List<NodeAddress> nodes = discoveryCluster.getNodesAddr();
        NodeAddress primary = nodes.get(0);
        NodeAddress replica0 = nodes.get(1);
        String uniqueName = "discovery_t4_" + UUID.randomUUID();

        createAndRegister(
                GlideClientConfiguration.builder()
                        .address(primary)
                        .useTLS(TLS)
                        .nodeDiscoveryMode(NodeDiscoveryMode.DISCOVER_ALL)
                        .clientName(uniqueName)
                        .build());

        GlideClient probe =
                createAndRegister(
                        GlideClientConfiguration.builder()
                                .address(replica0)
                                .useTLS(TLS)
                                .readOnly(true)
                                .build());

        String clientList = (String) probe.customCommand(new String[] {"CLIENT", "LIST"}).get();
        assertTrue(clientList.contains(uniqueName));
    }

    @SneakyThrows
    @Test
    public void test_discover_replicas_from_replica() {
        List<NodeAddress> nodes = discoveryCluster.getNodesAddr();
        NodeAddress replica0 = nodes.get(1);
        String uniqueName = "discovery_t5_" + UUID.randomUUID();
        String key = "test_discover_from_replica_" + System.currentTimeMillis();

        GlideClient discoveryClient =
                createAndRegister(
                        GlideClientConfiguration.builder()
                                .address(replica0)
                                .useTLS(TLS)
                                .nodeDiscoveryMode(NodeDiscoveryMode.DISCOVER_ALL)
                                .clientName(uniqueName)
                                .build());

        assertNotNull(discoveryClient);
        discoveryClient.set(key, "value").get();
        assertEquals("value", discoveryClient.get(key).get());

        GlideClient probe =
                createAndRegister(
                        GlideClientConfiguration.builder()
                                .address(replica0)
                                .useTLS(TLS)
                                .readOnly(true)
                                .build());

        String clientList = (String) probe.customCommand(new String[] {"CLIENT", "LIST"}).get();
        assertTrue(clientList.contains(uniqueName));

        discoveryClient.del(new String[] {key}).get();
    }

    @SneakyThrows
    @Test
    public void test_discover_replicas_partial_addresses() {
        List<NodeAddress> nodes = discoveryCluster.getNodesAddr();
        NodeAddress primary = nodes.get(0);
        NodeAddress replica0 = nodes.get(1);
        NodeAddress replica1 = nodes.get(2);
        NodeAddress replica2 = nodes.get(3);
        String uniqueName = "discovery_t6_" + UUID.randomUUID();

        createAndRegister(
                GlideClientConfiguration.builder()
                        .address(primary)
                        .address(replica0)
                        .useTLS(TLS)
                        .nodeDiscoveryMode(NodeDiscoveryMode.DISCOVER_ALL)
                        .clientName(uniqueName)
                        .build());

        GlideClient probe1 =
                createAndRegister(
                        GlideClientConfiguration.builder()
                                .address(replica1)
                                .useTLS(TLS)
                                .readOnly(true)
                                .build());

        GlideClient probe2 =
                createAndRegister(
                        GlideClientConfiguration.builder()
                                .address(replica2)
                                .useTLS(TLS)
                                .readOnly(true)
                                .build());

        String clientList1 = (String) probe1.customCommand(new String[] {"CLIENT", "LIST"}).get();
        assertTrue(clientList1.contains(uniqueName));

        String clientList2 = (String) probe2.customCommand(new String[] {"CLIENT", "LIST"}).get();
        assertTrue(clientList2.contains(uniqueName));
    }
}
