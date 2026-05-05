/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/** Thin HGetExParams for this compatibility module; see AbstractHGetExParams for implementation. */
public final class HGetExParams extends AbstractHGetExParams<HGetExParams> {

    public HGetExParams() {
        super();
    }

    public static HGetExParams hGetExParams() {
        return new HGetExParams();
    }
}
