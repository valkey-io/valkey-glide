/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.valkey.glide.api.commands;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents a Valkey/Redis command with its arguments.
 * This is a simple data structure that holds the command type and arguments.
 * Command implementations are located in the client classes (BaseClient, GlideClient, etc.).
 */
public class Command {
    private final CommandType type;
    private final String[] arguments;

    /**
     * Creates a command with String arguments.
     *
     * @param type The command type
     * @param args The command arguments as strings
     */
    public Command(CommandType type, String... args) {
        this.type = Objects.requireNonNull(type, "Command type cannot be null");
        this.arguments = args != null ? Arrays.copyOf(args, args.length) : new String[0];
    }

    /**
     * Gets the command type.
     *
     * @return The command type
     */
    public CommandType getType() {
        return type;
    }

    /**
     * Gets the command arguments.
     *
     * @return The command arguments
     */
    public String[] getArguments() {
        return Arrays.copyOf(arguments, arguments.length);
    }

    /**
     * Gets the command arguments as array (for compatibility).
     *
     * @return The command arguments as array
     */
    public String[] getArgumentsArray() {
        return Arrays.copyOf(arguments, arguments.length);
    }

    /**
     * Gets the command arguments as a list.
     *
     * @return The command arguments as a list
     */
    public List<String> getArgumentsList() {
        return Arrays.asList(arguments);
    }

    /**
     * Gets the command name for the native client.
     *
     * @return The command name
     */
    public String getCommand() {
        return type.name();
    }

    /**
     * Converts the command to a string representation.
     *
     * @return String representation of the command
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.name());
        if (arguments.length > 0) {
            sb.append(" [");
            for (int i = 0; i < arguments.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(arguments[i]);
            }
            sb.append("]");
        }
        return sb.toString();
    }

    /**
     * Checks equality with another object.
     *
     * @param obj The object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Command command = (Command) obj;
        return type == command.type && Arrays.equals(arguments, command.arguments);
    }

    /**
     * Gets the hash code.
     *
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, Arrays.hashCode(arguments));
    }
}
