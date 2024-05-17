/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

public class LoggerResolver {
    // TODO: consider lazy loading the glide_rs library
    static {
        System.loadLibrary("glide_rs");
    }

    public static native int initInternal(int level, String fileName);

    public static native void logInternal(int level, String logIdentifier, String message);
}
