package babushka.ffi.resolvers;

public class SocketListenerResolver {

  /** Make an FFI call to Babushka to open a UDS socket to connect to. */
  private static native String startSocketListener() throws Exception;

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
      return startSocketListener();
    } catch (Exception | UnsatisfiedLinkError e) {
      System.err.printf("Failed to create a UDS connection: %s%n%n", e);
      throw new RuntimeException(e);
    }
  }
}
