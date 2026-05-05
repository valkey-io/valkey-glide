/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/** Thin ZRangeParams for this compatibility module; see AbstractZRangeParams for implementation. */
public final class ZRangeParams extends AbstractZRangeParams<ZRangeParams> {

    public ZRangeParams(int min, int max) {
        super(min, max);
    }

    public ZRangeParams(double min, double max) {
        super(min, max);
    }

    public ZRangeParams(ZRangeBy by, String min, String max) {
        super(by, min, max);
    }

    public ZRangeParams(ZRangeBy by, byte[] min, byte[] max) {
        super(by, min, max);
    }

    public static ZRangeParams zrangeParams(int min, int max) {
        return new ZRangeParams(min, max);
    }

    public static ZRangeParams zrangeByScoreParams(double min, double max) {
        return new ZRangeParams(min, max);
    }

    public static ZRangeParams zrangeByLexParams(String min, String max) {
        return new ZRangeParams(ZRangeBy.LEX, min, max);
    }

    public static ZRangeParams zrangeByLexParams(byte[] min, byte[] max) {
        return new ZRangeParams(ZRangeBy.LEX, min, max);
    }
}
