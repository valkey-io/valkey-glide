/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/** Thin XTrimParams for this compatibility module; see AbstractXTrimParams for implementation. */
public final class XTrimParams extends AbstractXTrimParams<XTrimParams> {

    public XTrimParams() {
        super();
    }

    public static XTrimParams xTrimParams() {
        return new XTrimParams();
    }
}
