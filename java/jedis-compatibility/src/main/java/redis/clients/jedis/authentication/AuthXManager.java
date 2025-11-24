/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.authentication;

import java.util.function.Supplier;
import redis.clients.jedis.RedisCredentials;

/** AuthXManager compatibility stub for Valkey GLIDE wrapper. */
public final class AuthXManager implements Supplier<RedisCredentials> {

    @Override
    public RedisCredentials get() {
        return null;
    }
}
