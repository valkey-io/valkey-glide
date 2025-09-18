/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command request class for all protocol operations.
 * Provides efficient serialization with caching for common patterns.
 * Handles single commands, batches, transactions, and binary data.
 */
public class CommandRequest {
    // Cache for serialized command names to avoid repeated encoding
    private static final ConcurrentHashMap<String, byte[]> COMMAND_NAME_CACHE = new ConcurrentHashMap<>();

    // Cache for serialized route information
    private static final ConcurrentHashMap<RouteInfo, byte[]> ROUTE_CACHE = new ConcurrentHashMap<>();

    // Pre-allocated common byte arrays
    private static final byte[] ZERO = new byte[] {0};
    private static final byte[] ONE = new byte[] {1};

    // Command data
    private final String commandName;
    private final List<Object> arguments; // Can contain String or byte[]
    private final RouteInfo routeInfo;

    // Batch/transaction data
    private final List<CommandRequest> commands; // For batch operations
    private final boolean atomic;  // true for transactions
    private final Integer timeoutMs;
    private final boolean raiseOnError;
    private final boolean binary;  // true if contains binary data

    // Cached serialized form
    private byte[] cachedBytes = null;

    /**
     * Private constructor - use factory methods.
     */
    private CommandRequest(
            String commandName,
            List<Object> arguments,
            RouteInfo routeInfo,
            List<CommandRequest> commands,
            boolean atomic,
            Integer timeoutMs,
            boolean raiseOnError,
            boolean binary) {
        this.commandName = commandName;
        this.arguments = arguments;
        this.routeInfo = routeInfo != null ? routeInfo : RouteInfo.auto();
        this.commands = commands;
        this.atomic = atomic;
        this.timeoutMs = timeoutMs;
        this.raiseOnError = raiseOnError;
        this.binary = binary;
    }

    // ==================== Single Command Factory Methods ====================

    /**
     * Create a simple string command with automatic routing.
     */
    public static CommandRequest auto(String commandName, String... args) {
        return new CommandRequest(
            commandName,
            args != null ? Arrays.<Object>asList((Object[]) args) : Collections.emptyList(),
            RouteInfo.auto(),
            null, false, null, false, false
        );
    }

    /**
     * Create a string command for all nodes.
     */
    public static CommandRequest forAllNodes(String commandName, String... args) {
        return new CommandRequest(
            commandName,
            args != null ? Arrays.<Object>asList((Object[]) args) : Collections.emptyList(),
            RouteInfo.allNodes(),
            null, false, null, false, false
        );
    }

    /**
     * Create a string command for all primaries.
     */
    public static CommandRequest forAllPrimaries(String commandName, String... args) {
        return new CommandRequest(
            commandName,
            args != null ? Arrays.<Object>asList((Object[]) args) : Collections.emptyList(),
            RouteInfo.allPrimaries(),
            null, false, null, false, false
        );
    }

    /**
     * Create a string command for a random node.
     */
    public static CommandRequest forRandomNode(String commandName, String... args) {
        return new CommandRequest(
            commandName,
            args != null ? Arrays.<Object>asList((Object[]) args) : Collections.emptyList(),
            RouteInfo.random(),
            null, false, null, false, false
        );
    }

    /**
     * Create a string command routed by slot key.
     */
    public static CommandRequest forSlotKey(String commandName, String slotKey, boolean preferReplica, String... args) {
        return new CommandRequest(
            commandName,
            args != null ? Arrays.<Object>asList((Object[]) args) : Collections.emptyList(),
            RouteInfo.bySlotKey(slotKey, preferReplica),
            null, false, null, false, false
        );
    }

    /**
     * Create a string command routed by slot ID.
     */
    public static CommandRequest forSlotId(String commandName, int slotId, boolean preferReplica, String... args) {
        return new CommandRequest(
            commandName,
            args != null ? Arrays.<Object>asList((Object[]) args) : Collections.emptyList(),
            RouteInfo.bySlotId(slotId, preferReplica),
            null, false, null, false, false
        );
    }

    /**
     * Create a string command routed by address.
     */
    public static CommandRequest forAddress(String commandName, String host, int port, String... args) {
        return new CommandRequest(
            commandName,
            args != null ? Arrays.<Object>asList((Object[]) args) : Collections.emptyList(),
            RouteInfo.byAddress(host, port),
            null, false, null, false, false
        );
    }

