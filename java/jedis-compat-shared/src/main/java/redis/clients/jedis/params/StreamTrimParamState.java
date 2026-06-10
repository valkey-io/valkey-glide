/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import glide.api.models.commands.stream.StreamTrimOptions;

/**
 * Shared trim field state for {@link AbstractXAddParams} and {@link AbstractXTrimParams}. Both
 * command param types expose the same MAXLEN / MINID / LIMIT semantics; this type holds the
 * conversion to GLIDE {@link StreamTrimOptions} in one place.
 */
final class StreamTrimParamState {

    Long maxLen;
    String minId;
    Boolean exactTrimming;
    Long limit;

    /**
     * @param required when true (XTRIM), at least one of maxLen or minId must be set
     * @return trim options, or null when optional (XADD) and no trim fields are set
     */
    StreamTrimOptions toStreamTrimOptions(boolean required) {
        boolean exact = exactTrimming != null && exactTrimming;

        if (maxLen != null) {
            if (limit != null) {
                return new StreamTrimOptions.MaxLen(maxLen, limit);
            }
            return new StreamTrimOptions.MaxLen(exact, maxLen);
        }
        if (minId != null) {
            if (limit != null) {
                return new StreamTrimOptions.MinId(minId, limit);
            }
            return new StreamTrimOptions.MinId(exact, minId);
        }
        if (required) {
            throw new IllegalArgumentException("XTrimParams must specify either maxLen or minId");
        }
        return null;
    }
}
