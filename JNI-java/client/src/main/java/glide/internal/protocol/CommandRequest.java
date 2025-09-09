package glide.internal.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Lightweight binary command request format with optional routing for JNI
 * communication.
 * This replaces protobuf with a simple, fast binary format while supporting
 * sophisticated
 * glide-core routing integration for optimal performance and cluster awareness.
 * 
 * <p>
 * Key Architecture:
 * - Default: Let glide-core handle sophisticated routing automatically
 * <p>
 * Binary Format:
 * 
 * <pre>
 * [4 bytes] Command Type Length
 * [N bytes] Command Type (UTF-8 string)  
 * [4 bytes] Argument Count
 * For each argument:
 *   [4 bytes] Argument Length
 *   [N bytes] Argument Data (UTF-8 string)
 * [1 byte] Has Route (0 = let glide-core auto-route, 1 = explicit routing)
 * If Has Route = 1:
 *   [Route Data] - see RouteInfo.toBytes() format
 * </pre>
 */
public final class CommandRequest {
    
    private final String commandType;
    private final List<String> arguments;
    private final List<String> extraCommandTokens;
    private final Optional<RouteInfo> route;
    // Primary token is sent as commandType; remaining tokens (if any) are arguments.
    
    /**
     * Create a command request without routing (let glide-core auto-route).
     * This is the RECOMMENDED approach for 95% of commands as it utilizes
     * all glide-core sophisticated routing optimizations:
     * - Hash slot calculation and cluster topology awareness
     * - Connection pooling and multiplexing  
     * - Multi-slot command splitting and automatic failover
     * - Replica read routing and load balancing
     * 
     * @param commandType The Valkey command type (e.g., "GET", "SET", "HGET")
     * @param arguments List of command arguments
     */
    public CommandRequest(String commandType, List<String> arguments) {
        this(commandType, arguments, Optional.empty(), null);
    }
    
    /**
     * Create a command request with specific routing.
     * Use ONLY when you need to override glide-core automatic routing:
     * - Cluster-wide operations (FLUSHALL, SCRIPT LOAD)
     * - Node-specific debugging (INFO, CONFIG)
     * - Explicit replica read routing
     * - Load balancing specific scenarios
     * 
     * @param commandType The Valkey command type
     * @param arguments List of command arguments  
     * @param route Explicit routing information (null = auto-route)
     */
    public CommandRequest(String commandType, List<String> arguments, RouteInfo route) {
        this(commandType, arguments, Optional.ofNullable(route), null);
    }

    
    /**
     * Internal constructor with validation.
     */
    private CommandRequest(String commandType, List<String> arguments, Optional<RouteInfo> route) {
        this(commandType, arguments, route, null);
    }

