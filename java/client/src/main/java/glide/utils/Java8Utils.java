/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Utility methods for Java 8 compatibility. Provides alternatives for Java 9+ APIs. */
public class Java8Utils {

    /**
     * Compares two byte arrays lexicographically.
     *
     * @param a the first array to compare
     * @param b the second array to compare
     * @return the value 0 if the first and second array are equal and contain the same elements in
     *     the same order; a value less than 0 if the first array is lexicographically less than the
     *     second array; and a value greater than 0 if the first array is lexicographically greater
     *     than the second array
     */
    public static int compareByteArrays(byte[] a, byte[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int cmp = Byte.compare(a[i], b[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    /**
     * Creates an empty HashMap.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return an empty HashMap
     */
    public static <K, V> Map<K, V> createMap() {
        return new HashMap<>();
    }

    /**
     * Creates a HashMap with one key-value pair.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @param k1 the key
     * @param v1 the value
     * @return a HashMap containing the key-value pair
     */
    public static <K, V> Map<K, V> createMap(K k1, V v1) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        return map;
    }

    /**
     * Creates a HashMap with two key-value pairs.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @return a HashMap containing the key-value pairs
     */
    public static <K, V> Map<K, V> createMap(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    /**
     * Creates a HashMap with three key-value pairs.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @return a HashMap containing the key-value pairs
     */
    public static <K, V> Map<K, V> createMap(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    /**
     * Creates a HashMap with four key-value pairs.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @param k4 the fourth key
     * @param v4 the fourth value
     * @return a HashMap containing the key-value pairs
     */
    public static <K, V> Map<K, V> createMap(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return map;
    }

    /**
     * Creates a HashMap with five key-value pairs.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @param k4 the fourth key
     * @param v4 the fourth value
     * @param k5 the fifth key
     * @param v5 the fifth value
     * @return a HashMap containing the key-value pairs
     */
    public static <K, V> Map<K, V> createMap(
            K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        return map;
    }

    /**
     * Creates a HashMap with six key-value pairs.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @param k4 the fourth key
     * @param v4 the fourth value
     * @param k5 the fifth key
     * @param v5 the fifth value
     * @param k6 the sixth key
     * @param v6 the sixth value
     * @return a HashMap containing the key-value pairs
     */
    public static <K, V> Map<K, V> createMap(
            K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        map.put(k6, v6);
        return map;
    }

    /**
     * Creates a HashMap with seven key-value pairs.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @param k1 the first key
     * @param v1 the first value
     * @param k2 the second key
     * @param v2 the second value
     * @param k3 the third key
     * @param v3 the third value
     * @param k4 the fourth key
     * @param v4 the fourth value
     * @param k5 the fifth key
     * @param v5 the fifth value
     * @param k6 the sixth key
     * @param v6 the sixth value
     * @param k7 the seventh key
     * @param v7 the seventh value
     * @return a HashMap containing the key-value pairs
     */
    public static <K, V> Map<K, V> createMap(
            K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        map.put(k6, v6);
        map.put(k7, v7);
        return map;
    }

    /**
     * Creates a HashSet with elements.
     *
     * @param <T> the type of elements
     * @param elements the elements
     * @return a HashSet containing the elements
     */
    @SafeVarargs
    public static <T> Set<T> createSet(T... elements) {
        Set<T> set = new HashSet<>();
        for (T element : elements) {
            set.add(element);
        }
        return set;
    }

    /**
     * Creates an empty list.
     *
     * @param <T> the type of elements
     * @return an empty list
     */
    public static <T> List<T> createList() {
        return Collections.emptyList();
    }

    /**
     * Creates a list with elements.
     *
     * @param <T> the type of elements
     * @param elements the elements
     * @return a list containing the elements
     */
    @SafeVarargs
    public static <T> List<T> createList(T... elements) {
        return Arrays.asList(elements);
    }

    /**
     * Repeats a string n times.
     *
     * @param str the string to repeat
     * @param count the number of times to repeat
     * @return the repeated string
     * @throws IllegalArgumentException if count is negative
     */
    public static String repeat(String str, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count is negative: " + count);
        }
        if (count == 0 || str.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    private Java8Utils() {
        // Utility class, prevent instantiation
    }
}
