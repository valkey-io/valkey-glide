/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/**
 * Thin ZIncrByParams for this compatibility module; see AbstractZIncrByParams for implementation.
 */
public final class ZIncrByParams extends AbstractZIncrByParams<ZIncrByParams> {

    public ZIncrByParams() {
        super();
    }

    public static ZIncrByParams zIncrByParams() {
        return new ZIncrByParams();
    }
}
