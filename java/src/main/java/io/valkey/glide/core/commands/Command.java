package io.valkey.glide.core.commands;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a Valkey/Redis command with its arguments.
 * This class provides a clean interface for building and executing commands
 * without protobuf dependencies.
 */
public class Command {
    private final String command;
    private final String[] arguments;

    /**
     * Create a command with the specified name and arguments.
     *
     * @param command The command name (e.g., "GET", "SET", "HGET")
     * @param arguments The command arguments
     */
    public Command(String command, String... arguments) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }
        this.command = command;
        this.arguments = arguments != null ? arguments : new String[0];
    }

    /**
     * Create a command from a CommandType enum and arguments.
     *
     * @param commandType The command type enum
     * @param arguments The command arguments
     */
    public Command(CommandType commandType, String... arguments) {
        this(commandType.toString(), arguments);
    }

    /**
     * Create a command with arguments from a list.
     *
     * @param command The command name
     * @param arguments The command arguments as a list
     */
    public Command(String command, List<String> arguments) {
        this(command, arguments != null ? arguments.toArray(new String[0]) : new String[0]);
    }

    /**
     * Get the command name.
     *
     * @return The command name
     */
    public String getCommand() {
        return command;
    }

    /**
     * Get the command arguments.
     *
     * @return Array of command arguments
     */
    public String[] getArguments() {
        return Arrays.copyOf(arguments, arguments.length);
    }

    /**
     * Get the command arguments as an array (internal use).
     * This method avoids copying for performance when the array won't be modified.
     *
     * @return Array of command arguments (direct reference)
     */
    public String[] getArgumentsArray() {
        return arguments;
    }

    /**
     * Get the total number of arguments.
     *
     * @return Number of arguments
     */
    public int getArgumentCount() {
        return arguments.length;
    }

    /**
     * Create a new command by appending additional arguments.
     *
     * @param additionalArgs Additional arguments to append
     * @return New Command instance with appended arguments
     */
    public Command withAdditionalArgs(String... additionalArgs) {
        if (additionalArgs == null || additionalArgs.length == 0) {
            return this;
        }

        String[] newArgs = new String[arguments.length + additionalArgs.length];
        System.arraycopy(arguments, 0, newArgs, 0, arguments.length);
        System.arraycopy(additionalArgs, 0, newArgs, arguments.length, additionalArgs.length);

        return new Command(command, newArgs);
    }

    /**
     * Check if this command has any arguments.
     *
     * @return true if the command has arguments, false otherwise
     */
    public boolean hasArguments() {
        return arguments.length > 0;
    }

    @Override
    public String toString() {
        if (arguments.length == 0) {
            return command;
        }
        return command + " " + String.join(" ", arguments);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Command other = (Command) obj;
        return command.equals(other.command) && Arrays.equals(arguments, other.arguments);
    }

    @Override
    public int hashCode() {
        return command.hashCode() * 31 + Arrays.hashCode(arguments);
    }

    // ==================== FACTORY METHODS ====================

    /**
     * Create a GET command.
     *
     * @param key The key to get
     * @return GET command
     */
    public static Command get(String key) {
        return new Command(CommandType.GET, key);
    }

    /**
     * Create a SET command.
     *
     * @param key The key to set
     * @param value The value to set
     * @return SET command
     */
    public static Command set(String key, String value) {
        return new Command(CommandType.SET, key, value);
    }

    /**
     * Create a MGET command.
     *
     * @param keys The keys to get
     * @return MGET command
     */
    public static Command mget(String... keys) {
        return new Command(CommandType.MGET, keys);
    }

    /**
     * Create a HGET command.
     *
     * @param key The hash key
     * @param field The field to get
     * @return HGET command
     */
    public static Command hget(String key, String field) {
        return new Command(CommandType.HGET, key, field);
    }

    /**
     * Create a HSET command.
     *
     * @param key The hash key
     * @param field The field to set
     * @param value The value to set
     * @return HSET command
     */
    public static Command hset(String key, String field, String value) {
        return new Command(CommandType.HSET, key, field, value);
    }

    /**
     * Create a SADD command.
     *
     * @param key The set key
     * @param members The members to add
     * @return SADD command
     */
    public static Command sadd(String key, String... members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return new Command(CommandType.SADD, args);
    }

    /**
     * Create a ZADD command.
     *
     * @param key The sorted set key
     * @param score The score
     * @param member The member to add
     * @return ZADD command
     */
    public static Command zadd(String key, double score, String member) {
        return new Command(CommandType.ZADD, key, String.valueOf(score), member);
    }

    /**
     * Create a LPUSH command.
     *
     * @param key The list key
     * @param elements The elements to push
     * @return LPUSH command
     */
    public static Command lpush(String key, String... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return new Command(CommandType.LPUSH, args);
    }

    /**
     * Create a PING command.
     *
     * @return PING command
     */
    public static Command ping() {
        return new Command(CommandType.PING);
    }

    /**
     * Create an INFO command.
     *
     * @param section Optional section to get info for
     * @return INFO command
     */
    public static Command info(String... section) {
        return new Command(CommandType.INFO, section);
    }
}
