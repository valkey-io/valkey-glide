/** Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0 */
package glide.utils;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.models.GlideString;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ArrayTransformUtilsTest {

    @ParameterizedTest
    @MethodSource("provideMapsForConversion")
    void testConvertMapToKeyValueStringArray(Map<String, ?> inputMap, String[] expectedArray) {
        // When
        String[] result = ArrayTransformUtils.convertMapToKeyValueStringArray(inputMap);

        // Then
        assertArrayEquals(expectedArray, result);
    }

    private static Stream<Arguments> provideMapsForConversion() {
        // Given 1: Empty map
        Map<String, Object> emptyMap = new LinkedHashMap<>();

        // Given 2: Map with only String values
        Map<String, Object> stringMap = new LinkedHashMap<>();
        stringMap.put("key1", "value1");
        stringMap.put("key2", "value2");

        // Given 3: Map with mixed value types (Integer, Double, Boolean)
        Map<String, Object> mixedTypeMap = new LinkedHashMap<>();
        mixedTypeMap.put("stringKey", "str");
        mixedTypeMap.put("intKey", 42);
        mixedTypeMap.put("doubleKey", 3.14);
        mixedTypeMap.put("boolKey", true);

        // Given 4: Map with null value
        Map<String, Object> nullValueMap = new LinkedHashMap<>();
        nullValueMap.put("nullKey", null);

        return Stream.of(
                Arguments.of(emptyMap, new String[] {}),
                Arguments.of(stringMap, new String[] {"key1", "value1", "key2", "value2"}),
                Arguments.of(
                        mixedTypeMap,
                        new String[] {
                            "stringKey", "str", "intKey", "42", "doubleKey", "3.14", "boolKey", "true"
                        }),
                Arguments.of(nullValueMap, new String[] {"nullKey", null}));
    }

    @ParameterizedTest
    @MethodSource("provideGlideStringMapsForConversion")
    void testConvertMapToKeyValueGlideStringArray(
            Map<GlideString, GlideString> inputMap, GlideString[] expectedArray) {
        // When
        GlideString[] result = ArrayTransformUtils.convertMapToKeyValueGlideStringArray(inputMap);

        // Then
        assertArrayEquals(expectedArray, result);
    }

    private static Stream<Arguments> provideGlideStringMapsForConversion() {
        // Given 1: Empty map
        Map<GlideString, GlideString> emptyMap = new LinkedHashMap<>();

        // Given 2: Map with values
        Map<GlideString, GlideString> stringMap = new LinkedHashMap<>();
        stringMap.put(gs("key1"), gs("value1"));
        stringMap.put(gs("key2"), gs("value2"));

        // Given 3: Map with null value
        Map<GlideString, GlideString> nullValueMap = new LinkedHashMap<>();
        nullValueMap.put(gs("nullKey"), null);

        return Stream.of(
                Arguments.of(emptyMap, new GlideString[] {}),
                Arguments.of(
                        stringMap, new GlideString[] {gs("key1"), gs("value1"), gs("key2"), gs("value2")}),
                Arguments.of(nullValueMap, new GlideString[] {gs("nullKey"), null}));
    }

    @Test
    void convertMapToValueKeyStringArray_basic() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("a", 1.0);
        map.put("b", 2.5);

        assertArrayEquals(
                new String[] {"1.0", "a", "2.5", "b"},
                ArrayTransformUtils.convertMapToValueKeyStringArray(map));
    }

    @Test
    void convertMapToValueKeyStringArray_empty() {
        assertEquals(
                0,
                ArrayTransformUtils.convertMapToValueKeyStringArray(new LinkedHashMap<>()).length);
    }

    @Test
    void convertMapToValueKeyStringArrayBinary_basic() {
        Map<GlideString, Double> map = new LinkedHashMap<>();
        map.put(gs("m1"), 3.0);
        map.put(gs("m2"), 7.5);

        assertArrayEquals(
                new GlideString[] {gs("3.0"), gs("m1"), gs("7.5"), gs("m2")},
                ArrayTransformUtils.convertMapToValueKeyStringArrayBinary(map));
    }

    @Test
    void flattenMapToGlideStringArray_basic() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("k1", "v1");
        map.put("k2", "v2");

        assertArrayEquals(
                new GlideString[] {gs("k1"), gs("v1"), gs("k2"), gs("v2")},
                ArrayTransformUtils.flattenMapToGlideStringArray(map));
    }

    @Test
    void flattenMapToGlideStringArray_empty() {
        assertEquals(
                0, ArrayTransformUtils.flattenMapToGlideStringArray(new LinkedHashMap<>()).length);
    }

    @Test
    void flattenMapToGlideStringArrayValueFirst_basic() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("k1", "v1");
        map.put("k2", "v2");

        assertArrayEquals(
                new GlideString[] {gs("v1"), gs("k1"), gs("v2"), gs("k2")},
                ArrayTransformUtils.flattenMapToGlideStringArrayValueFirst(map));
    }

    @Test
    void flattenAllKeysFollowedByAllValues_basic() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");

        assertArrayEquals(
                new GlideString[] {gs("k1"), gs("k2"), gs("k3"), gs("v1"), gs("v2"), gs("v3")},
                ArrayTransformUtils.flattenAllKeysFollowedByAllValues(map));
    }

    @Test
    void flattenAllKeysFollowedByAllValues_singleEntry() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("k", "v");
        assertArrayEquals(
                new GlideString[] {gs("k"), gs("v")},
                ArrayTransformUtils.flattenAllKeysFollowedByAllValues(map));
    }

    @Test
    void flattenAllKeysFollowedByAllValues_empty() {
        assertEquals(
                0,
                ArrayTransformUtils.flattenAllKeysFollowedByAllValues(new LinkedHashMap<>())
                        .length);
    }

    @Test
    void resultSize_isAlwaysInputSizeTimesTwo() {
        int n = 17;
        Map<GlideString, GlideString> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            map.put(gs("k" + i), gs("v" + i));
        }
        assertEquals(n * 2, ArrayTransformUtils.convertMapToKeyValueGlideStringArray(map).length);
        assertEquals(n * 2, ArrayTransformUtils.flattenMapToGlideStringArray(map).length);
        assertEquals(n * 2, ArrayTransformUtils.flattenAllKeysFollowedByAllValues(map).length);
    }
}
