package glide.internal.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Binary-safe command request that supports mixed String/byte[] arguments.
 * Preserves binary data integrity while providing the same routing capabilities as CommandRequest.
 */
public class BinaryCommandRequest {
    private final String commandName;
    private final List<Object> arguments; // Can be String or byte[]
    private final RouteInfo routeInfo;

    private BinaryCommandRequest(String commandName, List<Object> arguments, RouteInfo routeInfo) {
        this.commandName = commandName;
        this.arguments = arguments;
        this.routeInfo = routeInfo;
    }

    public String getCommandName() {
        return commandName;
    }

    public List<Object> getArguments() {
        return arguments;
    }

    public RouteInfo getRouteInfo() {
        return routeInfo;
    }

    /**
     * Create binary command with automatic routing
     */
    public static BinaryCommandRequest auto(BinaryCommand command) {
        return new BinaryCommandRequest(
            command.getType(), 
            command.getArguments(), 
            RouteInfo.auto()
        );
    }

    /**
     * Create binary command for all nodes
     */
    public static BinaryCommandRequest forAllNodes(BinaryCommand command) {
        return new BinaryCommandRequest(
            command.getType(), 
            command.getArguments(), 
            RouteInfo.allNodes()
        );
    }

    /**
     * Create binary command for all primaries
     */
    public static BinaryCommandRequest forAllPrimaries(BinaryCommand command) {
        return new BinaryCommandRequest(
            command.getType(), 
            command.getArguments(), 
            RouteInfo.allPrimaries()
        );
    }

    /**
     * Create binary command for random node
     */
    public static BinaryCommandRequest forRandomNode(BinaryCommand command) {
        return new BinaryCommandRequest(
            command.getType(), 
            command.getArguments(), 
            RouteInfo.random()
        );
    }

    /**
     * Create binary command routed by slot key
     */
    public static BinaryCommandRequest forSlotKey(BinaryCommand command, String slotKey, boolean preferReplica) {
        return new BinaryCommandRequest(
            command.getType(), 
            command.getArguments(), 
            RouteInfo.bySlotKey(slotKey, preferReplica)
        );
    }

    /**
     * Create binary command routed by slot ID
     */
    public static BinaryCommandRequest forSlotId(BinaryCommand command, int slotId, boolean preferReplica) {
        return new BinaryCommandRequest(
            command.getType(), 
            command.getArguments(), 
            RouteInfo.bySlotId(slotId, preferReplica)
        );
    }

    /**
     * Create binary command routed by address
     */
    public static BinaryCommandRequest forAddress(BinaryCommand command, String host, int port) {
        return new BinaryCommandRequest(
            command.getType(), 
            command.getArguments(), 
            RouteInfo.byAddress(host, port)
        );
    }

    /**
     * Serialize to binary format for JNI transmission while preserving binary data
     */
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // Write command name length and data
            byte[] cmdBytes = commandName.getBytes(StandardCharsets.UTF_8);
            writeVarInt(baos, cmdBytes.length);
            baos.write(cmdBytes);
            
            // Write argument count
            writeVarInt(baos, arguments.size());
            
            // Write each argument (preserving binary data)
            for (Object arg : arguments) {
                byte[] argBytes;
                if (arg instanceof byte[]) {
                    argBytes = (byte[]) arg;
                } else if (arg instanceof String) {
                    argBytes = ((String) arg).getBytes(StandardCharsets.UTF_8);
                } else {
                    // Convert other objects to string then bytes
                    argBytes = String.valueOf(arg).getBytes(StandardCharsets.UTF_8);
                }
                
                writeVarInt(baos, argBytes.length);
                baos.write(argBytes);
            }
            
            // Write routing information
            routeInfo.writeTo(baos);
            
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize binary command request", e);
        }
    }

    private void writeVarInt(ByteArrayOutputStream baos, int value) throws IOException {
        while ((value & 0x80) != 0) {
            baos.write((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        baos.write((byte) (value & 0x7F));
    }

    @Override
    public String toString() {
        return "BinaryCommandRequest{" +
                "command='" + commandName + '\'' +
                ", argCount=" + arguments.size() +
                ", route=" + routeInfo +
                '}';
    }

    /**
     * Interface for binary commands that support mixed String/byte[] arguments
     */
    public interface BinaryCommand {
        String getType();
        List<Object> getArguments(); // Can contain String or byte[] elements
    }
}
