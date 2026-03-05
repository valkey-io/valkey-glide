/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for cluster management and information retrieval for cluster clients.
 *
 * <p>These commands provide comprehensive control over Valkey cluster operations, enabling
 * applications to inspect cluster state, view topology information, and retrieve node details.
 *
 * @see <a href="https://valkey.io/commands/?group=cluster">Cluster Commands</a>
 */
public interface ClusterManagementClusterCommands {

    /**
     * Gets information and statistics about the cluster state.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/cluster-info/">valkey.io</a> for details.
     * @return A <code>String</code> containing cluster state information with key-value pairs
     *     separated by newlines. Key fields include:
     *     <ul>
     *       <li><code>cluster_state</code> - State of the cluster (ok or fail)
     *       <li><code>cluster_slots_assigned</code> - Number of slots assigned
     *       <li><code>cluster_slots_ok</code> - Number of slots in OK state
     *       <li><code>cluster_slots_pfail</code> - Number of slots in PFAIL state
     *       <li><code>cluster_slots_fail</code> - Number of slots in FAIL state
     *       <li><code>cluster_known_nodes</code> - Total number of known nodes
     *       <li><code>cluster_size</code> - Number of primary nodes serving at least one slot
     *       <li><code>cluster_current_epoch</code> - Current cluster epoch
     *       <li><code>cluster_my_epoch</code> - Config epoch of the current node
     *       <li><code>cluster_stats_messages_sent</code> - Number of messages sent
     *       <li><code>cluster_stats_messages_received</code> - Number of messages received
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * String info = clusterClient.clusterInfo().get();
     * System.out.println(info);
     * // Output:
     * // cluster_state:ok
     * // cluster_slots_assigned:16384
     * // cluster_slots_ok:16384
     * // cluster_slots_pfail:0
     * // cluster_slots_fail:0
     * // cluster_known_nodes:3
     * // cluster_size:3
     * // ...
     * }</pre>
     */
    CompletableFuture<String> clusterInfo();

    /**
     * Gets information and statistics about the cluster state.
     *
     * @see <a href="https://valkey.io/commands/cluster-info/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return When specifying a single-node route, returns a <code>String</code> containing cluster
     *     state information. When specifying a multi-node route, returns a <code>
     *     Map{@literal <String, String>}</code> with each node address as the key and its cluster
     *     state information as the value.
     * @example
     *     <pre>{@code
     * ClusterValue<String> infoResult = clusterClient.clusterInfo(ALL_PRIMARIES).get();
     * // Command sent to all primary nodes, expecting MultiValue result
     * for (Map.Entry<String, String> entry : infoResult.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "]:");
     *     System.out.println(entry.getValue());
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterInfo(Route route);

    /**
     * Gets a list of all nodes in the cluster and their attributes.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/cluster-nodes/">valkey.io</a> for details.
     * @return A <code>String</code> containing information about all nodes in the cluster. Each line
     *     represents a node with space-separated fields:
     *     <ul>
     *       <li><code>node-id</code> - 40-character hex string identifier
     *       <li><code>ip:port@cport</code> - Node address (IPv4, IPv6, or hostname). The @cport is
     *           the cluster port used for node-to-node communication
     *       <li><code>flags</code> - Comma-separated list (myself, master, slave, fail?, fail,
     *           handshake, noaddr, nofailover, noflags)
     *       <li><code>master-id</code> - Node ID of the master (or "-" if this node is a master)
     *       <li><code>ping-sent</code> - Milliseconds unix time of last ping sent (0 if no ping sent)
     *       <li><code>pong-recv</code> - Milliseconds unix time of last pong received
     *       <li><code>config-epoch</code> - Config epoch of this node
     *       <li><code>link-state</code> - State of the node-to-node link (connected or disconnected)
     *       <li><code>slots</code> - Hash slots served by this node (e.g., 0-5460 or individual slots
     *           like 1 2 3)
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * String nodes = clusterClient.clusterNodes().get();
     * System.out.println(nodes);
     * // Output (example):
     * // 07c37dfeb235213a872192d90877d0cd55635b91 127.0.0.1:30004@31004 slave
     * // e7d1eecce10fd6bb5eb35b9f99a514335d9ba9ca 0 1426238317239 4 connected
     * // 67ed2db8d677e59ec4a4cefb06858cf2a1a89fa1 127.0.0.1:30002@31002 master - 0
     * // 1426238316232 2 connected 5461-10922
     * // ...
     * }</pre>
     */
    CompletableFuture<String> clusterNodes();

