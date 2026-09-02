/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * Factory class for creating CommandObject instances used in transactions and pipelines.
 *
 * <p>This class provides methods to create command objects with appropriate builders for converting
 * raw Redis responses to typed Java objects.
 *
 * <p>This version is compatible with Jedis 4.x (without RedisProtocol).
 */
public class CommandObjects {

    /** Creates a new CommandObjects instance. */
    public CommandObjects() {}

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
