package io.valkey.glide.jni.metadata;

/**
 * Feature flags - not used in simplified POC.
 * <p>
 * The POC uses simple blocking calls without advanced features.
 *
 * @deprecated Not used in simplified POC approach
 */
@Deprecated
public final class Flags {
    private Flags() {
        // Prevent instantiation
    }

    /**
     * No flags used in POC
     */
    public static final short NONE = 0;
}
