/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import redis.clients.jedis.args.BitCountOption;

/** Parameters for BITPOS command in Jedis compatibility layer. */
public class BitPosParams {
    private Long start;
    private Long end;
    private BitCountOption modifier;

    public BitPosParams() {}

    public BitPosParams(long start) {
        this.start = start;
    }

    public BitPosParams(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public static BitPosParams bitPosParams() {
        return new BitPosParams();
    }

    public BitPosParams start(long start) {
        this.start = start;
        return this;
    }

    public BitPosParams end(long end) {
        this.end = end;
        return this;
    }

    public BitPosParams modifier(BitCountOption modifier) {
        this.modifier = modifier;
        return this;
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
