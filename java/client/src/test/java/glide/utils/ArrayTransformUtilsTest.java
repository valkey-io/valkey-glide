/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import glide.api.models.GlideString;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
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
}