    /**
     * Gets a list of all nodes in the cluster and their attributes.
     *
     * @see <a href="https://valkey.io/commands/cluster-nodes/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return When specifying a single-node route, returns a <code>String</code> containing node
     *     information. When specifying a multi-node route, returns a <code>Map{@literal <String,
     *     String>}</code> with each node address as the key and its node listing as the value.
     * @example
     *     <pre>{@code
     * ClusterValue<String> nodesResult = clusterClient.clusterNodes(RANDOM).get();
     * // Command sent to a random node, expecting SingleValue result
     * System.out.println(nodesResult.getSingleValue());
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterNodes(Route route);

    /**
     * Returns details about the shards of the cluster.<br>
     * The command will be routed to a random node.
     *
     * @apiNote Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-shards/">valkey.io</a> for details.
     * @return An <code>array</code> of maps, where each map represents a shard and contains the
     *     following keys:
     *     <ul>
     *       <li><code>"slots"</code> - An array of slot ranges (each range is a two-element array
     *           [start, end])
     *       <li><code>"nodes"</code> - An array of node objects in this shard, each containing:
     *           <ul>
     *             <li><code>"id"</code> - Node ID (40-character hex string)
     *             <li><code>"endpoint"</code> - Node address (IP and port)
     *             <li><code>"ip"</code> - Node IP address (IPv4 or IPv6)
     *             <li><code>"port"</code> - Node port number
     *             <li><code>"role"</code> - Node role ("master" or "replica")
     *             <li><code>"replication-offset"</code> - Replication offset
     *             <li><code>"health"</code> - Node health status ("online", "failed", "loading")
     *           </ul>
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Object[] shards = clusterClient.clusterShards().get();
     * for (Object shardObj : shards) {
     *     Map<String, Object> shard = (Map<String, Object>) shardObj;
     *     Object[] slots = (Object[]) shard.get("slots");
     *     System.out.println("Shard slots: ");
     *     for (Object slotRange : slots) {
     *         Object[] range = (Object[]) slotRange;
     *         System.out.println("  " + range[0] + "-" + range[1]);
     *     }
     *
     *     Object[] nodes = (Object[]) shard.get("nodes");
     *     for (Object nodeObj : nodes) {
     *         Map<String, Object> node = (Map<String, Object>) nodeObj;
     *         System.out.println("  Node: " + node.get("id"));
     *         System.out.println("    Endpoint: " + node.get("endpoint"));
     *         System.out.println("    Role: " + node.get("role"));
     *     }
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> clusterShards();

    /**
     * Returns details about the shards of the cluster.
     *
     * @apiNote Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-shards/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return When specifying a single-node route, returns an <code>array</code> of shard information
     *     maps. When specifying a multi-node route, returns a <code>Map{@literal <String,
     *     Object[]>}</code> with each node address as the key and its shard details array as the
     *     value.
     * @example
     *     <pre>{@code
     * ClusterValue<Object[]> shardsResult = clusterClient.clusterShards(ALL_NODES).get();
     * // Command sent to all nodes, expecting MultiValue result
     * for (Map.Entry<String, Object[]> entry : shardsResult.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "] shards: " + Arrays.toString(entry.getValue()));
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object[]>> clusterShards(Route route);

    /**
     * Returns the mapping of hash slots to nodes.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/cluster-slots/">valkey.io</a> for details.
     * @return A <code>two-dimensional array</code> where each inner array represents a slot range and
     *     contains the following elements:
     *     <ul>
     *       <li><code>[0]</code> (Long) - Start slot number
     *       <li><code>[1]</code> (Long) - End slot number
     *       <li><code>[2..]</code> (Object[]) - Node information arrays, each containing:
     *           <ul>
     *             <li><code>[0]</code> (String) - Node IP address (IPv4, IPv6, or hostname)
     *             <li><code>[1]</code> (Long) - Node port
     *             <li><code>[2]</code> (String) - Node ID (40-character hex string)
     *             <li><code>[3..]</code> (Optional) - Additional hostname/IP entries for this node
     *           </ul>
     *     </ul>
     *     The first node in each slot range is the master, followed by its replicas.
     * @example
     *     <pre>{@code
     * Object[][] slots = clusterClient.clusterSlots().get();
     * for (Object[] slotRange : slots) {
     *     long startSlot = (long) slotRange[0];
     *     long endSlot = (long) slotRange[1];
     *     System.out.println("Slots " + startSlot + "-" + endSlot + ":");
     *
     *     // First node is the master
     *     Object[] masterInfo = (Object[]) slotRange[2];
     *     System.out.println("  Master: " + masterInfo[0] + ":" + masterInfo[1] + " (" + masterInfo[2] + ")");
     *
     *     // Remaining nodes are replicas
     *     for (int i = 3; i < slotRange.length; i++) {
     *         Object[] replicaInfo = (Object[]) slotRange[i];
     *         System.out.println("  Replica: " + replicaInfo[0] + ":" + replicaInfo[1] + " (" + replicaInfo[2] + ")");
     *     }
     * }
     * }</pre>
     */
    CompletableFuture<Object[][]> clusterSlots();

