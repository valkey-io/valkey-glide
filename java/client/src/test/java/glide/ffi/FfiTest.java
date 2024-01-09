package glide.ffi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FfiTest {

  static {
    System.loadLibrary("glide_rs");
  }

  public static native Object valueFromPointerTest(long pointer);

  public static native long createLeakedNil();
  public static native long createLeakedSimpleString(String value);
  public static native long createLeakedOkay();
  public static native long createLeakedInt(long value);

  @BeforeEach
  public void init() {}

  @AfterEach
  public void teardown() {}

  @Test
  public void redisValueToJavaValue_Nil() {
    long ptr = FfiTest.createLeakedNil();
    Object nilValue = FfiTest.valueFromPointerTest(ptr);
    assertTrue(nilValue == null);
  }

  @Test
  public void redisValueToJavaValue_SimpleString() {
    long ptr = FfiTest.createLeakedSimpleString("hello");
    Object simpleStringValue = FfiTest.valueFromPointerTest(ptr);
    assertTrue(simpleStringValue instanceof String);
    assertEquals((String) simpleStringValue, "hello");
  }

  @Test
  public void redisValueToJavaValue_Okay() {
    long ptr = FfiTest.createLeakedOkay();
    Object okayValue = FfiTest.valueFromPointerTest(ptr);
    assertTrue(okayValue instanceof String);
  }

  @Test
  public void redisValueToJavaValue_Int() {
    long ptr = FfiTest.createLeakedInt(100L);
    Object longValue = FfiTest.valueFromPointerTest(ptr);
    assertTrue(longValue instanceof Long);
  }
}
