package io.valkey.glide.jni.metadata;

/**
 * Simple command metadata for POC benchmarking.
 * <p>
 * This class is kept for compatibility but is not used in the simplified POC approach.
 * The POC uses direct method parameters instead of complex metadata structures.
 *
 * @deprecated Use GlideJniClient methods directly (get/set/ping)
 */
@Deprecated
public class CommandMetadata {

    /**
     * Command type constants for the POC (matches Rust enum)
     */
    public static final class CommandType {
        /** GET command */
        public static final int GET = 1;

        /** SET command */
        public static final int SET = 2;

        /** PING command */
        public static final int PING = 3;
    }

    private final int commandType;
    private final int payloadLength;
    private final int callbackIdx;

    /**
     * Create command metadata for the POC
     *
     * @param commandType the command type (GET=1, SET=2, PING=3)
     * @param payloadLength size of the payload in bytes
     */
    public CommandMetadata(int commandType, int payloadLength) {
        this.commandType = commandType;
        this.payloadLength = payloadLength;
        this.callbackIdx = 0; // Not used in POC
    }

    /**
     * Get the command type
     *
     * @return command type
     */
    public int getCommandType() {
        return commandType;
    }

    /**
     * Get the payload length
     *
     * @return payload length in bytes
     */
    public int getPayloadLength() {
        return payloadLength;
    }

    /**
     * Get the callback index
     *
     * @return callback index
     */
    public int getCallbackIdx() {
        return callbackIdx;
    }
}