    /** Internal primary ctor with optional explicit isTwoWord override */
    private CommandRequest(String commandType, List<String> arguments, Optional<RouteInfo> route,
        Boolean isTwoWordOverride) {
        if (commandType == null || commandType.isEmpty()) {
            throw new IllegalArgumentException("Command type cannot be null or empty");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        
        // Split command type into tokens; first token is the actual command verb.
        String[] tokens = commandType.split("\\s+");
        this.commandType = tokens[0];
        this.extraCommandTokens = tokens.length > 1 ? Arrays.asList(Arrays.copyOfRange(tokens, 1, tokens.length)) : List.of();
        this.arguments = new ArrayList<>(arguments);
        this.route = route;
    }
    
    /**
     * Create a command request with array arguments (no routing).
     * 
     * @param commandType The Valkey command type
     * @param arguments Array of command arguments
     */
    public CommandRequest(String commandType, String... arguments) {
        this(commandType, Arrays.asList(arguments));
    }

    
    /**
     * Get the command type.
     * 
     * @return The Valkey command type
     */
    public String getCommandType() {
        return commandType;
    }
    
    /**
     * Get the command name (alias for getCommandType for OpenTelemetry integration).
     * 
     * @return The Valkey command name
     */
    public String getCommandName() {
        return commandType;
    }
    
    /**
     * Get the command arguments.
     * 
     * @return Copy of the arguments list
     */
    public List<String> getArguments() {
        return new ArrayList<>(arguments);
    }
    
    /**
     * Get the number of arguments.
     * 
     * @return Number of arguments
     */
    public int getArgumentCount() {
        return arguments.size();
    }
    
    /**
     * Get routing information.
     * 
     * @return Optional routing info (empty = let glide-core auto-route)
     */
    public Optional<RouteInfo> getRoute() {
        return route;
    }
    
    /**
     * Serialize this command request to binary format for JNI transmission.
     * The binary format is designed for minimal overhead and fast parsing
     * while supporting optional routing information.
     * 
     * @return Binary representation of the command request
     */
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(estimateSize());
            DataOutputStream dos = new DataOutputStream(baos);
            
            // Write primary command token
            writeString(dos, commandType);

            // Aggregate implicit extra command tokens (multi-word) + user arguments
            int totalArgCount = extraCommandTokens.size() + arguments.size();
            dos.writeInt(totalArgCount);
            for (String extra : extraCommandTokens) {
                writeString(dos, extra);
            }
            for (String arg : arguments) {
                writeString(dos, arg);
            }
            
            // Write routing information
            if (route.isPresent()) {
                dos.writeByte(1); // Has explicit routing
                dos.write(route.get().toBytes());
            } else {
                dos.writeByte(0); // No routing - let glide-core decide (OPTIMAL!)
            }
            
            dos.flush();
            return baos.toByteArray();
            
        } catch (IOException e) {
            // Should never happen with ByteArrayOutputStream
            throw new RuntimeException("Failed to serialize command request", e);
        }
    }
    
    /**
     * Parse a command request from binary format.
     * Used for testing and debugging - normally parsing happens in Rust.
     * 
     * @param bytes Binary data to parse
     * @return Parsed command request
     * @throws IllegalArgumentException if the binary format is invalid
     */
    public static CommandRequest fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            throw new IllegalArgumentException("Invalid binary format: too short");
        }
        
        try {
            java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(bytes)
            );
            
            // Read command type
            String commandType = readString(dis);
            
            // Read argument count and arguments
            int argumentCount = dis.readInt();
            if (argumentCount < 0 || argumentCount > 10000) {
                throw new IllegalArgumentException("Invalid argument count: " + argumentCount);
            }
            
            List<String> arguments = new ArrayList<>(argumentCount);
            for (int i = 0; i < argumentCount; i++) {
                arguments.add(readString(dis));
            }
            
            // Read routing information
            byte hasRoute = dis.readByte();
            Optional<RouteInfo> route = Optional.empty();
            if (hasRoute == 1) {
                // Read remaining bytes for route parsing
                ByteArrayOutputStream remaining = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = dis.read(buffer)) != -1) {
                    remaining.write(buffer, 0, bytesRead);
                }
                // Note: RouteInfo.fromBytes() would need to be implemented
                // route = Optional.of(RouteInfo.fromBytes(remaining.toByteArray()));
            }
            return new CommandRequest(commandType, arguments, route.orElse(null));
            
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse binary format", e);
        }
    }
    
    /**
     * Helper method to write length-prefixed string.
     */
    private void writeString(DataOutputStream dos, String str) throws IOException {
        if (str == null) {
            str = ""; // Convert null to empty string to prevent NPE
        }
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }
    
    /**
     * Helper method to read length-prefixed string.
     */
    private static String readString(java.io.DataInputStream dis) throws IOException {
        int length = dis.readInt();
        if (length <= 0 || length > 1024 * 1024) {
            throw new IllegalArgumentException("Invalid string length: " + length);
        }
        
        byte[] bytes = new byte[length];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    
    // ===== FACTORY METHODS FOR ROUTING SCENARIOS =====
    
    /**
     * Default command (let glide-core handle routing automatically).
     * This utilizes ALL glide-core sophisticated routing optimizations:
     * - Hash slot calculation and cluster topology awareness
     * - Connection pooling and request multiplexing
     * - Multi-slot command splitting for MGET/MSET  
     * - Automatic failover and replica read routing
     * - Load balancing and performance optimizations
     * 
     * THIS IS THE RECOMMENDED METHOD FOR 95% OF COMMANDS!
     */
    public static CommandRequest auto(String command, String... args) {
        return new CommandRequest(command, Arrays.asList(args));
    }
    
    /**
     * Send command to all nodes in cluster.
     * Use for: FLUSHALL, SCRIPT LOAD, CONFIG SET, cluster-wide operations
     */
    public static CommandRequest forAllNodes(String command, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.allNodes());
    }
    
    /**
     * Send command to all primary nodes in cluster.
     * Use for: Cluster PING, CONFIG operations, primary-specific commands
     */
    public static CommandRequest forAllPrimaries(String command, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.allPrimaries());
    }
    
    /**
     * Route command based on slot key to ensure command locality.
     * Use when you need to guarantee the command goes to the node handling a specific key,
     * especially useful for complex multi-key operations or debugging.
     */
    public static CommandRequest forSlotKey(String command, String slotKey, boolean replica, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.bySlotKey(slotKey, replica));
    }
    
    /**
     * Route command to specific slot ID.
     * Use for: Direct slot targeting, advanced cluster debugging
     */
    public static CommandRequest forSlotId(String command, int slotId, boolean replica, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.bySlotId(slotId, replica));
    }
    
    /**
     * Route command to specific node by address.
     * Use for: Node-specific debugging, INFO commands, direct node access
     */
    public static CommandRequest forAddress(String command, String host, int port, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.byAddress(host, port));
    }
    
    /**
     * Route command to random node.
     * Use for: Load balancing read operations, distributing non-key commands
     */
    public static CommandRequest forRandomNode(String command, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.random());
    }

    /**
     * Create a builder for constructing command requests fluently.
     * 
     * @return New builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create a builder starting with a command type.
     * 
     * @param commandType The Valkey command type
     * @return New builder instance with command type set
     */
    public static Builder builder(String commandType) {
        return new Builder().commandType(commandType);
    }
    
    /**
     * Builder for constructing CommandRequest instances fluently.
     */
    public static final class Builder {
        private String commandType;
        private final List<String> arguments = new ArrayList<>();
        private RouteInfo route = null;
        
        private Builder() {}
        
        /**
         * Set the command type.
         * 
         * @param commandType The Valkey command type
         * @return This builder for method chaining
         */
        public Builder commandType(String commandType) {
            this.commandType = commandType;
            return this;
        }
        
        /**
         * Add a single argument.
         * 
         * @param argument The argument to add
         * @return This builder for method chaining
         */
        public Builder arg(String argument) {
            if (argument != null) {
                this.arguments.add(argument);
            }
            return this;
        }
        
        /**
         * Add multiple arguments.
         * 
         * @param args The arguments to add
         * @return This builder for method chaining
         */
        public Builder args(String... args) {
            if (args != null) {
                for (String arg : args) {
                    if (arg != null) {
                        this.arguments.add(arg);
                    }
                }
            }
            return this;
        }
        
        /**
         * Add multiple arguments from a list.
         * 
         * @param args The arguments to add
         * @return This builder for method chaining
         */
        public Builder args(List<String> args) {
            if (args != null) {
                for (String arg : args) {
                    if (arg != null) {
                        this.arguments.add(arg);
                    }
                }
            }
            return this;
        }
        
        /**
         * Set explicit routing (overrides glide-core auto-routing).
         * 
         * @param route The routing information
         * @return This builder for method chaining
         */
        public Builder route(RouteInfo route) {
            this.route = route;
            return this;
        }
        
        /**
         * Build the command request.
         * 
         * @return New CommandRequest instance
         * @throws IllegalArgumentException if command type is not set
         */
        public CommandRequest build() {
            if (commandType == null || commandType.isEmpty()) {
                throw new IllegalArgumentException("Command type must be set");
            }
            return new CommandRequest(commandType, arguments, route);
        }
    }
    
    /**
     * Estimate the serialized size for buffer preallocation.
     * This reduces memory allocations during serialization.
     * 
     * @return Estimated size in bytes
     */
    private int estimateSize() {
        int size = 8; // Command type length + argument count
        size += commandType.length() * 3; // UTF-8 worst case for primary token

        // Include extra multi-word tokens as arguments in size estimation
        for (String t : extraCommandTokens) {
            size += 4; // length prefix
            size += t.length() * 3; // worst case
        }

        for (String arg : arguments) {
            size += 4; // Argument length prefix
            if (arg != null) {
                size += arg.length() * 3; // UTF-8 worst case
            }
        }
        
        size += 1; // Has route flag
        if (route.isPresent()) {
            size += 128; // Estimated route data size
        }
        
        return size;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CommandRequest{");
        sb.append("commandType='").append(commandType).append('\'');
        sb.append(", arguments=").append(arguments);
        if (route.isPresent()) {
            sb.append(", route=").append(route.get());
        } else {
            sb.append(", route=auto");
        }
        sb.append('}');
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CommandRequest that = (CommandRequest) obj;
        return commandType.equals(that.commandType) && 
               arguments.equals(that.arguments) &&
               route.equals(that.route);
    }
    
    @Override
    public int hashCode() {
        return commandType.hashCode() * 31 + arguments.hashCode() * 17 + route.hashCode();
    }
}