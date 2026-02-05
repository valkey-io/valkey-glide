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
     * Compares two byte arrays lexicographically. Java 8 compatible alternative to Arrays.compare
     * (Java 9+).
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
     * Creates an empty HashMap. Java 8 compatible alternative to Map.of() (Java 9+).
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return an empty HashMap
     */
    public static <K, V> Map<K, V> createMap() {
        return new HashMap<>();
    }

    /**
     * Creates a HashMap with one key-value pair. Java 8 compatible alternative to Map.of() (Java 9+).
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
     * Creates a HashMap with two key-value pairs. Java 8 compatible alternative to Map.of() (Java
     * 9+).
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
     * Creates a HashMap with three key-value pairs. Java 8 compatible alternative to Map.of() (Java
     * 9+).
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
     * Creates a HashMap with four key-value pairs. Java 8 compatible alternative to Map.of() (Java
     * 9+).
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
     * Creates a HashMap with five key-value pairs. Java 8 compatible alternative to Map.of() (Java
     * 9+).
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
     * Creates a HashMap with six key-value pairs. Java 8 compatible alternative to Map.of() (Java
     * 9+).
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
     * Creates a HashMap with seven key-value pairs. Java 8 compatible alternative to Map.of() (Java
     * 9+).
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
     * Creates a singleton set. Java 8 compatible alternative to Set.of() (Java 9+).
     *
     * @param <T> the type of element
     * @param element the single element
     * @return a singleton set containing the element
     */
    public static <T> Set<T> createSet(T element) {
        return Collections.singleton(element);
    }

    /**
     * Creates a HashSet with two elements. Java 8 compatible alternative to Set.of() (Java 9+).
     *
     * @param <T> the type of elements
     * @param e1 the first element
     * @param e2 the second element
     * @return a HashSet containing the elements
     */
    public static <T> Set<T> createSet(T e1, T e2) {
        Set<T> set = new HashSet<>();
        set.add(e1);
        set.add(e2);
        return set;
    }

    /**
     * Creates a HashSet with three elements. Java 8 compatible alternative to Set.of() (Java 9+).
     *
     * @param <T> the type of elements
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @return a HashSet containing the elements
     */
    public static <T> Set<T> createSet(T e1, T e2, T e3) {
        Set<T> set = new HashSet<>();
        set.add(e1);
        set.add(e2);
        set.add(e3);
        return set;
    }

    /**
     * Creates an empty list. Java 8 compatible alternative to List.of() (Java 9+).
     *
     * @param <T> the type of elements
     * @return an empty list
     */
    public static <T> List<T> createList() {
        return Collections.emptyList();
    }

    /**
     * Creates a list with elements. Java 8 compatible alternative to List.of() (Java 9+).
     *
     * @param <T> the type of elements
     * @param elements the elements
     * @return a list containing the elements
     */
    @SafeVarargs
    public static <T> List<T> createList(T... elements) {
        return Arrays.asList(elements);
    }

    private Java8Utils() {
        // Utility class, prevent instantiation
    }
}