    // ==================== Binary Command Factory Methods ====================

    /**
     * Create a binary command with automatic routing.
     */
    public static CommandRequest binaryAuto(String commandName, List<Object> args) {
        return new CommandRequest(
            commandName,
            args != null ? args : Collections.emptyList(),
            RouteInfo.auto(),
            null, false, null, false, true
        );
    }

    /**
     * Create a binary command for all nodes.
     */
    public static CommandRequest binaryForAllNodes(String commandName, List<Object> args) {
        return new CommandRequest(
            commandName,
            args != null ? args : Collections.emptyList(),
            RouteInfo.allNodes(),
            null, false, null, false, true
        );
    }

    /**
     * Create a binary command for all primaries.
     */
    public static CommandRequest binaryForAllPrimaries(String commandName, List<Object> args) {
        return new CommandRequest(
            commandName,
            args != null ? args : Collections.emptyList(),
            RouteInfo.allPrimaries(),
            null, false, null, false, true
        );
    }

    /**
     * Create a binary command for a random node.
     */
    public static CommandRequest binaryForRandomNode(String commandName, List<Object> args) {
        return new CommandRequest(
            commandName,
            args != null ? args : Collections.emptyList(),
            RouteInfo.random(),
            null, false, null, false, true
        );
    }

    /**
     * Create a binary command routed by slot key.
     */
    public static CommandRequest binaryForSlotKey(String commandName, String slotKey, boolean preferReplica, List<Object> args) {
        return new CommandRequest(
            commandName,
            args != null ? args : Collections.emptyList(),
            RouteInfo.bySlotKey(slotKey, preferReplica),
            null, false, null, false, true
        );
    }

    // ==================== Batch/Transaction Factory Methods ====================

    /**
     * Create a pipeline (non-atomic batch).
     */
    public static CommandRequest pipeline(List<CommandRequest> commands, RouteInfo routeInfo, boolean binary) {
        return new CommandRequest(
            null, null, routeInfo,
            commands, false, null, false, binary
        );
    }

    /**
     * Create a pipeline with timeout.
     */
    public static CommandRequest pipeline(List<CommandRequest> commands, RouteInfo routeInfo, int timeoutMs, boolean binary) {
        return new CommandRequest(
            null, null, routeInfo,
            commands, false, timeoutMs, false, binary
        );
    }

    /**
     * Create a transaction (atomic batch).
     */
    public static CommandRequest transaction(List<CommandRequest> commands, RouteInfo routeInfo, boolean binary) {
        return new CommandRequest(
            null, null, routeInfo,
            commands, true, null, false, binary
        );
    }

    /**
     * Create a transaction with timeout.
     */
    public static CommandRequest transaction(List<CommandRequest> commands, RouteInfo routeInfo, int timeoutMs, boolean binary) {
        return new CommandRequest(
            null, null, routeInfo,
            commands, true, timeoutMs, false, binary
        );
    }


    // ==================== Serialization ====================

