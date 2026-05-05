/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/** Thin LPosParams for this compatibility module; see AbstractLPosParams for implementation. */
public final class LPosParams extends AbstractLPosParams<LPosParams> {

    public LPosParams() {
        super();
    }

    public static LPosParams lPosParams() {
        return new LPosParams();
    }
}
