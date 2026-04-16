/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/** Builder abstract class for compatibility with Valkey GLIDE wrapper. */
public abstract class Builder<T> {
    public abstract T build(Object data);
}
