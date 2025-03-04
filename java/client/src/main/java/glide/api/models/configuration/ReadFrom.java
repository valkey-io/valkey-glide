/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

/** Represents the client's read from strategy. */
public enum ReadFrom {
    /** Always get from primary, in order to get the freshest data. */
    PRIMARY,
    /**
     * Spread the requests between all replicas in a round-robin manner. If no replica is available,
     * route the requests to the primary.
     */
    PREFER_REPLICA,
    /**
     * Spread the read requests between replicas in the same client's AZ (Aviliablity zone) in a
     * round-robin manner, falling back to other replicas or the primary if needed.
     */
    AZ_AFFINITY,
    /**
     * Spread the read requests among nodes within the client's Availability Zone (AZ) in a round
     * robin manner, prioritizing local replicas, then the local primary, and falling back to any
     * replica or the primary if needed.
     */
    AZ_AFFINITY_REPLICAS_AND_PRIMARY,
}
