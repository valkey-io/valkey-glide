/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.GlideClusterClient;
import glide.api.models.ClusterBatch;
import glide.api.models.ClusterValue;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30) // seconds
public class ClusterManagementCommandsTests {

    private static GlideClusterClient client;

    @BeforeAll
    @SneakyThrows
    public static void setUp() {
        client = GlideClusterClient.createClient(commonClusterClientConfig().build()).get();
    }

    @AfterAll
    @SneakyThrows
    public static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @SneakyThrows
    @Test
    public void clusterInfo_returns_cluster_state() {
        // Test without route - should go to random node
        String info = client.clusterInfo().get();

        assertNotNull(info);
        assertTrue(info.contains("cluster_state:"), "Should contain cluster_state");
        assertTrue(info.contains("cluster_slots_assigned:"), "Should contain cluster_slots_assigned");
        assertTrue(info.contains("cluster_known_nodes:"), "Should contain cluster_known_nodes");

        // Verify cluster is healthy
        assertTrue(info.contains("cluster_state:ok"), "Cluster should be in OK state");
        assertTrue(info.contains("cluster_slots_assigned:16384"), "All 16384 slots should be assigned");
    }

    @SneakyThrows
    @Test
    public void clusterInfo_with_route_single_node() {
        ClusterValue<String> result = client.clusterInfo(RANDOM).get();

        assertTrue(result.hasSingleData());
        String info = result.getSingleValue();

        assertNotNull(info);
        assertTrue(info.contains("cluster_state:"));
        assertTrue(info.contains("cluster_slots_assigned:"));
    }

    @SneakyThrows
    @Test
    public void clusterInfo_with_route_all_nodes() {
        ClusterValue<String> result = client.clusterInfo(ALL_NODES).get();

        assertTrue(result.hasMultiData());
        Map<String, String> infoMap = result.getMultiValue();

        assertNotNull(infoMap);
        assertFalse(infoMap.isEmpty(), "Should have info from multiple nodes");

        // Verify each node returns valid cluster info
        for (Map.Entry<String, String> entry : infoMap.entrySet()) {
            String nodeAddress = entry.getKey();
            String info = entry.getValue();

            assertNotNull(nodeAddress);
            assertNotNull(info);
            assertTrue(
                    info.contains("cluster_state:"), "Node " + nodeAddress + " should return cluster_state");
            assertTrue(
                    info.contains("cluster_slots_assigned:"),
                    "Node " + nodeAddress + " should return slots info");
        }
    }

    @SneakyThrows
    @Test
    public void clusterNodes_returns_node_topology() {
        // Test without route
        String nodes = client.clusterNodes().get();

        assertNotNull(nodes);
        assertFalse(nodes.isEmpty());

        // Verify format: each line should have node info
        String[] lines = nodes.split("\n");
        assertTrue(lines.length > 0, "Should have at least one node");

        // Check first line format: node-id ip:port flags master/slave ...
        String firstLine = lines[0];
        String[] parts = firstLine.split(" ");
        assertTrue(parts.length >= 8, "Each node line should have at least 8 fields");

        // Verify node ID is 40-character hex string
        String nodeId = parts[0];
        assertEquals(40, nodeId.length(), "Node ID should be 40 characters");
        assertTrue(nodeId.matches("[0-9a-f]+"), "Node ID should be hexadecimal");

        // Verify address format (ip:port@cport or ip:port)
        String address = parts[1];
        assertTrue(address.contains(":"), "Address should contain port");

        // Verify flags field exists
        String flags = parts[2];
        assertNotNull(flags);
        assertTrue(
                flags.contains("master") || flags.contains("slave") || flags.contains("myself"),
                "Should have role flags");
    }

    @SneakyThrows
    @Test
    public void clusterNodes_with_route() {
        ClusterValue<String> result = client.clusterNodes(ALL_PRIMARIES).get();

        assertTrue(result.hasMultiData());
        Map<String, String> nodesMap = result.getMultiValue();

        assertNotNull(nodesMap);
        assertFalse(nodesMap.isEmpty());

        // Each primary should return the full cluster topology
        for (Map.Entry<String, String> entry : nodesMap.entrySet()) {
            String nodes = entry.getValue();
            assertNotNull(nodes);
            assertTrue(nodes.length() > 0);
            assertTrue(nodes.contains("master") || nodes.contains("slave"));
        }
    }

