/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.args;

import redis.clients.jedis.util.SafeEncoder;

/**
 * Options for sorted set operations that specify whether to select minimum or maximum scored
 * elements. Used in commands like ZMPOP, BZMPOP.
 *
 * <p>This enum is compatible with Jedis SortedSetOption.
 */
public enum SortedSetOption implements Rawable {
    MIN,
    MAX;

    private final byte[] raw;

    private SortedSetOption() {
        raw = SafeEncoder.encode(name());
    }

    @Override
    public byte[] getRaw() {
        return raw;
    }
}
