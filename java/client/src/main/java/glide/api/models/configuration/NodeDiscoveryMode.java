/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

/**
 * Controls how the client discovers node roles and topology in standalone mode.
 *
 * <ul>
 *   <li>{@link #STANDARD} - Default: verify node roles via INFO REPLICATION, use only provided
 *       addresses.
 *   <li>{@link #STATIC} - Skip role detection entirely. Trust provided addresses as-is; first
 *       address is primary. Use when connecting through a proxy (e.g., Envoy) or when the topology
 *       is known and static.
 *   <li>{@link #DISCOVER_ALL} - Discover full topology (primary + all replicas) from any starting
 *       node. Provide any single node address and the client will find and connect to all other
 *       nodes.
 * </ul>
 */
public enum NodeDiscoveryMode {
    /** Default: verify node roles via INFO REPLICATION, use only provided addresses. */
    STANDARD,
    /**
     * Skip role detection entirely. Trust provided addresses as-is; first address is primary. Use
     * when connecting through a proxy (e.g., Envoy) or when the topology is known and static.
     *
     * <p>Note: Do not set {@code clientName} when using this mode with a proxy.
     */
    STATIC,
    /**
     * Discover full topology (primary + all replicas) from any starting node. Provide any single node
     * address and the client will find and connect to all other nodes.
     */
    DISCOVER_ALL
}
