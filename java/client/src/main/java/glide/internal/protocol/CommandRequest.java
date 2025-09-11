package glide.internal.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a command request with routing information for JNI execution.
 * Handles serialization of commands with routing metadata for glide-core processing.
 */
public class CommandRequest {
    private final String commandName;
    private final List<String> arguments;
    private final RouteInfo routeInfo;

    private CommandRequest(String commandName, List<String> arguments, RouteInfo routeInfo) {
        this.commandName = commandName;
        this.arguments = arguments;
        this.routeInfo = routeInfo;
    }

    public String getCommandName() {
        return commandName;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public RouteInfo getRouteInfo() {
        return routeInfo;
    }

    /**
     * Create command with automatic routing (recommended for most cases)
     */
    public static CommandRequest auto(String command, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.auto());
    }

    /**
     * Create command for all nodes in cluster
     */
    public static CommandRequest forAllNodes(String command, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.allNodes());
    }

    /**
     * Create command for all primary nodes
     */
    public static CommandRequest forAllPrimaries(String command, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.allPrimaries());
    }

    /**
     * Create command for random node
     */
    public static CommandRequest forRandomNode(String command, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.random());
    }

    /**
     * Create command routed by slot key
     */
    public static CommandRequest forSlotKey(String command, String slotKey, boolean preferReplica, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.bySlotKey(slotKey, preferReplica));
    }

    /**
     * Create command routed by slot ID
     */
    public static CommandRequest forSlotId(String command, int slotId, boolean preferReplica, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.bySlotId(slotId, preferReplica));
    }

    /**
     * Create command routed by address
     */
    public static CommandRequest forAddress(String command, String host, int port, String... args) {
        return new CommandRequest(command, Arrays.asList(args), RouteInfo.byAddress(host, port));
    }

    /**
     * Serialize to protobuf format for JNI transmission
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
            
            // Write each argument
            for (String arg : arguments) {
                byte[] argBytes = arg.getBytes(StandardCharsets.UTF_8);
                writeVarInt(baos, argBytes.length);
                baos.write(argBytes);
            }
            
            // Write routing information
            routeInfo.writeTo(baos);
            
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize command request", e);
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
        return "CommandRequest{" +
                "command='" + commandName + '\'' +
                ", args=" + arguments +
                ", route=" + routeInfo +
                '}';
    }
}
