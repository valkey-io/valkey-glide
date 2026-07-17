/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/** Thin XAddParams for this compatibility module; see AbstractXAddParams for implementation. */
public final class XAddParams extends AbstractXAddParams<XAddParams> {

    public XAddParams() {
        super();
    }

    public static XAddParams xAddParams() {
        return new XAddParams();
    }
}
