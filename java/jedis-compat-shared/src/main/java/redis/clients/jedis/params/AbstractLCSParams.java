/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/**
 * LCSParams compatibility class for Valkey GLIDE wrapper. Represents parameters for the LCS
 * (Longest Common Subsequence) command.
 */
public abstract class AbstractLCSParams<T extends AbstractLCSParams<T>> implements IParams {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    private boolean len = false;
    private boolean idx = false;
    private Long minMatchLen;
    private boolean withMatchLen = false;

    /**
     * When LEN is given the command returns the length of the longest common substring.
     *
     * @return LCSParams
     */
    public T len() {
        this.len = true;
        return self();
    }

    /**
     * When IDX is given the command returns an array with the LCS length and all the ranges in both
     * the strings, start and end offset for each string, where there are matches.
     *
     * @return LCSParams
     */
    public T idx() {
        this.idx = true;
        return self();
    }

    /**
     * Specify the minimum match length.
     *
     * @param minMatchLen minimum match length
     * @return LCSParams
     */
    public T minMatchLen(long minMatchLen) {
        this.minMatchLen = minMatchLen;
        return self();
    }

    /**
     * When WITHMATCHLEN is given each array representing a match will also have the length of the
     * match.
     *
     * @return LCSParams
     */
    public T withMatchLen() {
        this.withMatchLen = true;
        return self();
    }

    public boolean isLen() {
        return len;
    }

    public boolean isIdx() {
        return idx;
    }

    public Long getMinMatchLen() {
        return minMatchLen;
    }

    public boolean isWithMatchLen() {
        return withMatchLen;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractLCSParams<?> that = (AbstractLCSParams<?>) o;
        return len == that.len
                && idx == that.idx
                && withMatchLen == that.withMatchLen
                && java.util.Objects.equals(minMatchLen, that.minMatchLen);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(len, idx, minMatchLen, withMatchLen);
    }
}
