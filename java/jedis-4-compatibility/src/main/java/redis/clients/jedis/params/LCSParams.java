/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/** Thin LCSParams for this compatibility module; see AbstractLCSParams for implementation. */
public final class LCSParams extends AbstractLCSParams<LCSParams> {

    public LCSParams() {
        super();
    }

    public static LCSParams LCSParams() {
        return new LCSParams();
    }
}