    /**
     * Returns the mapping of hash slots to nodes.
     *
     * @see <a href="https://valkey.io/commands/cluster-slots/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return When specifying a single-node route, returns a <code>two-dimensional array</code> of
     *     slot mappings. When specifying a multi-node route, returns a <code>Map{@literal <String,
     *     Object[][]>}</code> with each node address as the key and its slot mapping array as the
     *     value.
     * @example
     *     <pre>{@code
     * ClusterValue<Object[][]> slotsResult = clusterClient.clusterSlots(RANDOM).get();
     * // Command sent to a random node, expecting SingleValue result
     * Object[][] slots = slotsResult.getSingleValue();
     * System.out.println("Total slot ranges: " + slots.length);
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object[][]>> clusterSlots(Route route);

    /**
     * Returns information about the TCP links to and from each node in the cluster.<br>
     * The command will be routed to a random node.
     *
     * @apiNote Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-links/">valkey.io</a> for details.
     * @return An <code>array</code> of maps, where each map represents a cluster link and contains
     *     the following keys:
     *     <ul>
     *       <li><code>"direction"</code> - Link direction ("to" or "from")
     *       <li><code>"node"</code> - Node ID (40-character hex string) at the other end of the link
     *       <li><code>"create-time"</code> - Timestamp when the link was created (milliseconds)
     *       <li><code>"events"</code> - Event flags for the link (e.g., "r" for readable, "w" for
     *           writable)
     *       <li><code>"send-buffer-allocated"</code> - Allocated size of the send buffer
     *       <li><code>"send-buffer-used"</code> - Size of the send buffer currently in use
     *       <li><code>"recv-buffer-allocated"</code> - Allocated size of the receive buffer
     *       <li><code>"recv-buffer-used"</code> - Size of the receive buffer currently in use
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Object[] links = clusterClient.clusterLinks().get();
     * for (Object linkObj : links) {
     *     Map<String, Object> link = (Map<String, Object>) linkObj;
     *     System.out.println("Link direction: " + link.get("direction"));
     *     System.out.println("  Node: " + link.get("node"));
     *     System.out.println("  Send buffer used: " + link.get("send-buffer-used"));
     *     System.out.println("  Recv buffer used: " + link.get("recv-buffer-used"));
     * }
     * }</pre>
     */
    CompletableFuture<Object[]> clusterLinks();

