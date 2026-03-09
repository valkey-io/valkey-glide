/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import redis.clients.jedis.exceptions.JedisDataException;

/**
 * A response wrapper for transaction commands that provides deferred access to command results.
 *
 * <p>In a Redis transaction, commands are queued and executed atomically when EXEC is called.
 * Response objects act as futures/promises that will be populated with actual values after the
 * transaction executes.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Transaction t = jedis.multi();
 * Response<String> r1 = t.set("key", "value");
 * Response<String> r2 = t.get("key");
 * t.exec();
 * String value = r2.get(); // Retrieve the actual value after exec()
 * }</pre>
 *
 * @param <T> the type of the response value
 */
public class Response<T> {
    private T response;
    private boolean built = false;
    private boolean set = false;
    private Builder<T> builder;
    private Object data;

    /**
     * Creates a new Response with a builder that will convert raw data to the expected type.
     *
     * @param builder the builder to convert raw response data
     */
    public Response(Builder<T> builder) {
        this.builder = builder;
    }

    /**
     * Sets the raw response data. This is called internally after transaction execution.
     *
     * @param data the raw response data from Redis
     */
    public void set(Object data) {
        this.data = data;
        this.set = true;
    }

    /**
     * Gets the response value, building it from raw data if necessary.
     *
     * @return the response value
     * @throws JedisDataException if the response data represents an error
     */
    public T get() {
        if (!set) {
            throw new JedisDataException(
                    "Please close pipeline or multi block before calling this method.");
        }

        if (!built) {
            if (data != null) {
                if (data instanceof JedisDataException) {
                    throw (JedisDataException) data;
                }
                if (builder == null) {
                    throw new JedisDataException("No builder set for response");
                }
                response = builder.build(data);
            }
            built = true;
        }

        return response;
    }

    /**
     * Returns a string representation of this response.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "Response [" + (built ? response : data) + "]";
    }
}
