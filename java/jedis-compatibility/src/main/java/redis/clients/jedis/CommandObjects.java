/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * Factory class for creating CommandObject instances used in transactions and pipelines.
 *
 * <p>This class provides methods to create command objects with appropriate builders for converting
 * raw Redis responses to typed Java objects.
 */
public class CommandObjects {
    private RedisProtocol protocol = RedisProtocol.RESP3;

    /** Creates a new CommandObjects instance. */
    public CommandObjects() {}

    /**
     * Sets the Redis protocol version.
     *
     * @param protocol the protocol version
     */
    public void setProtocol(RedisProtocol protocol) {
        this.protocol = protocol;
    }

    /**
     * Gets the current Redis protocol version.
     *
     * @return the protocol version
     */
    public RedisProtocol getProtocol() {
        return protocol;
    }

    // Watch command objects
    public CommandObject<String> watch(String... keys) {
        return new CommandObject<>(BuilderFactory.STRING, (Object[]) keys);
    }

    public CommandObject<String> watch(byte[]... keys) {
        return new CommandObject<>(BuilderFactory.STRING, (Object[]) keys);
    }

    // Command objects for common operations - these will be used by Transaction
    // For now, we'll add them as needed. The Transaction class will primarily
    // delegate to the Jedis instance for actual command execution.
}
