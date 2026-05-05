/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/** Thin GetExParams for this compatibility module; see AbstractGetExParams for implementation. */
public final class GetExParams extends AbstractGetExParams<GetExParams> {

    public GetExParams() {
        super();
    }

    public static GetExParams getExParams() {
        return new GetExParams();
    }
}