    /**
     * Serialize to bytes for transmission.
     * Uses caching for optimal performance.
     */
    public byte[] toBytes() {
        if (cachedBytes != null) {
            return cachedBytes;
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256);

            if (isBatch()) {
                writeBatch(baos);
            } else {
                writeSingleCommand(baos);
            }

            cachedBytes = baos.toByteArray();
            return cachedBytes;
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize command", e);
        }
    }

    private void writeSingleCommand(ByteArrayOutputStream baos) throws IOException {
        // Write cached command name
        byte[] cmdBytes = getCachedCommandName(commandName);
        writeVarInt(baos, cmdBytes.length);
        baos.write(cmdBytes);

        // Write argument count
        writeVarInt(baos, arguments.size());

        // Write arguments
        for (Object arg : arguments) {
            byte[] argBytes;
            if (arg instanceof byte[]) {
                argBytes = (byte[]) arg;
            } else if (arg instanceof String) {
                argBytes = ((String) arg).getBytes(StandardCharsets.UTF_8);
            } else {
                argBytes = String.valueOf(arg).getBytes(StandardCharsets.UTF_8);
            }
            writeVarInt(baos, argBytes.length);
            baos.write(argBytes);
        }

        // Write cached route info
        byte[] routeBytes = getCachedRoute(routeInfo);
        baos.write(routeBytes);
    }

    private void writeBatch(ByteArrayOutputStream baos) throws IOException {
        // Write header
        baos.write(atomic ? ONE : ZERO);
        baos.write(raiseOnError ? ONE : ZERO);
        baos.write(binary ? ONE : ZERO);

        // Write timeout
        if (timeoutMs != null) {
            baos.write(ONE);
            writeVarInt(baos, timeoutMs);
        } else {
            baos.write(ZERO);
        }

        // Write command count
        writeVarInt(baos, commands.size());

        // Write each command (without route info - batch has single route)
        for (CommandRequest cmd : commands) {
            writeCommandInBatch(baos, cmd);
        }

        // Write cached route info
        byte[] routeBytes = getCachedRoute(routeInfo);
        baos.write(routeBytes);
    }

    private void writeCommandInBatch(ByteArrayOutputStream baos, CommandRequest cmd) throws IOException {
        // Write cached command name
        byte[] cmdBytes = getCachedCommandName(cmd.commandName);
        writeVarInt(baos, cmdBytes.length);
        baos.write(cmdBytes);

        // Write argument count
        writeVarInt(baos, cmd.arguments.size());

        // Write arguments
        for (Object arg : cmd.arguments) {
            byte[] argBytes;
            if (arg instanceof byte[]) {
                argBytes = (byte[]) arg;
            } else if (arg instanceof String) {
                argBytes = ((String) arg).getBytes(StandardCharsets.UTF_8);
            } else {
                argBytes = String.valueOf(arg).getBytes(StandardCharsets.UTF_8);
            }
            writeVarInt(baos, argBytes.length);
            baos.write(argBytes);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Writes a variable-length integer to the output stream.
     * Uses LEB128 encoding for efficient space usage.
     */
    private static void writeVarInt(ByteArrayOutputStream baos, int value) throws IOException {
        while ((value & 0x80) != 0) {
            baos.write((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        baos.write((byte) (value & 0x7F));
    }

    // ==================== Caching ====================

    private static byte[] getCachedCommandName(String commandName) {
        return COMMAND_NAME_CACHE.computeIfAbsent(commandName,
            name -> name.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] getCachedRoute(RouteInfo routeInfo) {
        return ROUTE_CACHE.computeIfAbsent(routeInfo, route -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                route.writeTo(baos);
                return baos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Failed to cache route info", e);
            }
        });
    }

    /**
     * Clear all caches. Useful for testing or when memory needs to be freed.
     */
    public static void clearCaches() {
        COMMAND_NAME_CACHE.clear();
        ROUTE_CACHE.clear();
    }

    // ==================== Getters ====================

    public String getCommandName() {
        return commandName;
    }

    public List<Object> getArguments() {
        return arguments;
    }

    /**
     * Get arguments as String array for compatibility.
     */
    public String[] getArgumentsArray() {
        if (arguments == null) {
            return new String[0];
        }
        String[] result = new String[arguments.size()];
        for (int i = 0; i < arguments.size(); i++) {
            Object arg = arguments.get(i);
            if (arg instanceof byte[]) {
                result[i] = new String((byte[]) arg, StandardCharsets.UTF_8);
            } else {
                result[i] = String.valueOf(arg);
            }
        }
        return result;
    }

    public RouteInfo getRouteInfo() {
        return routeInfo;
    }

    public boolean isBatch() {
        return commands != null;
    }

    public boolean isAtomic() {
        return atomic;
    }

    public boolean isBinary() {
        return binary;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public boolean isRaiseOnError() {
        return raiseOnError;
    }

    public List<CommandRequest> getCommands() {
        return commands;
    }

    @Override
    public String toString() {
        if (isBatch()) {
            return "CommandRequest{batch size=" + commands.size() +
                   ", atomic=" + atomic +
                   ", timeout=" + timeoutMs +
                   ", route=" + routeInfo + "}";
        } else {
            return "CommandRequest{name='" + commandName +
                   "', args=" + (arguments != null ? arguments.size() : 0) +
                   ", route=" + routeInfo +
                   ", binary=" + binary + "}";
        }
    }
}