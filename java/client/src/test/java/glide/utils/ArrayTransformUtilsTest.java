/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.models.GlideString;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArrayTransformUtilsTest {

    @Test
    void convertMapToKeyValueStringArray_basic() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");

        assertArrayEquals(
                new String[] {"k1", "v1", "k2", "v2", "k3", "v3"},
                ArrayTransformUtils.convertMapToKeyValueStringArray(map));
    }

    @Test
    void convertMapToKeyValueStringArray_singleEntry() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key", "42");
        assertArrayEquals(
                new String[] {"key", "42"},
                ArrayTransformUtils.convertMapToKeyValueStringArray(map));
    }

    @Test
    void convertMapToKeyValueStringArray_empty() {
        assertEquals(
                0, ArrayTransformUtils.convertMapToKeyValueStringArray(new LinkedHashMap<>()).length);
    }

    @Test
    void convertMapToKeyValueGlideStringArray_basic() {
        Map<GlideString, GlideString> map = new LinkedHashMap<>();
        map.put(gs("k1"), gs("v1"));
        map.put(gs("k2"), gs("v2"));

        assertArrayEquals(
                new GlideString[] {gs("k1"), gs("v1"), gs("k2"), gs("v2")},
                ArrayTransformUtils.convertMapToKeyValueGlideStringArray(map));
    }

    @Test
    void convertMapToKeyValueGlideStringArray_empty() {
        assertEquals(
                0,
                ArrayTransformUtils.convertMapToKeyValueGlideStringArray(new LinkedHashMap<>())
                        .length);
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
