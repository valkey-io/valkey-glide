/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import glide.api.models.commands.stream.StreamTrimOptions;
import redis.clients.jedis.StreamEntryID;

/**
 * Parameters for XTRIM command in Jedis compatibility layer. Provides a fluent API for trimming
 * streams by maximum length or minimum ID.
 *
 * <p>Trim field semantics and GLIDE conversion are shared with {@link AbstractXAddParams} via
 * {@link StreamTrimParamState}. See {@link AbstractXAddParams} class Javadoc for maxLen vs limit
 * and maxLen-over-minId priority.
 */
public abstract class AbstractXTrimParams<T extends AbstractXTrimParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    private final StreamTrimParamState trim = new StreamTrimParamState();

    /**
     * Trim the stream to approximately the specified maximum length using MAXLEN ~ threshold.
     *
     * @param maxLen maximum length
     * @return this
     */
    public T maxLen(long maxLen) {
        trim.maxLen = maxLen;
        trim.exactTrimming = false;
        return self();
    }

    /**
     * Trim the stream to exactly the specified maximum length using MAXLEN = threshold.
     *
     * @param maxLen maximum length
     * @return this
     */
    public T maxLenExact(long maxLen) {
        trim.maxLen = maxLen;
        trim.exactTrimming = true;
        return self();
    }

    /**
     * Trim entries with IDs lower than minId using MINID ~ threshold.
     *
     * @param minId minimum ID threshold
     * @return this
     */
    public T minId(String minId) {
        trim.minId = minId;
        trim.exactTrimming = false;
        return self();
    }

    /**
     * Trim entries with IDs lower than minId using MINID ~ threshold.
     *
     * @param minId minimum ID threshold
     * @return this
     */
    public T minId(StreamEntryID minId) {
        trim.minId = minId != null ? minId.toString() : null;
        trim.exactTrimming = false;
        return self();
    }

    /**
     * Trim entries with IDs lower than minId using MINID = threshold (exact).
     *
     * @param minId minimum ID threshold
     * @return this
     */
    public T minIdExact(String minId) {
        trim.minId = minId;
        trim.exactTrimming = true;
        return self();
    }

    /**
     * Trim entries with IDs lower than minId using MINID = threshold (exact).
     *
     * @param minId minimum ID threshold
     * @return this
     */
    public T minIdExact(StreamEntryID minId) {
        trim.minId = minId != null ? minId.toString() : null;
        trim.exactTrimming = true;
        return self();
    }

    /**
     * Set the LIMIT count for trimming — maximum entries to evict in this trim pass. See {@link
     * AbstractXAddParams#limit(long)} for how limit relates to maxLen / minId.
     *
     * @param limit maximum number of entries to trim in one pass
     * @return this
     */
    public T limit(long limit) {
        trim.limit = limit;
        return self();
    }

    // Getters for internal use
    public Long getMaxLen() {
        return trim.maxLen;
    }

    public String getMinId() {
        return trim.minId;
    }

    public Boolean getExactTrimming() {
        return trim.exactTrimming;
    }

    public Long getLimit() {
        return trim.limit;
    }

    /**
     * Converts this XTrimParams to a GLIDE StreamTrimOptions.
     *
     * @return StreamTrimOptions instance configured with this params' settings
     * @throws IllegalArgumentException if neither maxLen nor minId is specified
     */
    public StreamTrimOptions toStreamTrimOptions() {
        return trim.toStreamTrimOptions(true);
    }
}
