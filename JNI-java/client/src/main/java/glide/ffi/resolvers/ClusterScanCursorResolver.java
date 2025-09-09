/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

/**
 * Resolver for cluster scan cursor management with native storage.
 * <p>
 * This class provides JNI bindings to glide-core's cluster scan container system,
 * enabling efficient cursor storage and retrieval for cluster scan operations.
 */
public final class ClusterScanCursorResolver {

    static {
        // Load the native library (same as GlideClient)
        try {
            System.loadLibrary("valkey_glide");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load native library for ClusterScanCursorResolver: " + e.getMessage());
            throw e;
        }
    }

    /** Constant representing a finished cluster scan cursor. */
    public static final String FINISHED_CURSOR_HANDLE;

    static {
        // Initialize the finished cursor constant from native code
        FINISHED_CURSOR_HANDLE = getFinishedCursorHandleConstant();
    }

    /**
     * Releases a native cluster scan cursor by its ID.
     * <p>
     * This removes the cursor from the native container and frees associated resources.
     *
     * @param cursor The cursor ID to release
     */
    public static native void releaseNativeCursor(String cursor);

    /**
     * Gets the constant value representing a finished cluster scan cursor.
     * <p>
     * This constant is used to indicate that a cluster scan operation has completed.
     *
     * @return The finished cursor handle constant
     */
    public static native String getFinishedCursorHandleConstant();
}