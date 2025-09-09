package glide.internal.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a Valkey command with mixed String and binary arguments for JNI operations.
 * This class extends the Command functionality to support binary data (byte[]) alongside
 * string arguments, enabling proper handling of Redis DUMP/RESTORE and function operations.
 * 
 * <p>Binary arguments are preserved as raw bytes without string conversion, preventing
 * data corruption that occurs when binary data is forced through UTF-8 string encoding.
 */
public class BinaryCommand implements CommandInterface {
    private final String type;
    private final List<BinaryValue> arguments;

    /**
     * Create a new BinaryCommand with the specified type and no arguments.
     *
     * @param type The command type
     */
    public BinaryCommand(String type) {
        this.type = type;
        this.arguments = new ArrayList<>();
    }

    /**
     * Create a new BinaryCommand with the specified type and mixed arguments.
     *
     * @param type The command type
     * @param arguments Mixed string and binary arguments
     */
    public BinaryCommand(String type, BinaryValue... arguments) {
        this.type = type;
        this.arguments = new ArrayList<>(Arrays.asList(arguments));
    }

    /**
     * Create a new BinaryCommand with the specified type and arguments list.
     *
     * @param type The command type
     * @param arguments The command arguments as a list
     */
    public BinaryCommand(String type, List<BinaryValue> arguments) {
        this.type = type;
        this.arguments = new ArrayList<>(arguments);
    }

    /**
     * Get the command type.
     *
     * @return The command type string
     */
    public String getType() {
        return type;
    }

    /**
     * Check if this command contains binary data.
     *
     * @return true, as this is a binary command that handles mixed String/byte[] arguments
     */
    @Override
    public boolean isBinaryCommand() {
        return true;
    }

    /**
     * Get the command arguments.
     *
     * @return A list of command arguments
     */
    public List<BinaryValue> getArguments() {
        return new ArrayList<>(arguments);
    }

    /**
     * Add a string argument to the command.
     *
     * @param argument The string argument to add
     * @return This BinaryCommand instance for method chaining
     */
    public BinaryCommand addArgument(String argument) {
        arguments.add(BinaryValue.of(argument));
        return this;
    }

    /**
     * Add a binary argument to the command.
     *
     * @param argument The binary argument to add
     * @return This BinaryCommand instance for method chaining
     */
    public BinaryCommand addArgument(byte[] argument) {
        arguments.add(BinaryValue.of(argument));
        return this;
    }

    /**
     * Add a BinaryValue argument to the command.
     *
     * @param argument The BinaryValue argument to add
     * @return This BinaryCommand instance for method chaining
     */
    public BinaryCommand addArgument(BinaryValue argument) {
        arguments.add(argument);
        return this;
    }

    /**
     * Add multiple string arguments to the command.
     *
     * @param args The string arguments to add
     * @return This BinaryCommand instance for method chaining
     */
    public BinaryCommand addArguments(String... args) {
        for (String arg : args) {
            arguments.add(BinaryValue.of(arg));
        }
        return this;
    }

    /**
     * Add multiple BinaryValue arguments to the command.
     *
     * @param args The BinaryValue arguments to add
     * @return This BinaryCommand instance for method chaining
     */
    public BinaryCommand addArguments(BinaryValue... args) {
        arguments.addAll(Arrays.asList(args));
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type);
        for (BinaryValue arg : arguments) {
            sb.append(" ").append(arg.toString());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BinaryCommand command = (BinaryCommand) obj;
        return type.equals(command.type) && arguments.equals(command.arguments);
    }

    @Override
    public int hashCode() {
        return type.hashCode() * 31 + arguments.hashCode();
    }

    /**
     * Represents a single argument that can be either a string or binary data.
     * This wrapper allows commands to mix string and binary arguments safely.
     */
    public static class BinaryValue {
        private final String stringValue;
        private final byte[] binaryValue;
        private final boolean isBinary;

        private BinaryValue(String stringValue) {
            this.stringValue = stringValue;
            this.binaryValue = null;
            this.isBinary = false;
        }

        private BinaryValue(byte[] binaryValue) {
            this.stringValue = null;
            this.binaryValue = binaryValue != null ? binaryValue.clone() : null;
            this.isBinary = true;
        }

        /**
         * Create a BinaryValue from a string.
         *
         * @param value The string value
         * @return A BinaryValue containing the string
         */
        public static BinaryValue of(String value) {
            return new BinaryValue(value);
        }

        /**
         * Create a BinaryValue from binary data.
         *
         * @param value The binary data
         * @return A BinaryValue containing the binary data
         */
        public static BinaryValue of(byte[] value) {
            return new BinaryValue(value);
        }

        /**
         * Check if this value contains binary data.
         *
         * @return true if this is binary data, false if string
         */
        public boolean isBinary() {
            return isBinary;
        }

        /**
         * Get the string value. Only valid if !isBinary().
         *
         * @return The string value
         * @throws IllegalStateException if this contains binary data
         */
        public String getStringValue() {
            if (isBinary) {
                throw new IllegalStateException("This BinaryValue contains binary data, not a string");
            }
            return stringValue;
        }

        /**
         * Get the binary value. Only valid if isBinary().
         *
         * @return A copy of the binary data
         * @throws IllegalStateException if this contains string data
         */
        public byte[] getBinaryValue() {
            if (!isBinary) {
                throw new IllegalStateException("This BinaryValue contains string data, not binary");
            }
            return binaryValue != null ? binaryValue.clone() : null;
        }

        @Override
        public String toString() {
            if (isBinary) {
                return binaryValue != null ? "[" + binaryValue.length + " bytes]" : "[null bytes]";
            } else {
                return stringValue != null ? stringValue : "[null string]";
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            BinaryValue that = (BinaryValue) obj;
            if (isBinary != that.isBinary) return false;
            if (isBinary) {
                return Arrays.equals(binaryValue, that.binaryValue);
            } else {
                return stringValue != null ? stringValue.equals(that.stringValue) : that.stringValue == null;
            }
        }

        @Override
        public int hashCode() {
            if (isBinary) {
                return Arrays.hashCode(binaryValue);
            } else {
                return stringValue != null ? stringValue.hashCode() : 0;
            }
        }
    }
}