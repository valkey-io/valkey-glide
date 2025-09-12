/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal.protocol;

/**
 * Represents a Redis/Valkey command with its type and arguments. This class provides compatibility
 * with the existing command execution architecture.
 */
public class Command {
    private final String type;
    private final String[] arguments;

    /**
     * Create a new Command with the specified type and arguments.
     *
     * @param type The command type (e.g., "GET", "SET", "HGET")
     * @param arguments The command arguments
     */
    public Command(String type, String... arguments) {
        this.type = type;
        this.arguments = arguments != null ? arguments : new String[0];
    }

    /**
     * Get the command type.
     *
     * @return The command type
     */
    public String getType() {
        return type;
    }

    /**
     * Get the command arguments as an array.
     *
     * @return Array of command arguments
     */
    public String[] getArgumentsArray() {
        return arguments.clone();
    }

    /**
     * Get the command arguments as a list.
     *
     * @return List of command arguments
     */
    public java.util.List<String> getArguments() {
        return java.util.Arrays.asList(arguments);
    }

    /**
     * Create a Command from a command string and arguments.
     *
     * @param command The command string
     * @param args The command arguments
     * @return A new Command instance
     */
    public static Command of(String command, String... args) {
        return new Command(command, args);
    }

    @Override
    public String toString() {
        if (arguments.length == 0) {
            return type;
        }
        return type + " " + String.join(" ", arguments);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Command command = (Command) obj;
        return java.util.Objects.equals(type, command.type)
                && java.util.Arrays.equals(arguments, command.arguments);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, java.util.Arrays.hashCode(arguments));
    }
}
