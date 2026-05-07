/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/** Thin BitPosParams for this compatibility module; see AbstractBitPosParams for implementation. */
public final class BitPosParams extends AbstractBitPosParams<BitPosParams> {

    public BitPosParams() {
        super();
    }

    public BitPosParams(long start) {
        super(start);
    }

    public BitPosParams(long start, long end) {
        super(start, end);
    }

    public static BitPosParams bitPosParams() {
        return new BitPosParams();
    }
}
