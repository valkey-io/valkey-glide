package glide.ffi.resolvers;

public class RuntimeResolver {
    static {
        System.loadLibrary("glide_rs");
    }

    /**
     * Enable or disable auto-exit when all clients are closed
     * @param enabled If true, automatically exits after delay when last client closes
     */
    public static native void setAutoExit(boolean enabled);
    
    /**
     * Set the delay in seconds before auto-exit after last client closes
     * @param delaySecs Delay in seconds (default is 2)
     */
    public static native void setAutoExitDelay(long delaySecs);
}
