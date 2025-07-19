package io.valkey.glide.core.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a Redis/Valkey command with its arguments.
 * This class encapsulates a command type and its associated arguments.
 */
public class Command {
    private final String type;
    private final List<String> arguments;

    /**
     * Create a new Command with the specified type and arguments.
     *
     * @param type The command type
     * @param arguments The command arguments
     */
    public Command(String type, String... arguments) {
        this.type = type;
        this.arguments = new ArrayList<>(Arrays.asList(arguments));
    }

    /**
     * Create a new Command with the specified type and arguments list.
     *
     * @param type The command type
     * @param arguments The command arguments as a list
     */
    public Command(String type, List<String> arguments) {
        this.type = type;
        this.arguments = new ArrayList<>(arguments);
    }

    /**
     * GET the command type.
     *
     * @return The command type
     */
    public String getType() {
        return type;
    }

    /**
     * GET the command arguments.
     *
     * @return A list of command arguments
     */
    public List<String> getArguments() {
        return new ArrayList<>(arguments);
    }

    /**
     * GET the command arguments as an array.
     *
     * @return An array of command arguments
     */
    public String[] getArgumentsArray() {
        return arguments.toArray(new String[0]);
    }

    /**
     * Add an argument to the command.
     *
     * @param argument The argument to add
     * @return This Command instance for method chaining
     */
    public Command addArgument(String argument) {
        arguments.add(argument);
        return this;
    }

    /**
     * Add multiple arguments to the command.
     *
     * @param args The arguments to add
     * @return This Command instance for method chaining
     */
    public Command addArguments(String... args) {
        arguments.addAll(Arrays.asList(args));
        return this;
    }

    /**
     * GET the full command as a string array (command name + arguments).
     *
     * @return Array with command name as first element, followed by arguments
     */
    public String[] toArray() {
        String[] result = new String[arguments.size() + 1];
        result[0] = type;
        for (int i = 0; i < arguments.size(); i++) {
            result[i + 1] = arguments.get(i);
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type);
        for (String arg : arguments) {
            sb.append(" ").append(arg);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Command command = (Command) obj;
        return type.equals(command.type) && arguments.equals(command.arguments);
    }

    @Override
    public int hashCode() {
        return type.hashCode() * 31 + arguments.hashCode();
    }
}