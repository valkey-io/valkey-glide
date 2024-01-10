package glide.ffi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.ffi.resolvers.RedisValueResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.HashSet;

public class FfiTest {

  static {
    System.loadLibrary("glide_rs");
  }

  public static native long createLeakedNil();
  public static native long createLeakedSimpleString(String value);
  public static native long createLeakedOkay();
  public static native long createLeakedInt(long value);
  public static native long createLeakedBulkString(byte[] value);
  public static native long createLeakedLongArray(long[] value);
  public static native long createLeakedMap();
  public static native long createLeakedDouble(double value);
  public static native long createLeakedBoolean(boolean value);
  public static native long createLeakedVerbatimString(String value);
  public static native long createLeakedLongSet(long[] value);

  @BeforeEach
  public void init() {}

  @AfterEach
  public void teardown() {}

  @Test
  public void redisValueToJavaValue_Nil() {
    long ptr = FfiTest.createLeakedNil();
    Object nilValue = RedisValueResolver.valueFromPointer(ptr);
    assertTrue(nilValue == null);
  }

  @Test
  public void redisValueToJavaValue_SimpleString() {
    long ptr = FfiTest.createLeakedSimpleString("hello");
    Object simpleStringValue = RedisValueResolver.valueFromPointer(ptr);
    assertTrue(simpleStringValue instanceof String);
    assertEquals((String) simpleStringValue, "hello");
  }

  @Test
  public void redisValueToJavaValue_Okay() {
    long ptr = FfiTest.createLeakedOkay();
    Object okayValue = RedisValueResolver.valueFromPointer(ptr);
    assertTrue(okayValue instanceof String);
    assertEquals((String) okayValue, "OK");
  }

  @Test
  public void redisValueToJavaValue_Int() {
    long ptr = FfiTest.createLeakedInt(100L);
    Object longValue = RedisValueResolver.valueFromPointer(ptr);
    assertTrue(longValue instanceof Long);
    assertEquals((Long) longValue, 100L);
  }

  @Test
  public void redisValueToJavaValue_BulkString() {
    byte[] bulkString = "hello".getBytes();
    long ptr = FfiTest.createLeakedBulkString(bulkString);
    Object bulkStringValue = RedisValueResolver.valueFromPointer(ptr);
    assertTrue(bulkStringValue instanceof String);
    assertEquals((String) bulkStringValue, "hello");
  }

  @Test
  public void redisValueToJavaValue_Array() {
    long[] array = { 1L, 2L, 3L };
    long ptr = FfiTest.createLeakedLongArray(array);
    Object longArrayValue = RedisValueResolver.valueFromPointer(ptr);
    assertTrue(longArrayValue instanceof Object[]);
    Object[] result = (Object[]) longArrayValue;
    assertEquals((Long) result[0], 1L);
    assertEquals((Long) result[1], 2L);
    assertEquals((Long) result[2], 3L);
  }

  @Test
  public void redisValueToJavaValue_Map() {
    long ptr = FfiTest.createLeakedMap();
    Object mapValue = RedisValueResolver.valueFromPointer(ptr);
    assertTrue(mapValue instanceof HashMap);
    HashMap<Object, Object> result = (HashMap<Object, Object>) mapValue;
    assertEquals(result.get(1L), 2L);
    assertEquals(result.get(3L), "hi");
  }

  @Test
  public void redisValueToJavaValue_Double() {
    long ptr = FfiTest.createLeakedDouble(1.0d);
    Object doubleValue = RedisValueResolver.valueFromPointer(ptr);
    assertTrue(doubleValue instanceof Double);
    assertEquals((Double) doubleValue, 1.0d);
  }

  @Test
  public void redisValueToJavaValue_Boolean() {
    long ptr = FfiTest.createLeakedBoolean(true);
    Object booleanValue = RedisValueResolver.valueFromPointer(ptr);
    assertTrue(booleanValue instanceof Boolean);
    assertEquals((Boolean) booleanValue, true);
  }

  @Test
  public void redisValueToJavaValue_VerbatimString() {
    long ptr = FfiTest.createLeakedVerbatimString("hello");
    Object verbatimStringValue = RedisValueResolver.valueFromPointer(ptr);
    assertTrue(verbatimStringValue instanceof String);
    assertEquals((String) verbatimStringValue, "hello");
  }

  @Test
  public void redisValueToJavaValue_Set() {
    long[] array = { 1L, 2L, 2L };
    long ptr = FfiTest.createLeakedLongSet(array);
    Object longSetValue = RedisValueResolver.valueFromPointer(ptr);
    assertTrue(longSetValue instanceof HashSet);
    HashSet<Long> result = (HashSet<Long>) longSetValue;
    assertTrue(result.contains(1L));
    assertTrue(result.contains(2L));
    assertEquals(result.size(), 2);
  }
}
