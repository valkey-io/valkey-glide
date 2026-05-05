/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.commands.stream.StreamTrimOptions;
import redis.clients.jedis.StreamEntryID;

/**
 * Parameters for XADD command in Jedis compatibility layer. Provides a fluent API for setting
 * stream entry ID, stream creation behavior, and trimming options.
 */
public class XAddParams {

    private String id;
    private Boolean makeStream;
    private Long maxLen;
    private String minId;
    private Boolean exactTrimming;
    private Long limit;

    public static XAddParams xAddParams() {
        return new XAddParams();
    }

    /**
     * Set the entry ID explicitly. Use "*" for auto-generation.
     *
     * @param id the entry ID
     * @return this
     */
    public XAddParams id(String id) {
        this.id = id;
        return this;
    }

    /**
     * Set the entry ID explicitly using StreamEntryID. Use "*" for auto-generation.
     *
     * @param id the entry ID
     * @return this
     */
    public XAddParams id(StreamEntryID id) {
        this.id = id != null ? id.toString() : "*";
        return this;
    }

    /**
     * If set to false, the stream won't be created if it doesn't exist. Equivalent to NOMKSTREAM.
     *
     * @return this
     */
    public XAddParams noMkStream() {
        this.makeStream = false;
        return this;
    }

    /**
     * Trim the stream to approximately the specified maximum length using MAXLEN ~ threshold.
     *
     * @param maxLen maximum length
     * @return this
     */
    public XAddParams maxLen(long maxLen) {
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
    public XAddParams maxLenExact(long maxLen) {
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
    public XAddParams minId(String minId) {
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
    public XAddParams minId(StreamEntryID minId) {
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
    public XAddParams minIdExact(String minId) {
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
    public XAddParams minIdExact(StreamEntryID minId) {
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
    public XAddParams limit(long limit) {
        this.limit = limit;
        return this;
    }

    // Getters for internal use
    public String getId() {
        return id;
    }

    public Boolean getMakeStream() {
        return makeStream;
    }

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
     * Converts this XAddParams to a GLIDE StreamAddOptions.
     *
     * @return StreamAddOptions instance configured with this params' settings
     */
    public StreamAddOptions toStreamAddOptions() {
        StreamAddOptions.StreamAddOptionsBuilder builder = StreamAddOptions.builder();

        if (id != null) {
            builder.id(id);
        }
        if (makeStream != null) {
            builder.makeStream(makeStream);
        }

        // Handle trim options
        if (maxLen != null) {
            boolean exact = exactTrimming != null && exactTrimming;
            StreamTrimOptions trimOpts;
            if (limit != null) {
                trimOpts = new StreamTrimOptions.MaxLen(maxLen, limit);
            } else {
                trimOpts = new StreamTrimOptions.MaxLen(exact, maxLen);
            }
            builder.trim(trimOpts);
        } else if (minId != null) {
            boolean exact = exactTrimming != null && exactTrimming;
            StreamTrimOptions trimOpts;
            if (limit != null) {
                trimOpts = new StreamTrimOptions.MinId(minId, limit);
            } else {
                trimOpts = new StreamTrimOptions.MinId(exact, minId);
            }
            builder.trim(trimOpts);
        }

        return builder.build();
    }
}
