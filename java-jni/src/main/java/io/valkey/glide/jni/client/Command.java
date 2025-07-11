package io.valkey.glide.jni.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A generic command builder that can construct any server command with arguments.
 * This provides a unified interface for building commands that will be executed through JNI.
 */
public class Command {
    private final String command;
    private final List<byte[]> arguments;

    private Command(String command, List<byte[]> arguments) {
        this.command = command;
        this.arguments = new ArrayList<>(arguments);
    }

    /**
     * Create a new command builder for the specified command.
     *
     * @param command The command name (e.g., "GET", "SET", "HGET")
     * @return A new command builder
     */
    public static CommandBuilder builder(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }
        return new CommandBuilder(command.trim().toUpperCase());
    }

    /**
     * Get the command name
     * @return The command name
     */
    public String getCommand() {
        return command;
    }

    /**
     * Get the command arguments as byte arrays
     * @return List of arguments as byte arrays
     */
    public List<byte[]> getArguments() {
        return new ArrayList<>(arguments);
    }

    /**
     * Get the command arguments as an array of byte arrays (for JNI)
     * @return Array of byte arrays containing all arguments
     */
    public byte[][] getArgumentsArray() {
        return arguments.toArray(new byte[0][]);
    }

    /**
     * Get the total number of arguments
     * @return Number of arguments
     */
    public int getArgumentCount() {
        return arguments.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(command);
        for (byte[] arg : arguments) {
            sb.append(" ");
            // For display purposes, try to show as string if it's valid UTF-8
            try {
                sb.append(new String(arg, "UTF-8"));
            } catch (Exception e) {
                sb.append("[binary data: ").append(arg.length).append(" bytes]");
            }
        }
        return sb.toString();
    }

    /**
     * Builder class for constructing commands with a fluent interface.
     */
    public static class CommandBuilder {
        private final String command;
        private final List<byte[]> arguments;

        private CommandBuilder(String command) {
            this.command = command;
            this.arguments = new ArrayList<>();
        }

        /**
         * Add a string argument to the command.
         *
         * @param arg The string argument
         * @return This builder for method chaining
         */
        public CommandBuilder arg(String arg) {
            if (arg != null) {
                arguments.add(arg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return this;
        }

        /**
         * Add a byte array argument to the command.
         *
         * @param arg The byte array argument
         * @return This builder for method chaining
         */
        public CommandBuilder arg(byte[] arg) {
            if (arg != null) {
                arguments.add(Arrays.copyOf(arg, arg.length));
            }
            return this;
        }

        /**
         * Add an integer argument to the command.
         *
         * @param arg The integer argument
         * @return This builder for method chaining
         */
        public CommandBuilder arg(int arg) {
            return arg(String.valueOf(arg));
        }

        /**
         * Add a long argument to the command.
         *
         * @param arg The long argument
         * @return This builder for method chaining
         */
        public CommandBuilder arg(long arg) {
            return arg(String.valueOf(arg));
        }

        /**
         * Add a double argument to the command.
         *
         * @param arg The double argument
         * @return This builder for method chaining
         */
        public CommandBuilder arg(double arg) {
            return arg(String.valueOf(arg));
        }

        /**
         * Add multiple string arguments to the command.
         *
         * @param args The string arguments
         * @return This builder for method chaining
         */
        public CommandBuilder args(String... args) {
            if (args != null) {
                for (String arg : args) {
                    arg(arg);
                }
            }
            return this;
        }

        /**
         * Add multiple byte array arguments to the command.
         *
         * @param args The byte array arguments
         * @return This builder for method chaining
         */
        public CommandBuilder args(byte[]... args) {
            if (args != null) {
                for (byte[] arg : args) {
                    arg(arg);
                }
            }
            return this;
        }

        /**
         * Add arguments from a list of strings.
         *
         * @param args The list of string arguments
         * @return This builder for method chaining
         */
        public CommandBuilder args(List<String> args) {
            if (args != null) {
                for (String arg : args) {
                    arg(arg);
                }
            }
            return this;
        }

        /**
         * Build the final Command object.
         *
         * @return The constructed Command
         */
        public Command build() {
            return new Command(command, arguments);
        }
    }
}