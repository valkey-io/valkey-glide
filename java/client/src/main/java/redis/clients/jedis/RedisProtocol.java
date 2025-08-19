/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.models.configuration.ProtocolVersion;

/**
 * RedisProtocol compatibility enum for Valkey GLIDE wrapper. Represents different Redis protocol
 * versions.
 */
public enum RedisProtocol {
    RESP2,
    RESP3;

    public static RedisProtocol getDefault() {
        return RESP2;
    }

    /** Convert to GLIDE ProtocolVersion. */
    public ProtocolVersion toGlideProtocol() {
        switch (this) {
            case RESP2:
                return ProtocolVersion.RESP2;
            case RESP3:
                return ProtocolVersion.RESP3;
            default:
                return ProtocolVersion.RESP2;
        }
    }
}