    /**
     * Returns information about the TCP links to and from each node in the cluster.
     *
     * @apiNote Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-links/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return When specifying a single-node route, returns an <code>array</code> of link information
     *     maps. When specifying a multi-node route, returns a <code>Map{@literal <String, Object[]>}
     *     </code> with each node address as the key and its link details array as the value.
     * @example
     *     <pre>{@code
     * ClusterValue<Object[]> linksResult = clusterClient.clusterLinks(ALL_PRIMARIES).get();
     * // Command sent to all primary nodes, expecting MultiValue result
     * for (Map.Entry<String, Object[]> entry : linksResult.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "] has " + entry.getValue().length + " links");
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object[]>> clusterLinks(Route route);

    /**
     * Returns the unique identifier (ID) of the current node.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/cluster-myid/">valkey.io</a> for details.
     * @return A <code>String</code> containing the unique 40-character identifier of the node
     *     executing the command. This ID remains constant for the lifetime of the node and is used in
     *     various cluster commands to reference this specific node.
     * @example
     *     <pre>{@code
     * String nodeId = clusterClient.clusterMyId().get();
     * System.out.println("Current node ID: " + nodeId);
     * // Output: Current node ID: 07c37dfeb235213a872192d90877d0cd55635b91
     * }</pre>
     */
    CompletableFuture<String> clusterMyId();

    /**
     * Returns the unique identifier (ID) of the node(s).
     *
     * @see <a href="https://valkey.io/commands/cluster-myid/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return When specifying a single-node route, returns a <code>String</code> containing the node
     *     ID. When specifying a multi-node route, returns a <code>Map{@literal <String, String>}
     *     </code> with each node address as the key and its node ID as the value.
     * @example
     *     <pre>{@code
     * ClusterValue<String> idsResult = clusterClient.clusterMyId(ALL_NODES).get();
     * // Command sent to all nodes, expecting MultiValue result
     * for (Map.Entry<String, String> entry : idsResult.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "] ID: " + entry.getValue());
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterMyId(Route route);

    /**
     * Returns the shard ID of the current node.<br>
     * The command will be routed to a random node.
     *
     * <p>In Valkey, a shard is a set of nodes that replicate the same data (one primary and zero or
     * more replicas). The shard ID is a unique identifier for this replication group and remains
     * constant even during failovers when the primary role transfers to a replica.
     *
     * @apiNote Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/cluster-myshardid/">valkey.io</a> for details.
     * @return A <code>String</code> containing the unique shard identifier of the current node. All
     *     nodes in the same shard (primary and replicas) share the same shard ID.
     * @example
     *     <pre>{@code
     * String shardId = clusterClient.clusterMyShardId().get();
     * System.out.println("Current shard ID: " + shardId);
     * // Output: Current shard ID: 3c3a0c74aae0b56170ccb03a76b60cfe7dc1912e
     * }</pre>
     */
    CompletableFuture<String> clusterMyShardId();

