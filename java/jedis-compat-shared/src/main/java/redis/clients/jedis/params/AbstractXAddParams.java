/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.commands.stream.StreamTrimOptions;
import redis.clients.jedis.StreamEntryID;

/**
 * Parameters for XADD command in Jedis compatibility layer. Provides a fluent API for setting
 * stream entry ID, stream creation behavior, and trimming options.
 *
 * <p><b>Trim semantics (shared with {@link AbstractXTrimParams}):</b>
 *
 * <ul>
 *   <li>{@code maxLen} sets the stream length threshold (MAXLEN); {@code minId} sets a minimum
 *       entry ID threshold (MINID). If both are configured, <b>maxLen takes priority</b> and minId
 *       is ignored when building GLIDE options — matching Redis XADD trim modifier behavior where
 *       only one strategy is applied per command.
 *   <li>{@code limit} caps how many entries may be evicted in a single trim pass (the LIMIT
 *       modifier). It does not replace maxLen/minId; it qualifies whichever trim strategy is
 *       active. For example, {@code maxLen(1000).limit(10)} means "trim toward ~1000 entries,
 *       evicting at most 10 entries this call."
 * </ul>
 */
public abstract class AbstractXAddParams<T extends AbstractXAddParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    private String id;
    private Boolean makeStream;
    private final StreamTrimParamState trim = new StreamTrimParamState();

    /**
     * Set the entry ID explicitly. Use "*" for auto-generation.
     *
     * @param id the entry ID
     * @return this
     */
    public T id(String id) {
        this.id = id;
        return self();
    }

    /**
     * Set the entry ID explicitly using StreamEntryID. Use "*" for auto-generation.
     *
     * @param id the entry ID
     * @return this
     */
    public T id(StreamEntryID id) {
        this.id = id != null ? id.toString() : "*";
        return self();
    }

    /**
     * If set to false, the stream won't be created if it doesn't exist. Equivalent to NOMKSTREAM.
     *
     * @return this
     */
    public T noMkStream() {
        this.makeStream = false;
        return self();
    }

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
     * Trim entries with IDs lower than minId using MINID ~ threshold. Ignored at conversion time if
     * {@link #maxLen(long)} or {@link #maxLenExact(long)} was also set.
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
     * Trim entries with IDs lower than minId using MINID ~ threshold. Ignored at conversion time if
     * maxLen was also set.
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
     * Trim entries with IDs lower than minId using MINID = threshold (exact). Ignored at conversion
     * time if maxLen was also set.
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
     * Trim entries with IDs lower than minId using MINID = threshold (exact). Ignored at conversion
     * time if maxLen was also set.
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
     * Set the LIMIT count for trimming — the maximum number of entries to evict in this trim pass.
     * Applies together with whichever of {@link #maxLen(long)} or {@link #minId(String)} is active
     * (maxLen wins when both trim strategies are configured).
     *
     * @param limit maximum number of entries to trim in one pass
     * @return this
     */
    public T limit(long limit) {
        trim.limit = limit;
        return self();
    }

    // Getters for internal use
    public String getId() {
        return id;
    }

    public Boolean getMakeStream() {
        return makeStream;
    }

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

        StreamTrimOptions trimOpts = trim.toStreamTrimOptions(false);
        if (trimOpts != null) {
            builder.trim(trimOpts);
        }

        return builder.build();
    }
}
