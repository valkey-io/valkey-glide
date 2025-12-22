/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.*;
import static glide.api.BaseClient.OK;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SlotType.PRIMARY;
import static glide.api.models.configuration.RequestRoutingConfiguration.SlotType.REPLICA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.*;
import glide.api.models.exceptions.ClosingException;
import glide.cluster.ValkeyCluster;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(10) // seconds
public class ConnectionTests {

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @SneakyThrows
    public void basic_client(ProtocolVersion protocol) {
        var regularClient =
                GlideClient.createClient(commonClientConfig().protocol(protocol).build()).get();
        regularClient.close();
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @SneakyThrows
    public void cluster_client(ProtocolVersion protocol) {
        var clusterClient =
                GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get();
        clusterClient.close();
    }

    @SneakyThrows
    public GlideClusterClient createAzTestClient(String az) {
        return GlideClusterClient.createClient(
                        azClusterClientConfig()
                                .readFrom(ReadFrom.AZ_AFFINITY)
                                .clientAZ(az)
                                .requestTimeout(2000)
                                .build())
                .get();
    }

    @SneakyThrows
    private ValkeyCluster createDedicatedCluster(boolean clusterMode) {
        return new ValkeyCluster(false, clusterMode, clusterMode ? 3 : 1, 0, null, null);
    }

    @SneakyThrows
    public BaseClient createConnectionTimeoutClient(
            Boolean clusterMode,
            int connectionTimeout,
            int requestTimeout,
            BackoffStrategy backoffStrategy) {
        if (clusterMode) {
            var advancedConfiguration =
                    AdvancedGlideClusterClientConfiguration.builder()
                            .connectionTimeout(connectionTimeout)
                            .build();
            return GlideClusterClient.createClient(
                            commonClusterClientConfig()
                                    .advancedConfiguration(advancedConfiguration)
                                    .requestTimeout(requestTimeout)
                                    .build())
                    .get();
        }
        var advancedConfiguration =
                AdvancedGlideClientConfiguration.builder().connectionTimeout(connectionTimeout).build();
        return GlideClient.createClient(
                        commonClientConfig()
                                .advancedConfiguration(advancedConfiguration)
                                .requestTimeout(requestTimeout)
                                .reconnectStrategy(backoffStrategy)
                                .build())
                .get();
    }

    @SneakyThrows
    public BaseClient createClientWithTLSMode(boolean isCluster, boolean useInsecureTLS) {
        if (isCluster) {
            var advancedConfiguration =
                    AdvancedGlideClusterClientConfiguration.builder()
                            .tlsAdvancedConfiguration(
                                    TlsAdvancedConfiguration.builder().useInsecureTLS(useInsecureTLS).build())
                            .build();
            return GlideClusterClient.createClient(
                            commonClusterClientConfig().advancedConfiguration(advancedConfiguration).build())
                    .get();
        }
        var advancedConfiguration =
                AdvancedGlideClientConfiguration.builder()
                        .tlsAdvancedConfiguration(
                                TlsAdvancedConfiguration.builder().useInsecureTLS(useInsecureTLS).build())
                        .build();
        return GlideClient.createClient(
                        commonClientConfig().advancedConfiguration(advancedConfiguration).build())
                .get();
    }

    /**
     * Test that the client with AZ affinity strategy routes in a round-robin manner to all replicas
     * within the specified AZ.
     */
    @SneakyThrows
    @Test
    public void test_routing_by_slot_to_replica_with_az_affinity_strategy_to_all_replicas() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");

        String az = "us-east-1a";

        // Create client for setting the configs
        GlideClusterClient configSetClient =
                GlideClusterClient.createClient(azClusterClientConfig().requestTimeout(2000).build()).get();
        assertEquals(configSetClient.configResetStat().get(), OK);

        // Get Replica Count for current cluster
        var clusterInfo =
                configSetClient
                        .customCommand(
                                new String[] {"INFO", "REPLICATION"},
                                new RequestRoutingConfiguration.SlotKeyRoute("key", PRIMARY))
                        .get();
        long nReplicas =
                Long.parseLong(
                        Stream.of(((String) clusterInfo.getSingleValue()).split("\\R"))
                                .map(line -> line.split(":", 2))
                                .filter(parts -> parts.length == 2 && parts[0].trim().equals("connected_slaves"))
                                .map(parts -> parts[1].trim())
                                .findFirst()
                                .get());
        long nGetCalls = 3 * nReplicas;
        String getCmdstat = String.format("cmdstat_get:calls=%d", 3);

        // Setting AZ for all Nodes
        configSetClient.configSet(Map.of("availability-zone", az), ALL_NODES).get();
        configSetClient.close();

        // Creating Client with AZ configuration for testing
        GlideClusterClient azTestClient = createAzTestClient(az);
        ClusterValue<Map<String, String>> azGetResult =
                azTestClient.configGet(new String[] {"availability-zone"}, ALL_NODES).get();
        Map<String, Map<String, String>> azData = azGetResult.getMultiValue();

        // Check that all replicas have the availability zone set to the az
        for (var entry : azData.entrySet()) {
            assertEquals(az, entry.getValue().get("availability-zone"));
        }

        // execute GET commands
        for (int i = 0; i < nGetCalls; i++) {
            azTestClient.get("foo").get();
        }

        ClusterValue<String> infoResult =
                azTestClient.info(new InfoOptions.Section[] {InfoOptions.Section.ALL}, ALL_NODES).get();
        Map<String, String> infoData = infoResult.getMultiValue();

        // Check that all replicas have the same number of GET calls
        long matchingEntries =
                infoData.values().stream()
                        .filter(value -> value.contains(getCmdstat) && value.contains(az))
                        .count();
        assertEquals(nReplicas, matchingEntries);
        azTestClient.close();
    }

