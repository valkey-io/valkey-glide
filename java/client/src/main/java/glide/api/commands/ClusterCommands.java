/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Cluster Management" group for cluster clients.
 *
 * @see <a href="https://valkey.io/commands/?group=cluster">Cluster Management Commands</a>
 */
public interface ClusterCommands {

    /**
     * Returns information and statistics about the cluster, per the <code>CLUSTER INFO</code>
     * command.
     *
     * @since Valkey 3.0.0 and above.
     * @apiNote When in cluster mode, the command will be routed to all primary nodes.
     * @see <a href="https://valkey.io/commands/cluster-info/">valkey.io</a> for details.
     * @return A {@link String} containing cluster state information.
     * @example
     *     <pre>{@code
     * String info = client.clusterInfo().get();
     * System.out.println(info);
     * // Output:
     * // cluster_state:ok
     * // cluster_slots_assigned:16384
     * // cluster_slots_ok:16384
     * // cluster_known_nodes:6
     * // cluster_size:3
     * }</pre>
     */
    CompletableFuture<String> clusterInfo();

    /**
     * Returns information and statistics about the cluster, per the <code>CLUSTER INFO</code>
     * command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-info/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command.
     * @return A {@link String} or {@link ClusterValue} containing cluster state information,
     *     depending on the routing.
     * @example
     *     <pre>{@code
     * ClusterValue<String> info = client.clusterInfo(ALL_PRIMARIES).get();
     * info.getMultiValue().forEach((node, value) ->
     *     System.out.printf("Node %s: %s%n", node, value));
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterInfo(Route route);

    /**
     * Returns the hash slot for a given key, per the <code>CLUSTER KEYSLOT</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-keyslot/">valkey.io</a> for details.
     * @param key The key to determine the hash slot for.
     * @return The hash slot number for the specified key.
     * @example
     *     <pre>{@code
     * Long slot = client.clusterKeySlot("myKey").get();
     * System.out.printf("Key 'myKey' belongs to slot: %d%n", slot);
     * }</pre>
     */
    CompletableFuture<Long> clusterKeySlot(String key);

    /**
     * Returns the hash slot for a given key, per the <code>CLUSTER KEYSLOT</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-keyslot/">valkey.io</a> for details.
     * @param key The key to determine the hash slot for.
     * @return The hash slot number for the specified key.
     * @example
     *     <pre>{@code
     * Long slot = client.clusterKeySlot(gs("myKey")).get();
     * System.out.printf("Key 'myKey' belongs to slot: %d%n", slot);
     * }</pre>
     */
    CompletableFuture<Long> clusterKeySlot(GlideString key);

    /**
     * Returns the node ID of the current node, per the <code>CLUSTER MYID</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-myid/">valkey.io</a> for details.
     * @return A {@link String} containing the node ID.
     * @example
     *     <pre>{@code
     * String nodeId = client.clusterMyId().get();
     * System.out.printf("Current node ID: %s%n", nodeId);
     * }</pre>
     */
    CompletableFuture<String> clusterMyId();

    /**
     * Returns the node ID of the node, per the <code>CLUSTER MYID</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-myid/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command.
     * @return A {@link String} or {@link ClusterValue} containing the node ID, depending on the
     *     routing.
     * @example
     *     <pre>{@code
     * ClusterValue<String> nodeIds = client.clusterMyId(ALL_NODES).get();
     * nodeIds.getMultiValue().forEach((node, id) ->
     *     System.out.printf("Node %s has ID: %s%n", node, id));
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterMyId(Route route);

    /**
     * Returns information about the nodes in the cluster in the format of the <code>CLUSTER NODES
     * </code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @apiNote When in cluster mode, the command will be routed to a random node.
     * @see <a href="https://valkey.io/commands/cluster-nodes/">valkey.io</a> for details.
     * @return A {@link String} containing node information.
     * @example
     *     <pre>{@code
     * String nodes = client.clusterNodes().get();
     * System.out.println(nodes);
     * // Output format:
     * // <id> <ip:port@cport> <flags> <master> <ping-sent> <pong-recv> <config-epoch> <link-state> <slot> <slot> ... <slot>
     * }</pre>
     */
    CompletableFuture<String> clusterNodes();

