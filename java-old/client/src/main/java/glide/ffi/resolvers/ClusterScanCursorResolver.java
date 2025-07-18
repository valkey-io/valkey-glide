/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

import glide.managers.CommandManager;

/**
 * Helper class for invoking JNI resources for {@link CommandManager.ClusterScanCursorDetail}
 * implementations.
 */
public final class ClusterScanCursorResolver {
    public static final String FINISHED_CURSOR_HANDLE;

    // TODO: consider lazy loading the glide_rs library
    static {
        NativeUtils.loadGlideLib();
        FINISHED_CURSOR_HANDLE = getFinishedCursorHandleConstant();
    }

    public static native void releaseNativeCursor(String cursor);

    public static native String getFinishedCursorHandleConstant();
}
