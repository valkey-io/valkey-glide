/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

/**
 * Enum representing cache eviction policies.
 *
 * Eviction policies determine which entries are removed from the cache
 * when the cache reaches its maximum memory limit.
 */
export enum EvictionPolicy {
    /**
     * Least Recently Used eviction policy.
     * Removes the entries that have been accessed least recently.
     */
    LRU = 0,

    /**
     * Least Frequently Used eviction policy.
     * Removes the entries that have been accessed least frequently.
     */
    LFU = 1,
}
