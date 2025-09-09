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
 * Binary-aware command request format for JNI communication supporting mixed String/byte[] arguments.
 * This extends CommandRequest functionality to handle binary data without string conversion corruption.
 * 
 * <p>Binary Format:
 * <pre>
 * [4 bytes] Command Type Length
 * [N bytes] Command Type (UTF-8 string)  
 * [4 bytes] Argument Count
 * For each argument:
 *   [1 byte] Argument Type (0 = string, 1 = binary)
 *   [4 bytes] Argument Length
 *   [N bytes] Argument Data (UTF-8 string if type=0, raw bytes if type=1)
 * [1 byte] Has Route (0 = let glide-core auto-route, 1 = explicit routing)
 * If Has Route = 1:
 *   [Route Data] - see RouteInfo.toBytes() format
 * </pre>
 */
public final class BinaryCommandRequest {
    
    private final String commandType;
    private final List<BinaryCommand.BinaryValue> arguments;
    private final Optional<RouteInfo> route;
    
    /**
     * Create a binary command request without routing (let glide-core auto-route).
     */
    private BinaryCommandRequest(String commandType, List<BinaryCommand.BinaryValue> arguments, Optional<RouteInfo> route) {
        this.commandType = commandType;
        this.arguments = new ArrayList<>(arguments);
        this.route = route;
    }

    /**
     * Create an auto-routed binary command request.
     * This is the RECOMMENDED approach for most commands.
     */
    public static BinaryCommandRequest auto(BinaryCommand command) {
        return new BinaryCommandRequestBuilder(command).build(Optional.empty());
    }

    /**
     * Create an auto-routed binary command request from type and arguments.
     */
    public static BinaryCommandRequest auto(String commandType, BinaryCommand.BinaryValue... arguments) {
        return new BinaryCommandRequest(commandType, Arrays.asList(arguments), Optional.empty());
    }

    /**
     * Create a binary command request routed to all cluster nodes.
     */
    public static BinaryCommandRequest forAllNodes(BinaryCommand command) {
        return new BinaryCommandRequestBuilder(command).build(Optional.of(RouteInfo.allNodes()));
    }

    /**
     * Create a binary command request routed to all primary nodes.
     */
    public static BinaryCommandRequest forAllPrimaries(BinaryCommand command) {
        return new BinaryCommandRequestBuilder(command).build(Optional.of(RouteInfo.allPrimaries()));
    }

    /**
     * Create a binary command request routed to a specific address.
     */
    public static BinaryCommandRequest forAddress(BinaryCommand command, String host, int port) {
        return new BinaryCommandRequestBuilder(command).build(Optional.of(RouteInfo.byAddress(host, port)));
    }

    /**
     * Create a binary command request routed by slot key.
     */
    public static BinaryCommandRequest forSlotKey(BinaryCommand command, String slotKey, boolean preferReplica) {
        return new BinaryCommandRequestBuilder(command).build(Optional.of(RouteInfo.bySlotKey(slotKey, preferReplica)));
    }

    /**
     * Create a binary command request routed by slot ID.
     */
    public static BinaryCommandRequest forSlotId(BinaryCommand command, int slotId, boolean preferReplica) {
        return new BinaryCommandRequestBuilder(command).build(Optional.of(RouteInfo.bySlotId(slotId, preferReplica)));
    }

    /**
     * Create a binary command request routed to a random node.
     */
    public static BinaryCommandRequest forRandomNode(BinaryCommand command) {
        return new BinaryCommandRequestBuilder(command).build(Optional.of(RouteInfo.random()));
    }

    /**
     * Internal helper that performs multi-word command token splitting for BinaryCommand.
     * Mirrors logic in {@link CommandRequest}: first token is the command type, the remaining
     * tokens are injected as leading string arguments BEFORE any user supplied arguments so
     * server receives: CMD SUBCMD [payload/other args...].
     */
    private static final class BinaryCommandRequestBuilder {
        private final String commandType;
        private final List<BinaryCommand.BinaryValue> args;

        BinaryCommandRequestBuilder(BinaryCommand command) {
            String raw = command.getType();
            String[] tokens = raw.split("\\s+");
            this.commandType = tokens[0];
            // Preserve original argument ordering but inject extra tokens at the front
            this.args = new ArrayList<>();
            // Add secondary command name tokens (if any) as STRING arguments
            if (tokens.length > 1) {
                for (int i = 1; i < tokens.length; i++) {
                    this.args.add(BinaryCommand.BinaryValue.of(tokens[i]));
                }
            }
            // Append original arguments
            this.args.addAll(command.getArguments());
        }

        BinaryCommandRequest build(Optional<RouteInfo> route) {
            return new BinaryCommandRequest(commandType, args, route);
        }
    }

    /**
     * Get the command name for logging/telemetry.
     */
    public String getCommandName() {
        return commandType;
    }

    /**
     * Serialize this binary command request to binary format for JNI transmission.
     * Binary arguments are preserved as raw bytes without string conversion.
     * 
     * @return Binary representation of the command request
     */
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(estimateSize());
            DataOutputStream dos = new DataOutputStream(baos);
            
            // Write command type
            writeString(dos, commandType);

            // Write argument count
            dos.writeInt(arguments.size());
            
            // Write each argument with type marker
            for (BinaryCommand.BinaryValue arg : arguments) {
                if (arg.isBinary()) {
                    dos.writeByte(1); // Binary argument
                    byte[] data = arg.getBinaryValue();
                    dos.writeInt(data != null ? data.length : 0);
                    if (data != null) {
                        dos.write(data);
                    }
                } else {
                    dos.writeByte(0); // String argument
                    writeString(dos, arg.getStringValue());
                }
            }
            
            // Write routing information
            if (route.isPresent()) {
                dos.writeByte(1); // Has explicit routing
                dos.write(route.get().toBytes());
            } else {
                dos.writeByte(0); // No routing - let glide-core decide
            }
            
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize BinaryCommandRequest", e);
        }
    }

    /**
     * Estimate the size needed for binary serialization.
     */
    private int estimateSize() {
        int size = 4 + commandType.getBytes(StandardCharsets.UTF_8).length; // Command type
        size += 4; // Argument count
        for (BinaryCommand.BinaryValue arg : arguments) {
            size += 1; // Type marker
            size += 4; // Length
            if (arg.isBinary()) {
                byte[] data = arg.getBinaryValue();
                size += data != null ? data.length : 0;
            } else {
                String str = arg.getStringValue();
                size += str != null ? str.getBytes(StandardCharsets.UTF_8).length : 0;
            }
        }
        size += 1; // Route marker
        if (route.isPresent()) {
            size += 32; // Rough estimate for route data
        }
        return size;
    }

    /**
     * Write a string to the data output stream with length prefix.
     */
    private void writeString(DataOutputStream dos, String str) throws IOException {
        if (str == null) {
            dos.writeInt(0);
        } else {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(bytes.length);
            dos.write(bytes);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BinaryCommandRequest{")
          .append("command=").append(commandType)
          .append(", arguments=").append(arguments.size())
          .append(", route=").append(route.map(Object::toString).orElse("auto"))
          .append("}");
        return sb.toString();
    }
}