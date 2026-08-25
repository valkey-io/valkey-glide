/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.util.function.Supplier;

public interface RedisCredentialsProvider extends Supplier<RedisCredentials> {

    /**
     * Prepare {@link RedisCredentials} before {@link RedisCredentialsProvider#get()} is called.
     *
     * <p>An application may:
     *
     * <ul>
     *   <li>Load credentials from the credentials management system
     *   <li>Reload credentials when credentials are rotated.
     *   <li>Reload credentials after an authentication error (e.g. NOAUTH, WRONGPASS, etc).
     *   <li>Minimize the time that the password lives in the memory (in combination with {@link
     *       RedisCredentialsProvider#cleanUp()}).
     * </ul>
     */
    default void prepare() {}

    /**
     * Clean up credentials (e.g. from memory).
     *
     * @see RedisCredentialsProvider#prepare()
     */
    default void cleanUp() {}
}