    /**
     * Returns information about the nodes in the cluster, per the <code>CLUSTER NODES</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-nodes/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command.
     * @return A {@link String} or {@link ClusterValue} containing node information, depending on the
     *     routing.
     * @example
     *     <pre>{@code
     * ClusterValue<String> nodes = client.clusterNodes(ALL_NODES).get();
     * nodes.getMultiValue().forEach((node, info) ->
     *     System.out.printf("Node %s info:%n%s%n", node, info));
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterNodes(Route route);

    /**
     * Returns an array of slot ranges assigned to each node in the cluster, per the <code>
     * CLUSTER SLOTS</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @apiNote This command is deprecated in Valkey 7.0.0 and above. Use {@link #clusterShards()}
     *     instead.
     * @see <a href="https://valkey.io/commands/cluster-slots/">valkey.io</a> for details.
     * @return An array of slot ranges and node information.
     * @example
     *     <pre>{@code
     * Object[] slots = client.clusterSlots().get();
     * for (Object slotInfo : slots) {
     *     // Each entry is an array: [start_slot, end_slot, [master_host, master_port, master_id], [replica1...], ...]
     *     System.out.println(Arrays.toString((Object[]) slotInfo));
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> clusterSlots();

    /**
     * Returns an array of slot ranges assigned to each node in the cluster, per the <code>
     * CLUSTER SLOTS</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @apiNote This command is deprecated in Valkey 7.0.0 and above. Use {@link
     *     #clusterShards(Route)} instead.
     * @see <a href="https://valkey.io/commands/cluster-slots/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command.
     * @return A {@link ClusterValue} containing an array of slot ranges and node information.
     * @example
     *     <pre>{@code
     * ClusterValue<Object[]> slots = client.clusterSlots(RANDOM).get();
     * Object[] slotInfo = slots.getSingleValue();
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object[]>> clusterSlots(Route route);

    /**
     * Returns information about the cluster shards, per the <code>CLUSTER SHARDS</code> command.
     *
     * @since Valkey 7.0.0 and above.
     * @apiNote When in cluster mode, the command will be routed to a random node.
     * @see <a href="https://valkey.io/commands/cluster-shards/">valkey.io</a> for details.
     * @return An array of maps, each containing information about a shard.
     * @example
     *     <pre>{@code
     * Object[] shards = client.clusterShards().get();
     * for (Object shard : shards) {
     *     System.out.println(shard);
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> clusterShards();

    /**
     * Returns information about the cluster shards, per the <code>CLUSTER SHARDS</code> command.
     *
     * @since Valkey 7.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-shards/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command.
     * @return A {@link ClusterValue} containing an array of maps, each containing information about a
     *     shard.
     * @example
     *     <pre>{@code
     * ClusterValue<Object[]> shards = client.clusterShards(RANDOM).get();
     * Object[] shardInfo = shards.getSingleValue();
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object[]>> clusterShards(Route route);

    /**
     * Assigns hash slots to the node, per the <code>CLUSTER ADDSLOTS</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-addslots/">valkey.io</a> for details.
     * @param slots An array of slot numbers to assign to the node.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterAddSlots(new long[]{1, 2, 3}).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterAddSlots(long[] slots);

    /**
     * Assigns hash slots to the node, per the <code>CLUSTER ADDSLOTS</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-addslots/">valkey.io</a> for details.
     * @param slots An array of slot numbers to assign to the node.
     * @param route Specifies the routing configuration for the command.
     * @return <code>OK</code> or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = client.clusterAddSlots(new long[]{1, 2, 3}, RANDOM).get();
     * assert result.getSingleValue().equals("OK");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterAddSlots(long[] slots, Route route);

    /**
     * Assigns hash slot ranges to the node, per the <code>CLUSTER ADDSLOTSRANGE</code> command.
     *
     * @since Valkey 7.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-addslotsrange/">valkey.io</a> for details.
     * @param slotRanges An array of slot range pairs where each entry represents a range with the key
     *     as the start slot and the value as the end slot.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * // Assign slots 1-100 and 500-600
     * String result = client.clusterAddSlotsRange(
     *     new Map.Entry[]{Map.entry(1L, 100L), Map.entry(500L, 600L)}).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterAddSlotsRange(Map.Entry<Long, Long>[] slotRanges);

    /**
     * Assigns hash slot ranges to the node, per the <code>CLUSTER ADDSLOTSRANGE</code> command.
     *
     * @since Valkey 7.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-addslotsrange/">valkey.io</a> for details.
     * @param slotRanges An array of slot range pairs where each entry represents a range with the key
     *     as the start slot and the value as the end slot.
     * @param route Specifies the routing configuration for the command.
     * @return <code>OK</code> or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = client.clusterAddSlotsRange(
     *     new Map.Entry[]{Map.entry(1L, 100L), Map.entry(500L, 600L)}, RANDOM).get();
     * assert result.getSingleValue().equals("OK");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterAddSlotsRange(
            Map.Entry<Long, Long>[] slotRanges, Route route);

    /**
     * Removes hash slots from the node, per the <code>CLUSTER DELSLOTS</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-delslots/">valkey.io</a> for details.
     * @param slots An array of slot numbers to remove from the node.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterDelSlots(new long[]{1, 2, 3}).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterDelSlots(long[] slots);

    /**
     * Removes hash slots from the node, per the <code>CLUSTER DELSLOTS</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-delslots/">valkey.io</a> for details.
     * @param slots An array of slot numbers to remove from the node.
     * @param route Specifies the routing configuration for the command.
     * @return <code>OK</code> or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = client.clusterDelSlots(new long[]{1, 2, 3}, RANDOM).get();
     * assert result.getSingleValue().equals("OK");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterDelSlots(long[] slots, Route route);

    /**
     * Removes hash slot ranges from the node, per the <code>CLUSTER DELSLOTSRANGE</code> command.
     *
     * @since Valkey 7.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-delslotsrange/">valkey.io</a> for details.
     * @param slotRanges An array of slot range pairs where each entry represents a range with the key
     *     as the start slot and the value as the end slot.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterDelSlotsRange(
     *     new Map.Entry[]{Map.entry(1L, 100L), Map.entry(500L, 600L)}).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterDelSlotsRange(Map.Entry<Long, Long>[] slotRanges);

    /**
     * Removes hash slot ranges from the node, per the <code>CLUSTER DELSLOTSRANGE</code> command.
     *
     * @since Valkey 7.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-delslotsrange/">valkey.io</a> for details.
     * @param slotRanges An array of slot range pairs where each entry represents a range with the key
     *     as the start slot and the value as the end slot.
     * @param route Specifies the routing configuration for the command.
     * @return <code>OK</code> or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = client.clusterDelSlotsRange(
     *     new Map.Entry[]{Map.entry(1L, 100L), Map.entry(500L, 600L)}, RANDOM).get();
     * assert result.getSingleValue().equals("OK");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterDelSlotsRange(
            Map.Entry<Long, Long>[] slotRanges, Route route);

    /**
     * Forces a node to start a manual failover, per the <code>CLUSTER FAILOVER</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-failover/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterFailover().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterFailover();

    /**
     * Forces a node to start a manual failover, per the <code>CLUSTER FAILOVER</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-failover/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command.
     * @return <code>OK</code> or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = client.clusterFailover(RANDOM).get();
     * assert result.getSingleValue().equals("OK");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterFailover(Route route);

    /**
     * Removes a node from the cluster, per the <code>CLUSTER FORGET</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-forget/">valkey.io</a> for details.
     * @param nodeId The ID of the node to remove.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterForget("node-id-123").get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterForget(String nodeId);

    /**
     * Removes a node from the cluster, per the <code>CLUSTER FORGET</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-forget/">valkey.io</a> for details.
     * @param nodeId The ID of the node to remove.
     * @param route Specifies the routing configuration for the command.
     * @return <code>OK</code> or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = client.clusterForget("node-id-123", ALL_NODES).get();
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterForget(String nodeId, Route route);

    /**
     * Configures the current node to start tracking specified node, per the <code>CLUSTER MEET
     * </code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-meet/">valkey.io</a> for details.
     * @param host The hostname or IP address of the node to meet.
     * @param port The port of the node to meet.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterMeet("192.168.1.100", 7000).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterMeet(String host, long port);

    /**
     * Configures the current node to start tracking specified node, per the <code>CLUSTER MEET
     * </code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-meet/">valkey.io</a> for details.
     * @param host The hostname or IP address of the node to meet.
     * @param port The port of the node to meet.
     * @param route Specifies the routing configuration for the command.
     * @return <code>OK</code> or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = client.clusterMeet("192.168.1.100", 7000, RANDOM).get();
     * assert result.getSingleValue().equals("OK");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterMeet(String host, long port, Route route);

    /**
     * Changes the replica node to replicate a different primary, per the <code>CLUSTER REPLICATE
     * </code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-replicate/">valkey.io</a> for details.
     * @param nodeId The ID of the primary node to replicate.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterReplicate("primary-node-id-123").get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterReplicate(String nodeId);

    /**
     * Changes the replica node to replicate a different primary, per the <code>CLUSTER REPLICATE
     * </code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-replicate/">valkey.io</a> for details.
     * @param nodeId The ID of the primary node to replicate.
     * @param route Specifies the routing configuration for the command.
     * @return <code>OK</code> or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = client.clusterReplicate("primary-node-id-123", RANDOM).get();
     * assert result.getSingleValue().equals("OK");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterReplicate(String nodeId, Route route);

    /**
     * Resets a node, per the <code>CLUSTER RESET</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-reset/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterReset().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterReset();

    /**
     * Resets a node, per the <code>CLUSTER RESET</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-reset/">valkey.io</a> for details.
     * @param mode The reset mode: <code>HARD</code> or <code>SOFT</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterReset(HARD).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterReset(ClusterResetMode mode);

    /**
     * Resets a node, per the <code>CLUSTER RESET</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-reset/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command.
     * @return <code>OK</code> or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = client.clusterReset(RANDOM).get();
     * assert result.getSingleValue().equals("OK");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterReset(Route route);

    /**
     * Resets a node, per the <code>CLUSTER RESET</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-reset/">valkey.io</a> for details.
     * @param mode The reset mode: <code>HARD</code> or <code>SOFT</code>.
     * @param route Specifies the routing configuration for the command.
     * @return <code>OK</code> or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = client.clusterReset(HARD, RANDOM).get();
     * assert result.getSingleValue().equals("OK");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterReset(ClusterResetMode mode, Route route);

    /**
     * Forces a node to save the cluster configuration to disk, per the <code>CLUSTER SAVECONFIG
     * </code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-saveconfig/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterSaveConfig().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterSaveConfig();

    /**
     * Forces a node to save the cluster configuration to disk, per the <code>CLUSTER SAVECONFIG
     * </code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-saveconfig/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command.
     * @return <code>OK</code> or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = client.clusterSaveConfig(ALL_NODES).get();
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterSaveConfig(Route route);

    /**
     * Gets the number of keys in the specified slot, per the <code>CLUSTER COUNTKEYSINSLOT</code>
     * command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-countkeysinslot/">valkey.io</a> for details.
     * @param slot The slot number.
     * @return The number of keys in the specified slot.
     * @example
     *     <pre>{@code
     * Long count = client.clusterCountKeysInSlot(5000).get();
     * System.out.printf("Slot 5000 contains %d keys%n", count);
     * }</pre>
     */
    CompletableFuture<Long> clusterCountKeysInSlot(long slot);

