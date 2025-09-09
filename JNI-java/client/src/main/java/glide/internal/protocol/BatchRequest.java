package glide.internal.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Batch request binary serializer for JNI.
 * Encodes multiple commands with optional transaction flag, timeout, error policy and routing.
 *
 * Binary format:
 * [u32] command_count
 * For each command i in [0..n):
 *   [u32] command_type_length
 *   [bytes] command_type (UTF-8)
 *   [u32] arg_count
 *   For each arg:
 *     [u32] arg_length
 *     [bytes] arg (UTF-8)
 * [u8] atomic_flag (1 = MULTI/EXEC transaction, 0 = pipeline)
 * [u32] timeout_ms (0 means use client default)
 * [u8] raise_on_error (1 = raise, 0 = include in results)
 * [u8] has_route (1 = explicit route appended, 0 = auto)
 * If has_route == 1:
 *   [RouteInfo bytes] as produced by RouteInfo.toBytes()
 * [u8] binary_output (1 = preserve binary as GlideString, 0 = decode to String) -- appended at end for forward compatibility
 */
public final class BatchRequest {

    private static final byte ARG_TYPE_TEXT = 0;
    private static final byte ARG_TYPE_BINARY = 1;

    private final CommandInterface[] commands;
    private final boolean atomic;
    private final int timeoutMs; // 0 => use client default
    private final boolean raiseOnError;
    private final RouteInfo route; // nullable
    private final boolean binaryOutput;

    public BatchRequest(CommandInterface[] commands, boolean atomic, Integer timeoutMs, boolean raiseOnError, RouteInfo route, boolean binaryOutput) {
        if (commands == null || commands.length == 0) {
            throw new IllegalArgumentException("At least one command is required");
        }
        this.commands = commands;
        this.atomic = atomic;
        this.timeoutMs = timeoutMs != null && timeoutMs > 0 ? timeoutMs : 0;
        this.raiseOnError = raiseOnError;
        this.route = route;
        this.binaryOutput = binaryOutput;
    }

    // Backward compatible constructor (older call sites)
    public BatchRequest(CommandInterface[] commands, boolean atomic, Integer timeoutMs, boolean raiseOnError, RouteInfo route) {
        this(commands, atomic, timeoutMs, raiseOnError, route, false);
    }

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // command count
            dos.writeInt(commands.length);
            boolean debug = System.getProperty("glide.debugBatch") != null || System.getenv("GLIDE_DEBUG_BATCH") != null;
            if (debug) {
                System.err.println("[DEBUG-BATCHSER] command_count=" + commands.length);
            }

            // commands
            for (int ci = 0; ci < commands.length; ci++) {
                CommandInterface cmd = commands[ci];
                writeString(dos, cmd.getType());
                if (debug) {
                    System.err.println("[DEBUG-BATCHSER] cmd[#" + ci + "] type=" + cmd.getType());
                }
                if (cmd.isBinaryCommand()) {
                    BinaryCommand bcmd = (BinaryCommand) cmd;
                    var args = bcmd.getArguments();
                    dos.writeInt(args.size());
                    if (debug) System.err.println("[DEBUG-BATCHSER]  arg_count=" + args.size());
                    for (BinaryCommand.BinaryValue v : args) {
                        if (v.isBinary()) {
                            byte[] bytes = v.getBinaryValue();
                            dos.writeByte(ARG_TYPE_BINARY);
                            dos.writeInt(bytes != null ? bytes.length : 0);
                            if (bytes != null && bytes.length > 0) {
                                dos.write(bytes);
                            }
                            if (debug) System.err.println("[DEBUG-BATCHSER]   arg(binary) len=" + (bytes == null ? 0 : bytes.length));
                        } else {
                            byte[] bytes = v.getStringValue().getBytes(StandardCharsets.UTF_8);
                            dos.writeByte(ARG_TYPE_TEXT);
                            dos.writeInt(bytes.length);
                            dos.write(bytes);
                            if (debug) System.err.println("[DEBUG-BATCHSER]   arg(text) len=" + bytes.length);
                        }
                    }
                } else {
                    Command scmd = (Command) cmd;
                    String[] args = scmd.getArgumentsArray();
                    dos.writeInt(args.length);
                    if (debug) System.err.println("[DEBUG-BATCHSER]  arg_count=" + args.length);
                    for (int ai = 0; ai < args.length; ai++) {
                        String arg = args[ai];
                        if (arg == null) {
                            // Provide rich diagnostics to help locate upstream command builder producing null.
                            String msg = "Null argument encountered during batch serialization: commandIndex=" + ci +
                                    ", commandType=" + cmd.getType() + ", argIndex=" + ai + ". This indicates a bug in batch command construction (an optional parameter was left as null).";
                            if (debug) System.err.println("[DEBUG-BATCHSER]  ERROR " + msg);
                            throw new IllegalArgumentException(msg);
                        }
                        byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
                        dos.writeByte(ARG_TYPE_TEXT);
                        dos.writeInt(bytes.length);
                        dos.write(bytes);
                        if (debug) System.err.println("[DEBUG-BATCHSER]   arg(text) len=" + bytes.length + (bytes.length > (1<<20) ? " (GT1MB)" : ""));
                    }
                }
            }

            // flags and options
            dos.writeByte(atomic ? 1 : 0);
            dos.writeInt(timeoutMs);
            dos.writeByte(raiseOnError ? 1 : 0);

            // route at the end to allow variable length parsing
            if (route != null) {
                dos.writeByte(1);
                dos.write(route.toBytes());
            } else {
                dos.writeByte(0);
            }

            // Append binary output flag at end so older parsers that ignore trailing bytes remain mostly safe
            dos.writeByte(binaryOutput ? 1 : 0);
            if (debug) {
                System.err.println("[DEBUG-BATCHSER] atomic=" + atomic + " timeoutMs=" + timeoutMs + " raiseOnError=" + raiseOnError + " route=" + (route!=null) + " binaryOutput=" + binaryOutput);
                System.err.println("[DEBUG-BATCHSER] total_bytes=" + baos.size());
            }

            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize batch request", e);
        }
    }

    private static void writeString(DataOutputStream dos, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }
}


