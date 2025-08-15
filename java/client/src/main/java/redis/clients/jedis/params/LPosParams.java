/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.Objects;

public class LPosParams {

    private Integer rank;
    private Integer maxlen;

    public static LPosParams lPosParams() {
        return new LPosParams();
    }

    public LPosParams rank(int rank) {
        this.rank = rank;
        return this;
    }

    public LPosParams maxlen(int maxLen) {
        this.maxlen = maxLen;
        return this;
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
        LPosParams that = (LPosParams) o;
        return Objects.equals(rank, that.rank) && Objects.equals(maxlen, that.maxlen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rank, maxlen);
    }
}