    /**
     * Test that the client with az affinity strategy will only route to the 1 replica with the same
     * az.
     */
    @SneakyThrows
    @Test
    public void test_routing_with_az_affinity_strategy_to_1_replica() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");

        String az = "us-east-1a";
        int nGetCalls = 3;
        String getCmdstat = String.format("cmdstat_get:calls=%d", nGetCalls);

        GlideClusterClient configSetClient =
                GlideClusterClient.createClient(azClusterClientConfig().requestTimeout(2000).build()).get();

        // reset availability zone for all nodes
        configSetClient.configSet(Map.of("availability-zone", ""), ALL_NODES).get();
        assertEquals(configSetClient.configResetStat().get(), OK);

        Long fooSlotKey =
                (Long)
                        configSetClient
                                .customCommand(new String[] {"CLUSTER", "KEYSLOT", "foo"})
                                .get()
                                .getSingleValue();
        int convertedKey = Integer.parseInt(fooSlotKey.toString());
        configSetClient
                .configSet(
                        Map.of("availability-zone", az),
                        new RequestRoutingConfiguration.SlotIdRoute(convertedKey, REPLICA))
                .get();
        configSetClient.close();

        GlideClusterClient azTestClient = createAzTestClient(az);

        // execute GET commands
        for (int i = 0; i < nGetCalls; i++) {
            azTestClient.get("foo").get();
        }

        ClusterValue<String> infoResult =
                azTestClient.info(new InfoOptions.Section[] {InfoOptions.Section.ALL}, ALL_NODES).get();
        Map<String, String> infoData = infoResult.getMultiValue();

