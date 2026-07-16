/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

/** Thin SetParams for this compatibility module; see AbstractSetParams for implementation. */
public final class SetParams extends AbstractSetParams<SetParams> {

    public SetParams() {
        super();
    }

    public static SetParams setParams() {
        return new SetParams();
    }
}
