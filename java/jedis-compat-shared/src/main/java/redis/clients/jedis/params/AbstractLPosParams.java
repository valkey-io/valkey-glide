/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.Objects;

public abstract class AbstractLPosParams<T extends AbstractLPosParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    private Integer rank;
    private Integer maxlen;

    public T rank(int rank) {
        this.rank = rank;
        return self();
    }

    public T maxlen(int maxLen) {
        this.maxlen = maxLen;
        return self();
    }

    public Integer getRank() {
        return rank;
    }

    public Integer getMaxlen() {
        return maxlen;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractLPosParams<?> that = (AbstractLPosParams<?>) o;
        return Objects.equals(rank, that.rank) && Objects.equals(maxlen, that.maxlen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rank, maxlen);
    }
}
