/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

public interface RedisCredentials {

    /**
     * @return Redis ACL user
     */
    default String getUser() {
        return null;
    }

    default char[] getPassword() {
        return null;
    }
}
