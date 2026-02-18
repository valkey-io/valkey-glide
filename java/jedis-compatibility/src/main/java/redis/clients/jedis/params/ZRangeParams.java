/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/**
 * Parameters for ZRANGE command. Provides options for controlling ZRANGE behavior such as BYSCORE,
 * BYLEX, REV (reverse order), and LIMIT (pagination).
 *
 */
public class ZRangeParams {

    public enum ZRangeBy {
        INDEX,
        SCORE,
        LEX
    }

    private final ZRangeBy by;
    private final Object min;
    private final Object max;
    private boolean rev = false;
    private boolean limit = false;
    private int offset;
    private int count;

    private ZRangeParams() {
        throw new InstantiationError("Empty constructor must not be called.");
    }

    public ZRangeParams(int min, int max) {
        this.by = ZRangeBy.INDEX;
        this.min = min;
        this.max = max;
    }

    public static ZRangeParams zrangeParams(int min, int max) {
        return new ZRangeParams(min, max);
    }

    public ZRangeParams(double min, double max) {
        this.by = ZRangeBy.SCORE;
        this.min = min;
        this.max = max;
    }

    public static ZRangeParams zrangeByScoreParams(double min, double max) {
        return new ZRangeParams(min, max);
    }

    private ZRangeParams(ZRangeBy by, Object min, Object max) {
        if (by == null || by == ZRangeBy.SCORE || by == ZRangeBy.LEX) {
            // ok
        } else {
            throw new IllegalArgumentException(by.name() + " is not a valid ZRANGE type argument.");
        }
        this.by = by;
        this.min = min;
        this.max = max;
    }

    public ZRangeParams(ZRangeBy by, String min, String max) {
        this(by, (Object) min, (Object) max);
    }

    public ZRangeParams(ZRangeBy by, byte[] min, byte[] max) {
        this(by, (Object) min, (Object) max);
    }

    public static ZRangeParams zrangeByLexParams(String min, String max) {
        return new ZRangeParams(ZRangeBy.LEX, min, max);
    }

    public static ZRangeParams zrangeByLexParams(byte[] min, byte[] max) {
        return new ZRangeParams(ZRangeBy.LEX, min, max);
    }

    public ZRangeParams rev() {
        this.rev = true;
        return this;
    }

    public ZRangeParams limit(int offset, int count) {
        this.limit = true;
        this.offset = offset;
        this.count = count;
        return this;
    }

    public ZRangeBy getBy() {
        return by;
    }

    public Object getMin() {
        return min;
    }

    public Object getMax() {
        return max;
    }

    public boolean isRev() {
        return rev;
    }

    public boolean hasLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZRangeParams that = (ZRangeParams) o;
        return rev == that.rev
                && limit == that.limit
                && offset == that.offset
                && count == that.count
                && by == that.by
                && java.util.Objects.equals(min, that.min)
                && java.util.Objects.equals(max, that.max);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(by, min, max, rev, limit, offset, count);
    }
}
