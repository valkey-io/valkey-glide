/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Utility class for Java 8 compatibility helpers. */
public class Java8Compat {

    /** Repeat a string n times (Java 11+ String.repeat() alternative). */
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

    /** Create an immutable map with 1 entry (Java 9+ Collections.emptyMap() alternative). */
    public static <K, V> Map<K, V> mapOf(K k1, V v1) {
        return Collections.singletonMap(k1, v1);
    }

    /** Create an immutable map with 2 entries (Java 9+ Collections.emptyMap() alternative). */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return Collections.unmodifiableMap(map);
    }

    /** Create an immutable map with 3 entries (Java 9+ Collections.emptyMap() alternative). */
    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return Collections.unmodifiableMap(map);
    }

    /** Create an immutable set with 1 entry (Java 9+ Collections.emptySet() alternative). */
    public static <T> Set<T> setOf(T v1) {
        return Collections.singleton(v1);
    }

    /** Create an immutable set with 2 entries (Java 9+ Collections.emptySet() alternative). */
    public static <T> Set<T> setOf(T v1, T v2) {
        Set<T> set = new HashSet<>();
        set.add(v1);
        set.add(v2);
        return Collections.unmodifiableSet(set);
    }

    /** Create an immutable set with 3 entries (Java 9+ Collections.emptySet() alternative). */
    public static <T> Set<T> setOf(T v1, T v2, T v3) {
        Set<T> set = new HashSet<>();
        set.add(v1);
        set.add(v2);
        set.add(v3);
        return Collections.unmodifiableSet(set);
    }
}
