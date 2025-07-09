package io.valkey.glide.jni.metadata;

/**
 * Command type constants for the POC (matches Rust command_type enum).
 * <p>
 * This class is kept for compatibility but the simplified POC uses
 * CommandType constants in GlideJniClient instead.
 *
 * @deprecated Use GlideJniClient.CommandType constants instead
 */
@Deprecated
public final class RequestType {
    private RequestType() {
        // Prevent instantiation
    }

    /**
     * GET command
     */
    public static final int GET = 1;

    /**
     * SET command
     */
    public static final int SET = 2;

    /**
     * PING command
     */
    public static final int PING = 3;
}
