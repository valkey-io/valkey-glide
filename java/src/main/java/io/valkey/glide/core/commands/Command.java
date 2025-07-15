/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.valkey.glide.core.commands;

import java.util.Arrays;

/**
 * Represents a Valkey command with its type and arguments.
 * This is a pure data structure without any command execution logic.
 */
public class Command {
    private final CommandType type;
    private final String[] args;

    /**
     * Creates a new Command with the specified type and arguments.
     *
     * @param type The command type
     * @param args The command arguments
     */
    public Command(CommandType type, String... args) {
        this.type = type;
        this.args = args != null ? args.clone() : new String[0];
    }

    /**
     * Get the command type.
     *
     * @return The command type
     */
    public CommandType getType() {
        return type;
    }

    /**
     * Get the command arguments.
     *
     * @return A copy of the command arguments
     */
    public String[] getArgs() {
        return args.clone();
    }

    /**
     * Get the command arguments as an array (for internal use).
     *
     * @return The command arguments array
     */
    public String[] getArgumentsArray() {
        return args.clone();
    }

    /**
     * Get the command as a string array with command name first, then arguments.
     *
     * @return Command as string array
     */
    public String[] getCommand() {
        String[] command = new String[args.length + 1];
        command[0] = type.getCommandName();
        System.arraycopy(args, 0, command, 1, args.length);
        return command;
    }

    @Override
    public String toString() {
        return "Command{" +
                "type=" + type +
                ", args=" + Arrays.toString(args) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Command command = (Command) o;
        return type == command.type && Arrays.equals(args, command.args);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + Arrays.hashCode(args);
        return result;
    }
}
