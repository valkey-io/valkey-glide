/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/** Thin ZAddParams for this compatibility module; see AbstractZAddParams for implementation. */
public final class ZAddParams extends AbstractZAddParams<ZAddParams> {

    public ZAddParams() {
        super();
    }

    public static ZAddParams zAddParams() {
        return new ZAddParams();
    }
}
