/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

/** Redis protocol version enumeration for Jedis compatibility. */
public enum RedisProtocol {
    RESP2,
    RESP3;

    /**
     * Convert to GLIDE protocol enum.
     *
     * @return corresponding GLIDE protocol
     */
    public glide.api.models.configuration.ProtocolVersion toGlideProtocol() {
        switch (this) {
            case RESP2:
                return glide.api.models.configuration.ProtocolVersion.RESP2;
            case RESP3:
                return glide.api.models.configuration.ProtocolVersion.RESP3;
            default:
                return glide.api.models.configuration.ProtocolVersion.RESP2;
        }
    }
}
