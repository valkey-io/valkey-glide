/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

public final class ScriptResolver {

    static {
        NativeUtils.loadGlideLib();
    }

    /**
     * Loads a Lua script into the scripts cache, without executing it.
     *
     * @param code The Lua script
     * @return String representing the saved hash
     */
    public static native String storeScript(byte[] code);

    public static native void dropScript(String sha1);
}
