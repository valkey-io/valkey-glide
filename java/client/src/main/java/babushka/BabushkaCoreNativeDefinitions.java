package babushka;

public class BabushkaCoreNativeDefinitions {
  public static native String startSocketListenerExternal() throws Exception;

  public static native Object valueFromPointer(long pointer);

  static {
    System.loadLibrary("babushka");
  }
}