    @SneakyThrows
    @Test
    public void clusterShards_returns_shard_info() {
        String minVersion = "7.0.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        Object[] shards = client.clusterShards().get();

        assertNotNull(shards);
        assertTrue(shards.length > 0, "Should have at least one shard");

        // Verify first shard structure
        assertInstanceOf(Map.class, shards[0]);
        @SuppressWarnings("unchecked")
        Map<String, Object> shard = (Map<String, Object>) shards[0];

        // Check required fields
        assertTrue(shard.containsKey("slots"), "Shard should have 'slots' field");
        assertTrue(shard.containsKey("nodes"), "Shard should have 'nodes' field");

        // Verify slots is an array (can be flat list of numbers or array of ranges depending on
        // version)
        assertInstanceOf(Object[].class, shard.get("slots"));
        Object[] slots = (Object[]) shard.get("slots");
        assertTrue(slots.length > 0, "Should have slot information");

        // Verify nodes is an array
        assertInstanceOf(Object[].class, shard.get("nodes"));
        Object[] nodes = (Object[]) shard.get("nodes");
        assertTrue(nodes.length > 0, "Should have at least one node in shard");

        // Verify first node structure
        assertInstanceOf(Map.class, nodes[0]);
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) nodes[0];

        assertTrue(node.containsKey("id"), "Node should have 'id' field");
        assertTrue(node.containsKey("endpoint"), "Node should have 'endpoint' field");
        assertTrue(node.containsKey("ip"), "Node should have 'ip' field");
        assertTrue(node.containsKey("port"), "Node should have 'port' field");
        assertTrue(node.containsKey("role"), "Node should have 'role' field");

