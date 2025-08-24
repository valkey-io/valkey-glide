/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.args;

/**
 * Byte array representation of arguments to write in socket input stream.
 *
 * <p>This interface is provided for compatibility with original Jedis code. It represents objects
 * that can be converted to their byte array representation for Redis protocol communication.
 */
public interface Rawable {

    /**
     * Get byte array representation.
     *
     * @return binary representation of the object
     */
    byte[] getRaw();

    @Override
    int hashCode();

    @Override
    boolean equals(Object o);
}
