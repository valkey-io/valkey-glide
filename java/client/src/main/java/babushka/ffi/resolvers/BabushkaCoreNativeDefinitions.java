package babushka.ffi.resolvers;

public class BabushkaCoreNativeDefinitions {
  public static native String startSocketListenerExternal() throws Exception;

  public static native Object valueFromPointer(long pointer);

  static {
    System.loadLibrary("javababushka");
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
