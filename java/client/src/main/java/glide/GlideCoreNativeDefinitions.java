package glide;

public class GlideCoreNativeDefinitions {
  public static native String startSocketListenerExternal() throws Exception;

  public static native Object valueFromPointer(long pointer);

  static {
    System.loadLibrary("glide-rs");
  }
}