    /**
     * Gets the number of keys in the specified slot, per the <code>CLUSTER COUNTKEYSINSLOT</code>
     * command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-countkeysinslot/">valkey.io</a> for details.
     * @param slot The slot number.
     * @param route Specifies the routing configuration for the command.
     * @return The number of keys in the specified slot or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<Long> count = client.clusterCountKeysInSlot(5000, RANDOM).get();
     * System.out.printf("Slot 5000 contains %d keys%n", count.getSingleValue());
     * }</pre>
     */
    CompletableFuture<ClusterValue<Long>> clusterCountKeysInSlot(long slot, Route route);

    /**
     * Gets keys from the specified slot, per the <code>CLUSTER GETKEYSINSLOT</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-getkeysinslot/">valkey.io</a> for details.
     * @param slot The slot number.
     * @param count The maximum number of keys to return.
     * @return An array of keys in the specified slot.
     * @example
     *     <pre>{@code
     * String[] keys = client.clusterGetKeysInSlot(5000, 10).get();
     * System.out.printf("Found %d keys in slot 5000%n", keys.length);
     * }</pre>
     */
    CompletableFuture<String[]> clusterGetKeysInSlot(long slot, long count);

    /**
     * Gets keys from the specified slot, per the <code>CLUSTER GETKEYSINSLOT</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-getkeysinslot/">valkey.io</a> for details.
     * @param slot The slot number.
     * @param count The maximum number of keys to return.
     * @param route Specifies the routing configuration for the command.
     * @return An array of keys in the specified slot or a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<String[]> keys = client.clusterGetKeysInSlot(5000, 10, RANDOM).get();
     * System.out.printf("Found %d keys%n", keys.getSingleValue().length);
     * }</pre>
     */
    CompletableFuture<ClusterValue<String[]>> clusterGetKeysInSlot(
            long slot, long count, Route route);