    /**
     * Returns the shard ID of the node(s).
     *
     * <p>In Valkey, a shard is a set of nodes that replicate the same data (one primary and zero or
     * more replicas). The shard ID is a unique identifier for this replication group and remains
     * constant even during failovers when the primary role transfers to a replica.
     *
     * @apiNote Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/cluster-myshardid/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return When specifying a single-node route, returns a <code>String</code> containing the shard
     *     ID. When specifying a multi-node route, returns a <code>Map{@literal <String, String>}
     *     </code> with each node address as the key and its shard ID as the value.
     * @example
     *     <pre>{@code
     * ClusterValue<String> shardIdsResult = clusterClient.clusterMyShardId(ALL_PRIMARIES).get();
     * // Command sent to all primary nodes, expecting MultiValue result
     * for (Map.Entry<String, String> entry : shardIdsResult.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "] Shard ID: " + entry.getValue());
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterMyShardId(Route route);

    /**
     * Returns the hash slot number for the given key.<br>
     * The command will be routed to a random node.
     *
     * <p>The hash slot determines which node in a cluster is responsible for a given key. Valkey
     * Cluster uses 16384 hash slots (0-16383). Keys are mapped to slots using CRC16 of the key modulo
     * 16384.
     *
     * @see <a href="https://valkey.io/commands/cluster-keyslot/">valkey.io</a> for details.
     * @param key The key to get the hash slot for.
     * @return The hash slot number (0-16383) for the given key.
     * @example
     *     <pre>{@code
     * long slot = clusterClient.clusterKeySlot("mykey").get();
     * System.out.println("Key 'mykey' belongs to slot: " + slot);
     * // Output: Key 'mykey' belongs to slot: 14687
     * }</pre>
     */
    CompletableFuture<Long> clusterKeySlot(String key);

    /**
     * Returns the hash slot number for the given binary key.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/cluster-keyslot/">valkey.io</a> for details.
     * @param key The binary key to get the hash slot for.
     * @return The hash slot number (0-16383) for the given key.
     * @example
     *     <pre>{@code
     * long slot = clusterClient.clusterKeySlot(gs("mykey")).get();
     * System.out.println("Binary key belongs to slot: " + slot);
     * }</pre>
     */
    CompletableFuture<Long> clusterKeySlot(GlideString key);

    /**
     * Returns the number of keys in the specified hash slot.<br>
     * The command will be routed to the node responsible for the specified slot.
     *
     * @see <a href="https://valkey.io/commands/cluster-countkeysinslot/">valkey.io</a> for details.
     * @param slot The hash slot number (0-16383) to count keys in.
     * @return The number of keys in the specified slot.
     * @example
     *     <pre>{@code
     * long keyCount = clusterClient.clusterCountKeysInSlot(12345).get();
     * System.out.println("Number of keys in slot 12345: " + keyCount);
     * // Output: Number of keys in slot 12345: 42
     * }</pre>
     */
    CompletableFuture<Long> clusterCountKeysInSlot(long slot);

    /**
     * Returns an array of keys in the specified hash slot.<br>
     * The command will be routed to the node responsible for the specified slot.
     *
     * @see <a href="https://valkey.io/commands/cluster-getkeysinslot/">valkey.io</a> for details.
     * @param slot The hash slot number (0-16383) to retrieve keys from.
     * @param count The maximum number of keys to return. Must be positive.
     * @return An array of up to <code>count</code> keys belonging to the specified slot. Returns an
     *     empty array if the slot is empty or does not exist.
     * @example
     *     <pre>{@code
     * String[] keys = clusterClient.clusterGetKeysInSlot(12345, 10).get();
     * System.out.println("First 10 keys in slot 12345:");
     * for (String key : keys) {
     *     System.out.println("  " + key);
     * }
     * }</pre>
     */
    CompletableFuture<String[]> clusterGetKeysInSlot(long slot, long count);

    /**
     * Returns an array of binary keys in the specified hash slot.<br>
     * The command will be routed to the node responsible for the specified slot.
     *
     * @see <a href="https://valkey.io/commands/cluster-getkeysinslot/">valkey.io</a> for details.
     * @param slot The hash slot number (0-16383) to retrieve keys from.
     * @param count The maximum number of keys to return. Must be positive.
     * @return An array of up to <code>count</code> binary keys belonging to the specified slot.
     *     Returns an empty array if the slot is empty or does not exist.
     * @example
     *     <pre>{@code
     * GlideString[] keys = clusterClient.clusterGetKeysInSlotBinary(12345, 10).get();
     * System.out.println("First 10 binary keys in slot 12345: " + keys.length + " keys");
     * }</pre>
     */
    CompletableFuture<GlideString[]> clusterGetKeysInSlotBinary(long slot, long count);

