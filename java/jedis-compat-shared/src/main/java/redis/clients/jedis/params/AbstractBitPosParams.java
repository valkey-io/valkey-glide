/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import redis.clients.jedis.args.BitCountOption;

/** Parameters for BITPOS command in Jedis compatibility layer. */
public abstract class AbstractBitPosParams<T extends AbstractBitPosParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    private Long start;
    private Long end;
    private BitCountOption modifier;

    protected AbstractBitPosParams() {}

    protected AbstractBitPosParams(long start) {
        this.start = start;
    }

    protected AbstractBitPosParams(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public T start(long start) {
        this.start = start;
        return self();
    }

    public T end(long end) {
        this.end = end;
        return self();
    }

    public T modifier(BitCountOption modifier) {
        this.modifier = modifier;
        return self();
    }

    public Long getStart() {
        return start;
    }

    public Long getEnd() {
        return end;
    }

    public BitCountOption getModifier() {
        return modifier;
    }
}