        // Check that all replicas have the same number of GET calls
        long matchingEntries =
                infoData.values().stream()
                        .filter(value -> value.contains(getCmdstat) && value.contains(az))
                        .count();
        assertEquals(1, matchingEntries);
        azTestClient.close();
    }

    @SneakyThrows
    @Test
    public void test_az_affinity_non_existing_az() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");

        int nGetCalls = 4;
        int nReplicaCalls = 1;
        String getCmdstat = String.format("cmdstat_get:calls=%d", nReplicaCalls);

        GlideClusterClient azTestClient = createAzTestClient("non-existing-az");
        assertEquals(azTestClient.configResetStat(ALL_NODES).get(), OK);

        // execute GET commands
        for (int i = 0; i < nGetCalls; i++) {
            azTestClient.get("foo").get();
        }

        ClusterValue<String> infoResult =
                azTestClient
                        .info(new InfoOptions.Section[] {InfoOptions.Section.COMMANDSTATS}, ALL_NODES)
                        .get();
        Map<String, String> infoData = infoResult.getMultiValue();

        //  We expect the calls to be distributed evenly among the replicas
        long matchingEntries =
                infoData.values().stream().filter(value -> value.contains(getCmdstat)).count();
        long expectedReplicas = 4;
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            expectedReplicas = 0;
        }

        assertEquals(expectedReplicas, matchingEntries);
        azTestClient.close();
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_connection_timeout(boolean clusterMode) {
        var backoffStrategy =
                BackoffStrategy.builder().exponentBase(2).factor(100).numOfRetries(1).build();
        var client = createConnectionTimeoutClient(clusterMode, 250, 20000, backoffStrategy);

        // Runnable for long-running DEBUG SLEEP command
        Runnable debugSleepTask =
                () -> {
                    try {
                        if (client instanceof GlideClusterClient) {
                            ((GlideClusterClient) client)
                                    .customCommand(new String[] {"DEBUG", "sleep", "7"}, ALL_NODES)
                                    .get();
                        } else if (client instanceof GlideClient) {
                            ((GlideClient) client).customCommand(new String[] {"DEBUG", "sleep", "7"}).get();
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException("Error during DEBUG SLEEP command", e);
                    }
                };

        // Runnable for testing connection failure due to timeout
        Runnable failToConnectTask =
                () -> {
                    try {
                        Thread.sleep(1000); // Wait to ensure the debug sleep command is running
                        ExecutionException executionException =
                                assertThrows(
                                        ExecutionException.class,
                                        () -> createConnectionTimeoutClient(clusterMode, 100, 250, backoffStrategy));
                        assertInstanceOf(ClosingException.class, executionException.getCause());
                        assertTrue(executionException.getMessage().toLowerCase().contains("timed out"));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Thread was interrupted", e);
                    }
                };

        // Runnable for testing successful connection
        Runnable connectToClientTask =
                () -> {
                    try {
                        Thread.sleep(1000); // Wait to ensure the debug sleep command is running
                        var timeoutClient =
                                createConnectionTimeoutClient(clusterMode, 10000, 250, backoffStrategy);
                        assertEquals(timeoutClient.set("key", "value").get(), "OK");
                        timeoutClient.close();
                    } catch (Exception e) {
                        throw new RuntimeException("Error during successful connection attempt", e);
                    }
                };

        // Execute all tasks concurrently
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        try {
            executorService.invokeAll(
                    List.of(
                            Executors.callable(debugSleepTask),
                            Executors.callable(failToConnectTask),
                            Executors.callable(connectToClientTask)));
        } finally {
            executorService.shutdown();
            // Clean up the main client
            if (client != null) {
                client.close();
            }
        }
    }

    @SneakyThrows
    @Test
    public void test_az_affinity_replicas_and_primary_routes_to_primary() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");

        String az = "us-east-1a";
        String otherAz = "us-east-1b";
        int nGetCalls = 4;
        String getCmdstat = String.format("cmdstat_get:calls=%d", nGetCalls);

        // Create client for setting the configs
        GlideClusterClient configSetClient =
                GlideClusterClient.createClient(azClusterClientConfig().requestTimeout(2000).build()).get();

        // Reset stats and set all nodes to other_az
        assertEquals(configSetClient.configResetStat().get(), OK);
        configSetClient.configSet(Map.of("availability-zone", otherAz), ALL_NODES).get();

        // Set primary for slot 12182 to az
        configSetClient
                .configSet(
                        Map.of("availability-zone", az),
                        new RequestRoutingConfiguration.SlotIdRoute(12182, PRIMARY))
                .get();

        // Verify primary AZ
        ClusterValue<Map<String, String>> primaryAzResult =
                configSetClient
                        .configGet(
                                new String[] {"availability-zone"},
                                new RequestRoutingConfiguration.SlotIdRoute(12182, PRIMARY))
                        .get();
        assertEquals(
                az,
                primaryAzResult.getSingleValue().get("availability-zone"),
                "Primary for slot 12182 is not in the expected AZ " + az);

        configSetClient.close();

        // Create test client with AZ_AFFINITY_REPLICAS_AND_PRIMARY configuration
        GlideClusterClient azTestClient =
                GlideClusterClient.createClient(
                                azClusterClientConfig()
                                        .readFrom(ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY)
                                        .clientAZ(az)
                                        .requestTimeout(2000)
                                        .build())
                        .get();

        // Execute GET commands
        for (int i = 0; i < nGetCalls; i++) {
            azTestClient.get("foo").get();
        }

        ClusterValue<String> infoResult =
                azTestClient.info(new InfoOptions.Section[] {InfoOptions.Section.ALL}, ALL_NODES).get();
        Map<String, String> infoData = infoResult.getMultiValue();

        // Check that only the primary in the specified AZ handled all GET calls
        long matchingEntries =
                infoData.values().stream()
                        .filter(
                                value ->
                                        value.contains(getCmdstat)
                                                && value.contains(az)
                                                && value.contains("role:master"))
                        .count();
        assertEquals(1, matchingEntries, "Exactly one primary in AZ should handle all calls");

        // Verify total GET calls
        long totalGetCalls =
                infoData.values().stream()
                        .filter(value -> value.contains("cmdstat_get:calls="))
                        .mapToInt(
                                value -> {
                                    int startIndex =
                                            value.indexOf("cmdstat_get:calls=") + "cmdstat_get:calls=".length();
                                    int endIndex = value.indexOf(",", startIndex);
                                    return Integer.parseInt(value.substring(startIndex, endIndex));
                                })
                        .sum();
        assertEquals(nGetCalls, totalGetCalls, "Total GET calls mismatch");

        azTestClient.close();
    }

    @SneakyThrows
    @Test
    public void test_az_affinity_replicas_and_primary_prioritizes_replicas_over_primary() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0"), "Skip for versions below 8");

        String clientAz = "us-east-1b"; // Client is in 1B
        String otherAz = "us-east-1a"; // Other nodes in 1A
        int nGetCalls = 4;
        String getCmdstat = String.format("cmdstat_get:calls=%d", nGetCalls);
        int slot = 12182; // slot for key "foo"

        // Create client for setting the configs
        GlideClusterClient configSetClient =
                GlideClusterClient.createClient(azClusterClientConfig().requestTimeout(2000).build()).get();

        // Reset stats
        assertEquals(configSetClient.configResetStat().get(), OK);

        // Set ALL nodes to otherAz (us-east-1a)
        configSetClient.configSet(Map.of("availability-zone", otherAz), ALL_NODES).get();

        // Set REPLICA for slot to clientAz (us-east-1b)
        configSetClient
                .configSet(
                        Map.of("availability-zone", clientAz),
                        new RequestRoutingConfiguration.SlotIdRoute(slot, REPLICA))
                .get();

        // Verify setup: Primary should be in otherAz (1A)
        ClusterValue<Map<String, String>> primaryAzResult =
                configSetClient
                        .configGet(
                                new String[] {"availability-zone"},
                                new RequestRoutingConfiguration.SlotIdRoute(slot, PRIMARY))
                        .get();
        assertEquals(
                otherAz,
                primaryAzResult.getSingleValue().get("availability-zone"),
                "Primary for slot " + slot + " should be in: " + otherAz);

        configSetClient.close();

        // Create test client with AZ_AFFINITY_REPLICAS_AND_PRIMARY configuration
        // Client is in us-east-1b, same as the replica
        GlideClusterClient azTestClient =
                GlideClusterClient.createClient(
                                azClusterClientConfig()
                                        .readFrom(ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY)
                                        .clientAZ(clientAz)
                                        .requestTimeout(2000)
                                        .build())
                        .get();

        // Execute GET commands - these should go to the replica in clientAz (1B)
        for (int i = 0; i < nGetCalls; i++) {
            azTestClient.get("foo").get();
        }

        ClusterValue<String> infoResult =
                azTestClient.info(new InfoOptions.Section[] {InfoOptions.Section.ALL}, ALL_NODES).get();
        Map<String, String> infoData = infoResult.getMultiValue();

        // Check that a REPLICA in client's AZ (1B) handled all GET calls
        long replicaMatchingEntries =
                infoData.values().stream()
                        .filter(
                                value ->
                                        value.contains(getCmdstat)
                                                && value.contains(clientAz)
                                                && value.contains("role:slave"))
                        .count();
        assertEquals(
                1,
                replicaMatchingEntries,
                "Exactly one replica in client's AZ (" + clientAz + ") should handle all GET calls");

        // Verify that the PRIMARY did NOT receive any GET calls
        boolean primaryReceivedGets =
                infoData.values().stream()
                        .anyMatch(
                                value -> value.contains("role:master") && value.contains("cmdstat_get:calls="));

        assertFalse(
                primaryReceivedGets,
                "Primary should NOT receive GET calls when a replica is available in client's AZ");

        // Verify total GET calls equals expected
        long totalGetCalls =
                infoData.values().stream()
                        .filter(value -> value.contains("cmdstat_get:calls="))
                        .mapToInt(
                                value -> {
                                    int startIndex =
                                            value.indexOf("cmdstat_get:calls=") + "cmdstat_get:calls=".length();
                                    int endIndex = value.indexOf(",", startIndex);
                                    return Integer.parseInt(value.substring(startIndex, endIndex));
                                })
                        .sum();
        assertEquals(nGetCalls, totalGetCalls, "Total GET calls mismatch");

        azTestClient.close();
    }

    /**
     * Test that the client can connect using both secure and insecure TLS modes, meaning the client
     * bypasses the SSL certificate validation.
     */
    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"true, true", "true, false", "false, true", "false, false"})
    public void test_connection_tls_mode(String clusterMode, String insecureTls) {
        try (BaseClient client =
                createClientWithTLSMode(Boolean.getBoolean(clusterMode), Boolean.getBoolean(insecureTls))) {
            assertEquals("OK", client.set("key", "val").get());
            assertEquals("val", client.get("key").get());
            assertEquals(1, client.del(new String[] {"key"}).get());
        }
    }

    /** Helper method to get client connection count for either standalone or cluster client. */
    private int getClientCount(BaseClient client) throws ExecutionException, InterruptedException {
        if (client instanceof GlideClusterClient) {
            // For cluster client, execute CLIENT LIST on all nodes
            ClusterValue<Object> result =
                    ((GlideClusterClient) client)
                            .customCommand(new String[] {"CLIENT", "LIST"}, ALL_NODES)
                            .get();

            // Result will be a dict with node addresses as keys and CLIENT LIST output as values
            int totalCount = 0;
            for (Object nodeOutput : ((Map<String, Object>) result.getMultiValue()).values()) {
                totalCount += getClientListOutputCount((String) nodeOutput);
            }

            return totalCount;
        } else {
            // For standalone client, execute CLIENT LIST directly
            String result =
                    (String) ((GlideClient) client).customCommand(new String[] {"CLIENT", "LIST"}).get();
            return getClientListOutputCount(result);
        }
    }

    /** Helper method to parse CLIENT LIST output and return the number of clients. */
    private int getClientListOutputCount(String output) {
        if (output == null || output.trim().isEmpty()) {
            return 0;
        }
        return output.trim().split("\n").length;
    }

    /**
     * Helper method to get the expected number of new connections when a lazy client is initialized.
     */
    private int getExpectedNewConnections(BaseClient client)
            throws ExecutionException, InterruptedException {
        if (client instanceof GlideClusterClient) {
            // For cluster, get node count and multiply by 2 (2 connections per node)
            ClusterValue<Object> result =
                    ((GlideClusterClient) client).customCommand(new String[] {"CLUSTER", "NODES"}).get();
            String[] nodesInfo = ((String) result.getSingleValue()).trim().split("\n");
            return nodesInfo.length * 2;
        } else {
            // For standalone, always expect 1 new connection
            return 1;
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_lazy_connection_establishes_on_first_command(boolean clusterMode)
            throws Exception {
        BaseClient monitoringClient = null;
        BaseClient lazyGlideClient = null;
        String modeString = clusterMode ? "Cluster" : "Standalone";
        try (ValkeyCluster dedicatedCluster = createDedicatedCluster(clusterMode)) {
            // 1. Create a monitoring client (eagerly connected)
            monitoringClient = createDedicatedClient(clusterMode, null, dedicatedCluster, false);
            if (clusterMode) {
                assertInstanceOf(GlideClusterClient.class, monitoringClient);
                assertEquals("PONG", ((GlideClusterClient) monitoringClient).ping().get());
            } else {
                assertInstanceOf(GlideClient.class, monitoringClient);
                assertEquals("PONG", ((GlideClient) monitoringClient).ping().get());
            }

            // 2. Get initial client count
            int clientsBeforeLazyInit = getClientCount(monitoringClient);

            // 3. Create the "lazy" client
            lazyGlideClient = createDedicatedClient(clusterMode, null, dedicatedCluster, true);

            // 4. Check count (should not change)
            int clientsAfterLazyInit = getClientCount(monitoringClient);
            assertEquals(
                    clientsBeforeLazyInit,
                    clientsAfterLazyInit,
                    String.format(
                            "Lazy client (%s) should not connect before the first command. "
                                    + "Before: %d, After: %d",
                            modeString.toLowerCase(), clientsBeforeLazyInit, clientsAfterLazyInit));

            // 5. Send the first command using the lazy client
            Object pingResponse = null;
            if (clusterMode) {
                pingResponse = ((GlideClusterClient) lazyGlideClient).ping().get();
            } else {
                pingResponse = ((GlideClient) lazyGlideClient).ping().get();
            }

            // Assert PING success for both modes
            assertEquals("PONG", pingResponse, "PING response was not 'PONG': " + pingResponse);

            // 6. Check client count after the first command
            int clientsAfterFirstCommand = getClientCount(monitoringClient);
            int expectedNewConnections = getExpectedNewConnections(monitoringClient);

            assertEquals(
                    clientsBeforeLazyInit + expectedNewConnections,
                    clientsAfterFirstCommand,
                    String.format(
                            "Lazy client (%s) should establish %d new connection(s) after the first command. "
                                    + "Before: %d, After first command: %d",
                            modeString.toLowerCase(),
                            expectedNewConnections,
                            clientsBeforeLazyInit,
                            clientsAfterFirstCommand));

        } finally {
            if (monitoringClient != null) {
                monitoringClient.close();
            }
            if (lazyGlideClient != null) {
                lazyGlideClient.close();
            }
        }
    }

    @Test
    public void testRefreshTopologyFromInitialNodesDefault() {
        // Test that refreshTopologyFromInitialNodes defaults to false when not specified
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder().build();
        assertFalse(config.isRefreshTopologyFromInitialNodes());
    }

    @Test
    public void testRefreshTopologyFromInitialNodesEnabled() {
        // Test that refreshTopologyFromInitialNodes can be set to true
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder()
                        .refreshTopologyFromInitialNodes(true)
                        .build();
        assertTrue(config.isRefreshTopologyFromInitialNodes());
    }

    @Test
    public void testRefreshTopologyFromInitialNodesDisabled() {
        // Test that refreshTopologyFromInitialNodes can be explicitly set to false
        AdvancedGlideClusterClientConfiguration config =
                AdvancedGlideClusterClientConfiguration.builder()
                        .refreshTopologyFromInitialNodes(false)
                        .build();
        assertFalse(config.isRefreshTopologyFromInitialNodes());
    }
}
