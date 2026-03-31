/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * Represents a Redis command with its builder for converting raw responses to typed results.
 *
 * <p>This is used internally by the Transaction system to queue commands and build their responses
 * after execution.
 *
 * @param <T> the type of the command result
 */
public class CommandObject<T> {
    private final Builder<T> builder;
    private final Object[] arguments;

    /**
     * Creates a new command object with a builder and arguments.
     *
     * @param builder the builder to convert raw response data
     * @param arguments the command arguments (optional, for debugging)
     */
    public CommandObject(Builder<T> builder, Object... arguments) {
        this.builder = builder;
        this.arguments = arguments;
    }

    /**
     * Gets the builder for this command.
     *
     * @return the builder
     */
    public Builder<T> getBuilder() {
        return builder;
    }

    /**
     * Gets the command arguments.
     *
     * @return the arguments array
     */
    public Object[] getArguments() {
        return arguments;
    }
}
