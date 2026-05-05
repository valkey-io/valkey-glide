/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import glide.api.models.commands.stream.StreamTrimOptions;
import redis.clients.jedis.StreamEntryID;

/**
 * Parameters for XTRIM command in Jedis compatibility layer. Provides a fluent API for trimming
 * streams by maximum length or minimum ID.
 */
public class XTrimParams {

    private Long maxLen;
    private String minId;
    private Boolean exactTrimming;
    private Long limit;

    public static XTrimParams xTrimParams() {
        return new XTrimParams();
    }

    /**
     * Trim the stream to approximately the specified maximum length using MAXLEN ~ threshold.
     *
     * @param maxLen maximum length
     * @return this
     */
    public XTrimParams maxLen(long maxLen) {
        this.maxLen = maxLen;
        this.exactTrimming = false;
        return this;
    }

    /**
     * Trim the stream to exactly the specified maximum length using MAXLEN = threshold.
     *
     * @param maxLen maximum length
     * @return this
     */
    public XTrimParams maxLenExact(long maxLen) {
        this.maxLen = maxLen;
        this.exactTrimming = true;
        return this;
    }

    /**
     * Trim entries with IDs lower than minId using MINID ~ threshold.
     *
     * @param minId minimum ID threshold
     * @return this
     */
    public XTrimParams minId(String minId) {
        this.minId = minId;
        this.exactTrimming = false;
        return this;
    }

    /**
     * Trim entries with IDs lower than minId using MINID ~ threshold.
     *
     * @param minId minimum ID threshold
     * @return this
     */
    public XTrimParams minId(StreamEntryID minId) {
        this.minId = minId != null ? minId.toString() : null;
        this.exactTrimming = false;
        return this;
    }

    /**
     * Trim entries with IDs lower than minId using MINID = threshold (exact).
     *
     * @param minId minimum ID threshold
     * @return this
     */
    public XTrimParams minIdExact(String minId) {
        this.minId = minId;
        this.exactTrimming = true;
        return this;
    }

    /**
     * Trim entries with IDs lower than minId using MINID = threshold (exact).
     *
     * @param minId minimum ID threshold
     * @return this
     */
    public XTrimParams minIdExact(StreamEntryID minId) {
        this.minId = minId != null ? minId.toString() : null;
        this.exactTrimming = true;
        return this;
    }

    /**
     * Set the LIMIT count for trimming.
     *
     * @param limit maximum number of entries to trim
     * @return this
     */
    public XTrimParams limit(long limit) {
        this.limit = limit;
        return this;
    }

    // Getters for internal use
    public Long getMaxLen() {
        return maxLen;
    }

    public String getMinId() {
        return minId;
    }

    public Boolean getExactTrimming() {
        return exactTrimming;
    }

    public Long getLimit() {
        return limit;
    }

    /**
     * Converts this XTrimParams to a GLIDE StreamTrimOptions.
     *
     * @return StreamTrimOptions instance configured with this params' settings
     * @throws IllegalArgumentException if neither maxLen nor minId is specified
     */
    public StreamTrimOptions toStreamTrimOptions() {
        boolean exact = exactTrimming != null && exactTrimming;

        if (maxLen != null) {
            if (limit != null) {
                return new StreamTrimOptions.MaxLen(maxLen, limit);
            } else {
                return new StreamTrimOptions.MaxLen(exact, maxLen);
            }
        } else if (minId != null) {
            if (limit != null) {
                return new StreamTrimOptions.MinId(minId, limit);
            } else {
                return new StreamTrimOptions.MinId(exact, minId);
            }
        } else {
            throw new IllegalArgumentException("XTrimParams must specify either maxLen or minId");
        }
    }
}
