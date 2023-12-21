package glide.ffi.resolvers;

public class GlideCoreNativeDefinitions {
  public static native String startSocketListenerExternal() throws Exception;

  public static native Object valueFromPointer(long pointer);

  static {
    System.loadLibrary("glide-rs");
  }

  /**
   * Make an FFI call to obtain the socket path.
   *
   * @return A UDS path.
   */
  public static String getSocket() {
    try {
      return startSocketListenerExternal();
    } catch (Exception | UnsatisfiedLinkError e) {
      System.err.printf("Failed to create a UDS connection: %s%n%n", e);
      throw new RuntimeException(e);
    }
  }
}
