/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.ffi.resolvers.GlideValueResolver;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    public static native long createLeakedMap(long[] keys, long[] values);

    public static native long createLeakedDouble(double value);

    public static native long createLeakedBoolean(boolean value);

    public static native long createLeakedVerbatimString(String value);

    public static native long createLeakedLongSet(long[] value);

    // This tests that panics do not cross the FFI boundary and an exception is thrown if a panic is
    // caught
    public static native long handlePanics(
            boolean shouldPanic, boolean errorPresent, long value, long defaultValue);

    // This tests that Rust errors are properly converted into Java exceptions and thrown
    public static native long handleErrors(boolean isSuccess, long value, long defaultValue);

    // This tests that a Java exception is properly thrown across the FFI boundary
    public static native void throwException(
            boolean throwTwice, boolean isRuntimeException, String message);

    @Test
    public void respValueToJavaValue_Nil() {
        long ptr = FfiTest.createLeakedNil();
        Object nilValue = GlideValueResolver.valueFromPointer(ptr);
        assertNull(nilValue);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello", "cat", "dog"})
    public void respValueToJavaValue_SimpleString(String input) {
        long ptr = FfiTest.createLeakedSimpleString(input);
        Object simpleStringValue = GlideValueResolver.valueFromPointer(ptr);
        assertEquals(input, simpleStringValue);
    }

    @Test
    public void respValueToJavaValue_Okay() {
        long ptr = FfiTest.createLeakedOkay();
        Object okayValue = GlideValueResolver.valueFromPointer(ptr);
        assertEquals("OK", okayValue);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 100L, 774L, Integer.MAX_VALUE + 1L, Integer.MIN_VALUE - 1L})
    public void respValueToJavaValue_Int(Long input) {
        long ptr = FfiTest.createLeakedInt(input);
        Object longValue = GlideValueResolver.valueFromPointer(ptr);
        assertInstanceOf(Long.class, longValue);
        assertEquals(input, longValue);
    }

    @Test
    @SneakyThrows
    public void respValueToJavaValue_BulkString() {
        // This is explicitly for testing non-ASCII UTF-8 byte sequences.
        // Note that these can't be encoded as String literals without introducing compiler
        // warnings and errors.

        // This is the 'alpha' character.
        byte[] bulkString = new byte[] {(byte) 0xCE, (byte) 0xB1};
        long ptr = FfiTest.createLeakedBulkString(bulkString);
        final String input;
        input = new String(bulkString, StandardCharsets.UTF_8);
        Object bulkStringValue = GlideValueResolver.valueFromPointer(ptr);
        assertEquals(input, bulkStringValue);
    }

    @Test
    public void respValueToJavaValue_Array() {
        long[] array = {1L, 2L, 3L};
        long ptr = FfiTest.createLeakedLongArray(array);
        Object longArrayValue = GlideValueResolver.valueFromPointer(ptr);
        assertInstanceOf(Object[].class, longArrayValue);
        Object[] result = (Object[]) longArrayValue;
        assertArrayEquals(new Object[] {1L, 2L, 3L}, result);
    }

    @Test
    public void respValueToJavaValue_Map() {
        long[] keys = {12L, 14L, 23L};
        long[] values = {1L, 2L, 3L};
        long ptr = FfiTest.createLeakedMap(keys, values);
        Object mapValue = GlideValueResolver.valueFromPointer(ptr);
        assertInstanceOf(HashMap.class, mapValue);
        HashMap<?, ?> result = (HashMap<?, ?>) mapValue;
        assertAll(
                () -> assertEquals(1L, result.get(12L)),
                () -> assertEquals(2L, result.get(14L)),
                () -> assertEquals(3L, result.get(23L)));
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.0d, 25.2d, 103.5d})
    public void respValueToJavaValue_Double(Double input) {
        long ptr = FfiTest.createLeakedDouble(input);
        Object doubleValue = GlideValueResolver.valueFromPointer(ptr);
        assertEquals(input, doubleValue);
    }

    @Test
    public void respValueToJavaValue_Boolean() {
        long ptr = FfiTest.createLeakedBoolean(true);
        Object booleanValue = GlideValueResolver.valueFromPointer(ptr);
        assertTrue((Boolean) booleanValue);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello", "cat", "dog"})
    public void respValueToJavaValue_VerbatimString(String input) {
        long ptr = FfiTest.createLeakedVerbatimString(input);
        Object verbatimStringValue = GlideValueResolver.valueFromPointer(ptr);
        assertEquals(input, verbatimStringValue);
    }

    @Test
    public void respValueToJavaValue_Set() {
        long[] array = {1L, 2L, 2L};
        long ptr = FfiTest.createLeakedLongSet(array);
        Object longSetValue = GlideValueResolver.valueFromPointer(ptr);
        assertInstanceOf(HashSet.class, longSetValue);
        HashSet<?> result = (HashSet<?>) longSetValue;
        assertAll(
                () -> assertTrue(result.contains(1L)),
                () -> assertTrue(result.contains(2L)),
                () -> assertEquals(result.size(), 2));
    }

    @Test
    public void handlePanics_panic() {
        long expectedValue = 0L;
        long value = FfiTest.handlePanics(true, false, 1L, expectedValue);
        assertEquals(expectedValue, value);
    }

    @Test
    public void handlePanics_returnError() {
        long expectedValue = 0L;
        long value = FfiTest.handlePanics(false, true, 1L, expectedValue);
        assertEquals(expectedValue, value);
    }

    @Test
    public void handlePanics_returnValue() {
        long expectedValue = 2L;
        long value = FfiTest.handlePanics(false, false, expectedValue, 0L);
        assertEquals(expectedValue, value);
    }

    @Test
    public void handleErrors_success() {
        long expectedValue = 0L;
        long value = FfiTest.handleErrors(true, expectedValue, 1L);
        assertEquals(expectedValue, value);
    }

    @Test
    public void handleErrors_error() {
        assertThrows(Exception.class, () -> FfiTest.handleErrors(false, 0L, 1L));
    }

    @Test
    public void throwException() {
        assertThrows(Exception.class, () -> FfiTest.throwException(false, false, "My message"));
    }

    @Test
    public void throwException_throwTwice() {
        assertThrows(Exception.class, () -> FfiTest.throwException(true, false, "My message"));
    }

    @Test
    public void throwException_throwRuntimeException() {
        assertThrows(RuntimeException.class, () -> FfiTest.throwException(false, true, "My message"));
    }
}
