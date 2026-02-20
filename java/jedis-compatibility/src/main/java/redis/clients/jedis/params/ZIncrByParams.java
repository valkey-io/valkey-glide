/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/**
 * Parameters for ZINCRBY command. In fact, Redis doesn't have parameters for ZINCRBY. Instead Redis
 * has INCR parameter for ZADD.
 *
 * <p>When users call ZADD with INCR option, its restriction (only one member) and return type is
 * same to ZINCRBY. Document page for ZADD also describes INCR option to act like ZINCRBY. So we
 * decided to wrap "ZADD with INCR option" to ZINCRBY.
 *
 * <p>Works with Redis 3.0.2 and onwards.
 *
 * <p>This class is compatible with Jedis ZIncrByParams and provides the same builder-style API.
 */
public class ZIncrByParams {

    private Boolean nx;
    private Boolean xx;

    public ZIncrByParams() {}

    public static ZIncrByParams zIncrByParams() {
        return new ZIncrByParams();
    }

    /**
     * Only set the key if it does not already exist.
     *
     * @return ZIncrByParams
     */
    public ZIncrByParams nx() {
        this.nx = true;
        return this;
    }

    /**
     * Only set the key if it already exist.
     *
     * @return ZIncrByParams
     */
    public ZIncrByParams xx() {
        this.xx = true;
        return this;
    }

    public Boolean getNx() {
        return nx;
    }

    public Boolean getXx() {
        return xx;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZIncrByParams that = (ZIncrByParams) o;
        return java.util.Objects.equals(nx, that.nx) && java.util.Objects.equals(xx, that.xx);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(nx, xx);
    }
}
