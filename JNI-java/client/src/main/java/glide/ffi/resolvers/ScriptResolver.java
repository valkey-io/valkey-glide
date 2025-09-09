/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

/**
 * Resolver for script management operations with native storage.
 * <p>
 * This class provides JNI bindings to glide-core's script container system,
 * enabling efficient script storage and retrieval by SHA1 hash.
 */
public class ScriptResolver {

    static {
        // Load the native library (same as GlideClient)
        try {
            System.loadLibrary("valkey_glide");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load native library for ScriptResolver: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Stores a Lua script in the native script container and returns its SHA1 hash.
     * <p>
     * The script is stored with reference counting, allowing multiple clients
     * to share the same script efficiently.
     *
     * @param code The Lua script source code as bytes
     * @return String representing the SHA1 hash of the stored script
     * @throws RuntimeException if the script cannot be stored
     */
    public static native String storeScript(byte[] code);

    /**
     * Removes a script from the native script container by its SHA1 hash.
     * <p>
     * This decrements the reference count for the script. The script is only
     * physically removed when the reference count reaches zero.
     *
     * @param hash The SHA1 hash of the script to remove
     */
    public static native void dropScript(String hash);
}