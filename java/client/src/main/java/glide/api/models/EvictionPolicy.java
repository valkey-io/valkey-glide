/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

/**
 * Represents the eviction policy for client-side caching. Note: Numeric values must match the
 * protobuf EvictionPolicy enum in connection_request.proto
 */
public enum EvictionPolicy {
    /** Least Recently Used - evicts the least recently accessed entries first. */
    LRU(0),
    /** Least Frequently Used - evicts the least frequently accessed entries first. */
    LFU(1);

    private final int value;

    EvictionPolicy(int value) {
        this.value = value;
    }

    /**
     * Gets the numeric value of the eviction policy.
     *
     * @return The numeric value.
     */
    public int getValue() {
        return value;
    }
}