    /** Enum for CLUSTER RESET modes. */
    enum ClusterResetMode {
        /** Soft reset mode. */
        SOFT,
        /** Hard reset mode. */
        HARD
    }

    // Additional lower-priority commands follow similar patterns
    // These are intentionally left as stubs for now and can be implemented incrementally

    /**
     * Returns the shard ID of the node, per the <code>CLUSTER MYSHARDID</code> command.
     *
     * @since Valkey 7.2.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-myshardid/">valkey.io</a> for details.
     * @return A {@link String} containing the shard ID.
     * @example
     *     <pre>{@code
     * String shardId = client.clusterMyShardId().get();
     * System.out.printf("Current shard ID: %s%n", shardId);
     * }</pre>
     */
    CompletableFuture<String> clusterMyShardId();

    /**
     * Returns information about the replicas of a given node, per the <code>CLUSTER REPLICAS</code>
     * command.
     *
     * @since Valkey 5.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-replicas/">valkey.io</a> for details.
     * @param nodeId The ID of the primary node.
     * @return A {@link String} containing replica information.
     * @example
     *     <pre>{@code
     * String replicas = client.clusterReplicas("node-id-123").get();
     * System.out.println(replicas);
     * }</pre>
     */
    CompletableFuture<String> clusterReplicas(String nodeId);

