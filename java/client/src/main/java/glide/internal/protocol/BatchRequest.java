package glide.internal.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Batch request for executing multiple commands as a transaction or pipeline.
 * Supports both atomic transactions and non-atomic pipelines with routing.
 */
public class BatchRequest {
    private final CommandInterface[] commands;
    private final boolean atomic;
    private final Integer timeoutMs;
    private final boolean raiseOnError;
    private final RouteInfo routeInfo;
    private final boolean binary;

    public BatchRequest(CommandInterface[] commands, boolean atomic, Integer timeoutMs, 
                       boolean raiseOnError, RouteInfo routeInfo, boolean binary) {
        this.commands = commands;
        this.atomic = atomic;
        this.timeoutMs = timeoutMs;
        this.raiseOnError = raiseOnError;
        this.routeInfo = routeInfo != null ? routeInfo : RouteInfo.auto();
        this.binary = binary;
    }

    public CommandInterface[] getCommands() {
        return commands;
    }

    public boolean isAtomic() {
        return atomic;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public boolean isRaiseOnError() {
        return raiseOnError;
    }

    public RouteInfo getRouteInfo() {
        return routeInfo;
    }

    public boolean isBinary() {
        return binary;
    }

    /**
     * Serialize batch request to bytes for JNI transmission
     */
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // Write batch metadata
            baos.write(atomic ? 1 : 0);
            baos.write(raiseOnError ? 1 : 0);
            baos.write(binary ? 1 : 0);
            
            // Write timeout
            if (timeoutMs != null) {
                baos.write(1); // has timeout
                writeVarInt(baos, timeoutMs);
            } else {
                baos.write(0); // no timeout
            }
            
            // Write command count
            writeVarInt(baos, commands.length);
            
            // Write each command
            for (CommandInterface command : commands) {
                writeCommand(baos, command);
            }
            
            // Write routing information
            routeInfo.writeTo(baos);
            
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize batch request", e);
        }
    }

    private void writeCommand(ByteArrayOutputStream baos, CommandInterface command) throws IOException {
        // Write command name
        String commandName = command.getType();
        byte[] cmdBytes = commandName.getBytes(StandardCharsets.UTF_8);
        writeVarInt(baos, cmdBytes.length);
        baos.write(cmdBytes);
        
        // Handle different command types
        if (command instanceof BinaryCommand) {
            BinaryCommand binaryCmd = (BinaryCommand) command;
            List<Object> args = binaryCmd.getArguments();
            
            // Write argument count
            writeVarInt(baos, args.size());
            
            // Write each argument (preserving binary data)
            for (Object arg : args) {
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
        } else {
            // Regular command - convert arguments to strings
            String[] args = command.getArgumentsArray();
            
            // Write argument count
            writeVarInt(baos, args.length);
            
            // Write each argument as string
            for (String arg : args) {
                byte[] argBytes = arg.getBytes(StandardCharsets.UTF_8);
                writeVarInt(baos, argBytes.length);
                baos.write(argBytes);
            }
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
        return "BatchRequest{" +
                "commandCount=" + commands.length +
                ", atomic=" + atomic +
                ", timeout=" + timeoutMs +
                ", raiseOnError=" + raiseOnError +
                ", route=" + routeInfo +
                ", binary=" + binary +
                '}';
    }

    /**
     * Interface for commands that can be included in batches
     */
    public interface CommandInterface {
        String getType();
        String[] getArgumentsArray();
    }

    /**
     * Interface for binary commands in batches
     */
    public interface BinaryCommand extends CommandInterface {
        List<Object> getArguments(); // Can contain String or byte[] elements
    }
}
