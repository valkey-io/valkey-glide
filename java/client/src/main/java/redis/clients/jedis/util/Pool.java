/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.util;

/** Pool compatibility stub for Valkey GLIDE wrapper. */
public abstract class Pool<T> {

    public abstract T getResource();

    public abstract void returnResource(T resource);

    public abstract void returnBrokenResource(T resource);

    public abstract void destroy();

    public abstract boolean isClosed();
}