    /**
     * Assigns hash slots to the current node.<br>
     * The command will be routed to the node executing the command.
     *
     * @see <a href="https://valkey.io/commands/cluster-addslots/">valkey.io</a> for details.
     * @param slots An array of hash slot numbers (0-16383) to assign to the node. Slots must not
     *     already be assigned to any node.
     * @return <code>"OK"</code> if the slots were successfully assigned.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterAddSlots(new long[]{1, 2, 3, 4, 5}).get();
     * System.out.println("Slots assigned: " + result); // Output: Slots assigned: OK
     * }</pre>
     */
    CompletableFuture<String> clusterAddSlots(long[] slots);

    /**
     * Assigns hash slot ranges to the current node.<br>
     * The command will be routed to the node executing the command.
     *
     * <p>This command is more efficient than {@link #clusterAddSlots} when assigning multiple
     * contiguous slot ranges, as it uses the CLUSTER ADDSLOTSRANGE command introduced in Valkey 7.0.
     *
     * @apiNote Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-addslotsrange/">valkey.io</a> for details.
     * @param slotRanges A 2D array where each sub-array contains two elements: [start_slot, end_slot]
     *     representing an inclusive range of slots to assign. All slots must be between 0-16383 and
     *     not already assigned.
     * @return <code>"OK"</code> if the slot ranges were successfully assigned.
     * @example
     *     <pre>{@code
     * // Assign slots 0-100, 1000-2000, and 5000-5999
     * long[][] ranges = {{0, 100}, {1000, 2000}, {5000, 5999}};
     * String result = clusterClient.clusterAddSlotsRange(ranges).get();
     * System.out.println("Slot ranges assigned: " + result); // Output: Slot ranges assigned: OK
     * }</pre>
     */
    CompletableFuture<String> clusterAddSlotsRange(long[][] slotRanges);

    /**
     * Removes hash slots from the current node.<br>
     * The command will be routed to the node executing the command.
     *
     * <p>Once a slot is removed, it becomes unassigned and can be assigned to another node or
     * reassigned to the current node. This is typically used during cluster reconfiguration or before
     * removing a node from the cluster.
     *
     * @see <a href="https://valkey.io/commands/cluster-delslots/">valkey.io</a> for details.
     * @param slots An array of hash slot numbers (0-16383) to remove from the node. Slots must
     *     currently be assigned to this node.
     * @return <code>"OK"</code> if the slots were successfully removed.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterDelSlots(new long[]{1, 2, 3, 4, 5}).get();
     * System.out.println("Slots removed: " + result); // Output: Slots removed: OK
     * }</pre>
     */
    CompletableFuture<String> clusterDelSlots(long[] slots);

    /**
     * Removes hash slot ranges from the current node.<br>
     * The command will be routed to the node executing the command.
     *
     * <p>This command is more efficient than {@link #clusterDelSlots} when removing multiple
     * contiguous slot ranges, as it uses the CLUSTER DELSLOTSRANGE command introduced in Valkey 7.0.
     *
     * @apiNote Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-delslotsrange/">valkey.io</a> for details.
     * @param slotRanges A 2D array where each sub-array contains two elements: [start_slot, end_slot]
     *     representing an inclusive range of slots to remove. All slots must be between 0-16383 and
     *     currently assigned to this node.
     * @return <code>"OK"</code> if the slot ranges were successfully removed.
     * @example
     *     <pre>{@code
     * // Remove slots 0-100, 1000-2000, and 5000-5999
     * long[][] ranges = {{0, 100}, {1000, 2000}, {5000, 5999}};
     * String result = clusterClient.clusterDelSlotsRange(ranges).get();
     * System.out.println("Slot ranges removed: " + result); // Output: Slot ranges removed: OK
     * }</pre>
     */
    CompletableFuture<String> clusterDelSlotsRange(long[][] slotRanges);
}
