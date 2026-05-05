/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/** Thin HSetExParams for this compatibility module; see AbstractHSetExParams for implementation. */
public final class HSetExParams extends AbstractHSetExParams<HSetExParams> {

    public HSetExParams() {
        super();
    }

    public static HSetExParams hSetExParams() {
        return new HSetExParams();
    }
}