        // Verify node role is either master or replica
        String role = node.get("role").toString();
        assertTrue(role.equals("master") || role.equals("replica"), "Role should be master or replica");
    }

    @SneakyThrows
    @Test
    public void clusterShards_with_route() {
        String minVersion = "7.0.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        ClusterValue<Object[]> result = client.clusterShards(RANDOM).get();

        assertTrue(result.hasSingleData());
        Object[] shards = result.getSingleValue();

        assertNotNull(shards);
        assertTrue(shards.length > 0);
    }

    @SneakyThrows
    @Test
    public void clusterSlots_returns_slot_mapping() {
        Object[][] slots = client.clusterSlots().get();

        assertNotNull(slots);
        assertTrue(slots.length > 0, "Should have at least one slot range");

        // Verify first slot range structure
        Object[] slotRange = slots[0];
        assertTrue(slotRange.length >= 3, "Should have start, end, and at least one node");

        // Verify start and end slots are longs
        assertInstanceOf(Long.class, slotRange[0]);
        assertInstanceOf(Long.class, slotRange[1]);

        long startSlot = (Long) slotRange[0];
        long endSlot = (Long) slotRange[1];

        assertTrue(startSlot >= 0 && startSlot <= 16383, "Start slot should be in valid range");
        assertTrue(endSlot >= 0 && endSlot <= 16383, "End slot should be in valid range");
        assertTrue(startSlot <= endSlot, "Start slot should be <= end slot");

        // Verify node info structure [ip, port, node-id]
        assertInstanceOf(Object[].class, slotRange[2]);
        Object[] masterNode = (Object[]) slotRange[2];

        assertTrue(masterNode.length >= 3, "Node info should have at least ip, port, node-id");
        assertInstanceOf(String.class, masterNode[0]); // IP
        assertInstanceOf(Long.class, masterNode[1]); // Port
        assertInstanceOf(String.class, masterNode[2]); // Node ID

        String nodeId = (String) masterNode[2];
        assertEquals(40, nodeId.length(), "Node ID should be 40 characters");
    }

    @SneakyThrows
    @Test
    public void clusterSlots_with_route() {
        ClusterValue<Object[][]> result = client.clusterSlots(RANDOM).get();

        assertTrue(result.hasSingleData());
        Object[][] slots = result.getSingleValue();

        assertNotNull(slots);
        assertTrue(slots.length > 0);

        // Verify structure
        Object[] firstRange = slots[0];
        assertTrue(firstRange.length >= 3);
    }

    @SneakyThrows
    @Test
    public void clusterLinks_returns_link_info() {
        String minVersion = "7.0.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        Object[] links = client.clusterLinks().get();

        assertNotNull(links);
        // Links may be empty in single-node cluster, so we just verify the type

        if (links.length > 0) {
            // Verify first link structure
            assertInstanceOf(Map.class, links[0]);
            @SuppressWarnings("unchecked")
            Map<String, Object> link = (Map<String, Object>) links[0];

            assertTrue(link.containsKey("direction"), "Link should have 'direction' field");
            assertTrue(link.containsKey("node"), "Link should have 'node' field");
            assertTrue(link.containsKey("create-time"), "Link should have 'create-time' field");

            String direction = link.get("direction").toString();
            assertTrue(
                    direction.equals("to") || direction.equals("from"), "Direction should be 'to' or 'from'");
        }
    }

    @SneakyThrows
    @Test
    public void clusterLinks_with_route() {
        String minVersion = "7.0.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        ClusterValue<Object[]> result = client.clusterLinks(ALL_NODES).get();

        assertTrue(result.hasMultiData());
        Map<String, Object[]> linksMap = result.getMultiValue();

        assertNotNull(linksMap);
        assertFalse(linksMap.isEmpty());
    }

    @SneakyThrows
    @Test
    public void clusterMyId_returns_node_id() {
        String nodeId = client.clusterMyId().get();

        assertNotNull(nodeId);
        assertEquals(40, nodeId.length(), "Node ID should be 40 characters");
        assertTrue(nodeId.matches("[0-9a-f]+"), "Node ID should be hexadecimal");
    }

    @SneakyThrows
    @Test
    public void clusterMyId_with_route() {
        ClusterValue<String> result = client.clusterMyId(ALL_NODES).get();

        assertTrue(result.hasMultiData());
        Map<String, String> idsMap = result.getMultiValue();

        assertNotNull(idsMap);
        assertFalse(idsMap.isEmpty());

        // Verify each node returns a valid ID
        for (Map.Entry<String, String> entry : idsMap.entrySet()) {
            String nodeId = entry.getValue();
            assertEquals(40, nodeId.length(), "Each node ID should be 40 characters");
            assertTrue(nodeId.matches("[0-9a-f]+"), "Each node ID should be hexadecimal");
        }

        // Verify IDs are unique
        long uniqueIds = idsMap.values().stream().distinct().count();
        assertEquals(idsMap.size(), uniqueIds, "All node IDs should be unique");
    }

    @SneakyThrows
    @Test
    public void clusterMyShardId_returns_shard_id() {
        String minVersion = "7.2.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        String shardId = client.clusterMyShardId().get();

        assertNotNull(shardId);
        assertEquals(40, shardId.length(), "Shard ID should be 40 characters");
        assertTrue(shardId.matches("[0-9a-f]+"), "Shard ID should be hexadecimal");
    }

    @SneakyThrows
    @Test
    public void clusterMyShardId_with_route() {
        String minVersion = "7.2.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        ClusterValue<String> result = client.clusterMyShardId(ALL_PRIMARIES).get();

        assertTrue(result.hasMultiData());
        Map<String, String> shardIdsMap = result.getMultiValue();

        assertNotNull(shardIdsMap);
        assertFalse(shardIdsMap.isEmpty());

        // Verify each shard ID is valid
        for (Map.Entry<String, String> entry : shardIdsMap.entrySet()) {
            String shardId = entry.getValue();
            assertEquals(40, shardId.length(), "Each shard ID should be 40 characters");
            assertTrue(shardId.matches("[0-9a-f]+"), "Each shard ID should be hexadecimal");
        }
    }

    @SneakyThrows
    @Test
    public void batch_cluster_commands() {
        ClusterBatch batch = new ClusterBatch(false);
        batch.clusterInfo();
        batch.clusterMyId();
        batch.clusterNodes();

        Object[] results = client.exec(batch, false).get();

        assertEquals(3, results.length, "Should have 3 results");

        // Verify clusterInfo result
        assertInstanceOf(String.class, results[0]);
        String info = (String) results[0];
        assertTrue(info.contains("cluster_state:"));

        // Verify clusterMyId result
        assertInstanceOf(String.class, results[1]);
        String nodeId = (String) results[1];
        assertEquals(40, nodeId.length());

        // Verify clusterNodes result
        assertInstanceOf(String.class, results[2]);
        String nodes = (String) results[2];
        assertTrue(nodes.contains("master") || nodes.contains("slave"));
    }

    @SneakyThrows
    @Test
    public void batch_cluster_commands_with_shards_slots() {
        String minVersion = "7.0.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        ClusterBatch batch = new ClusterBatch(false);
        batch.clusterShards();
        batch.clusterSlots();

        Object[] results = client.exec(batch, false).get();

        assertEquals(2, results.length, "Should have 2 results");

        // Verify clusterShards result (array of shard info)
        assertNotNull(results[0]);

        // Verify clusterSlots result (array, may be wrapped depending on response)
        assertNotNull(results[1]);
        // Batch responses may wrap array results differently
        if (results[1] instanceof Object[][]) {
            Object[][] slots = (Object[][]) results[1];
            assertTrue(slots.length > 0);
        } else if (results[1] instanceof Object[]) {
            Object[] slots = (Object[]) results[1];
            assertTrue(slots.length > 0);
        }
    }

    @SneakyThrows
    @Test
    public void batch_cluster_commands_with_links() {
        String minVersion = "7.0.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        ClusterBatch batch = new ClusterBatch(false);
        batch.clusterLinks();
        batch.clusterMyId();

        Object[] results = client.exec(batch, false).get();

        assertEquals(2, results.length, "Should have 2 results");

        // Verify clusterLinks result
        assertInstanceOf(Object[].class, results[0]);

        // Verify clusterMyId result
        assertInstanceOf(String.class, results[1]);
    }

    @SneakyThrows
    @Test
    public void batch_cluster_commands_with_shard_id() {
        String minVersion = "7.2.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        ClusterBatch batch = new ClusterBatch(false);
        batch.clusterMyShardId();
        batch.clusterMyId();

        Object[] results = client.exec(batch, false).get();

        assertEquals(2, results.length, "Should have 2 results");

        // Verify both are strings
        assertInstanceOf(String.class, results[0]);
        assertInstanceOf(String.class, results[1]);

        String shardId = (String) results[0];
        String nodeId = (String) results[1];

        assertEquals(40, shardId.length());
        assertEquals(40, nodeId.length());
    }

    @SneakyThrows
    @Test
    public void verify_cluster_topology_consistency() {
        // Get cluster info from multiple sources and verify consistency
        String nodesOutput = client.clusterNodes().get();
        Object[][] slotsOutput = client.clusterSlots().get();
        String infoOutput = client.clusterInfo().get();

        // Extract known nodes count from info
        String[] infoLines = infoOutput.split("\n");
        int knownNodes = 0;
        for (String line : infoLines) {
            if (line.startsWith("cluster_known_nodes:")) {
                knownNodes = Integer.parseInt(line.split(":")[1].trim());
                break;
            }
        }

        // Count nodes in clusterNodes output
        String[] nodeLines = nodesOutput.trim().split("\n");
        int nodesCount = nodeLines.length;

        assertEquals(
                knownNodes, nodesCount, "Node count should match between cluster info and cluster nodes");

        // Verify all slots are covered
        long totalSlots = 0;
        for (Object[] slotRange : slotsOutput) {
            long start = (Long) slotRange[0];
            long end = (Long) slotRange[1];
            totalSlots += (end - start + 1);
        }

        assertEquals(16384, totalSlots, "All 16384 slots should be assigned");
    }

    @SneakyThrows
    @Test
    public void concurrent_cluster_info_requests() {
        // Test that concurrent cluster info requests work correctly
        int numRequests = 10;

        @SuppressWarnings("unchecked")
        java.util.concurrent.CompletableFuture<String>[] futures =
                new java.util.concurrent.CompletableFuture[numRequests];

        for (int i = 0; i < numRequests; i++) {
            futures[i] = client.clusterInfo();
        }

        // Wait for all to complete
        java.util.concurrent.CompletableFuture.allOf(futures).get();

        // Verify all responses are valid
        for (java.util.concurrent.CompletableFuture<String> future : futures) {
            String info = future.get();
            assertNotNull(info);
            assertTrue(info.contains("cluster_state:"));
        }
    }
}