    /**
     * Gets information about cluster link connections, per the <code>CLUSTER LINKS</code> command.
     *
     * @since Valkey 7.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-links/">valkey.io</a> for details.
     * @return An array containing information about cluster links.
     * @example
     *     <pre>{@code
     * Object[] links = client.clusterLinks().get();
     * System.out.printf("Found %d cluster links%n", links.length);
     * }</pre>
     */
    CompletableFuture<Object[]> clusterLinks();

    /**
     * Advances the cluster config epoch, per the <code>CLUSTER BUMPEPOCH</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-bumpepoch/">valkey.io</a> for details.
     * @return <code>OK</code> or error message.
     * @example
     *     <pre>{@code
     * String result = client.clusterBumpEpoch().get();
     * }</pre>
     */
    CompletableFuture<String> clusterBumpEpoch();

    /**
     * Binds a slot to a specific node, per the <code>CLUSTER SETSLOT</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-setslot/">valkey.io</a> for details.
     * @param slot The slot number.
     * @param subcommand The subcommand (e.g., "IMPORTING", "MIGRATING", "STABLE", "NODE").
     * @param nodeId The node ID (required for NODE subcommand).
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterSetSlot(5000, "NODE", "node-id-123").get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterSetSlot(long slot, String subcommand, String nodeId);

    /**
     * Sets the configuration epoch for a node, per the <code>CLUSTER SET-CONFIG-EPOCH</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-set-config-epoch/">valkey.io</a> for details.
     * @param epoch The configuration epoch.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterSetConfigEpoch(5).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterSetConfigEpoch(long epoch);

    /**
     * Gets the number of failure reports for a given node, per the <code>
     * CLUSTER COUNT-FAILURE-REPORTS</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-count-failure-reports/">valkey.io</a> for
     *     details.
     * @param nodeId The ID of the node.
     * @return The number of failure reports.
     * @example
     *     <pre>{@code
     * Long count = client.clusterCountFailureReports("node-id-123").get();
     * System.out.printf("Node has %d failure reports%n", count);
     * }</pre>
     */
    CompletableFuture<Long> clusterCountFailureReports(String nodeId);

    /**
     * Flushes all slots from a node, per the <code>CLUSTER FLUSHSLOTS</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-flushslots/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.clusterFlushSlots().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterFlushSlots();

    /**
     * Enables read queries for a connection to a cluster replica node, per the <code>READONLY</code>
     * command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/readonly/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.readOnly().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> readOnly();

    /**
     * Disables read queries for a connection to a cluster replica node, per the <code>READWRITE
     * </code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/readwrite/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.readWrite().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> readWrite();

    /**
     * Enables redirection mode in a cluster, per the <code>ASKING</code> command.
     *
     * @since Valkey 3.0.0 and above.
     * @see <a href="https://valkey.io/commands/asking/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String result = client.asking().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> asking();
}
