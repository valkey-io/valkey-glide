/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a returned value object from a the server with cluster-mode enabled. The response type
 * may depend on the submitted {@link Route}.
 *
 * @apiNote ClusterValue stores values in a union-like object. It contains a single-value or
 *     multi-value response from the server. If the command's routing is to a single node use {@link
 *     #getSingleValue()} to return a response of type <code>T</code>. Otherwise, use {@link
 *     #getMultiValue()} to return a <code>Map</code> of <code>address: nodeResponse</code> where
 *     <code>address</code> is of type <code>string</code> and <code>nodeResponse</code> is of type
 *     <code>T</code>.
 * @see <a href="https://valkey.io/docs/topics/cluster-spec/">Valkey cluster specification</a>
 * @param <T> The wrapped response type
 */
public class ClusterValue<T> {
    private Map<String, T> multiValue = null;

    private T singleValue = null;

    private ClusterValue() {}

    /**
     * GET per-node value.<br>
     * Asserts if {@link #hasMultiData()} is false.
     */
    public Map<String, T> getMultiValue() {
        assert hasMultiData() : "No multi value stored";
        return multiValue;
    }

    /**
     * GET the single value.<br>
     * Asserts if {@link #hasSingleData()} is false.
     */
    public T getSingleValue() {
        assert hasSingleData() : "No single value stored";
        return singleValue;
    }

    /** A constructor for the value with type auto-detection. */
    @SuppressWarnings("unchecked")
    public static <T> ClusterValue<T> of(Object data) {
        if (data instanceof Map) {
            var map = (Map<?, T>) data;
            if (map.isEmpty() || map.keySet().toArray()[0] instanceof String) {
                @SuppressWarnings("unchecked")
                var stringKeyed = (Map<String, T>) map;
                // Re-enable uniform collapsing by default for commands that benefit from it
                T uniform = tryComputeUniformValue(stringKeyed);
                if (uniform != null) {
                    debugUniformCollapse(stringKeyed.size(), uniform);
                    return ofSingleValue(uniform);
                }
                return ofMultiValue(stringKeyed);
            } else { // GlideString
                @SuppressWarnings("unchecked")
                var glideKeyed = (Map<GlideString, T>) map;
                ClusterValue<T> multi = ofMultiValueBinary(glideKeyed);
                // Re-enable uniform collapsing for GlideString maps too
                if (multi.multiValue != null) {
                    T uniform = tryComputeUniformValue(multi.multiValue);
                    if (uniform != null) {
                        debugUniformCollapse(multi.multiValue.size(), uniform);
                        return ofSingleValue(uniform);
                    }
                }
                return multi;
            }
        } else if (data instanceof Object[]) {
            // Heuristically decide whether this Object[] encodes flat key/value pairs (address,value)
            // or should be treated as a single scalar array value (e.g. CLUSTER SLOTS nested arrays).
            var array = (Object[]) data;
            boolean looksLikePairs = array.length % 2 == 0 && array.length > 0;
            if (looksLikePairs) {
                for (int i = 0; i < array.length; i += 2) {
                    if (!(array[i] instanceof String)) { // address must be String for pair encoding
                        looksLikePairs = false;
                        break;
                    }
                }
            }
            if (looksLikePairs) {
                java.util.Map<String, T> map = new java.util.LinkedHashMap<>();
                for (int i = 0; i < array.length; i += 2) {
                    String key = (String) array[i];
                    @SuppressWarnings("unchecked") T value = (T) array[i + 1];
                    map.put(key, value);
                }
                return ofMultiValue(map);
            }
            // Treat as single value (e.g., nested reply structure) to avoid ClassCastException
            return ofSingleValue((T) data);
        } else {
            return ofSingleValue((T) data);
        }
    }

    /** A constructor for the value with type auto-detection that never collapses uniform values. */
    @SuppressWarnings("unchecked")
    public static <T> ClusterValue<T> ofWithoutCollapse(Object data) {
        if (data instanceof Map) {
            var map = (Map<?, T>) data;
            if (map.isEmpty() || map.keySet().toArray()[0] instanceof String) {
                @SuppressWarnings("unchecked")
                var stringKeyed = (Map<String, T>) map;
                return ofMultiValueNoCollapse(stringKeyed);
            } else { // GlideString
                @SuppressWarnings("unchecked")
                var glideKeyed = (Map<GlideString, T>) map;
                return ofMultiValueBinaryNoCollapse(glideKeyed);
            }
        } else if (data instanceof Object[]) {
            // Heuristically decide whether this Object[] encodes flat key/value pairs (address,value)
            // or should be treated as a single scalar array value (e.g. CLUSTER SLOTS nested arrays).
            var array = (Object[]) data;
            boolean looksLikePairs = array.length % 2 == 0 && array.length > 0;
            if (looksLikePairs) {
                for (int i = 0; i < array.length; i += 2) {
                    if (!(array[i] instanceof String)) { // address must be String for pair encoding
                        looksLikePairs = false;
                        break;
                    }
                }
            }
            if (looksLikePairs) {
                java.util.Map<String, T> map = new java.util.LinkedHashMap<>();
                for (int i = 0; i < array.length; i += 2) {
                    String key = (String) array[i];
                    @SuppressWarnings("unchecked") T value = (T) array[i + 1];
                    map.put(key, value);
                }
                return ofMultiValueNoCollapse(map);
            }
            // Treat as single value (e.g., nested reply structure) to avoid ClassCastException
            return ofSingleValue((T) data);
        } else {
            return ofSingleValue((T) data);
        }
    }

    /** A constructor for the value. */
    public static <T> ClusterValue<T> ofSingleValue(T data) {
        var res = new ClusterValue<T>();
        res.singleValue = data;
        return res;
    }

    /** A constructor for the value. */
    public static <T> ClusterValue<T> ofMultiValue(Map<String, T> data) {
        // Re-enable uniform collapsing by default
        T uniform = tryComputeUniformValue(data);
        if (uniform != null) {
            return ofSingleValue(uniform);
        }
        var res = new ClusterValue<T>();
        res.multiValue = data;
        return res;
    }

    /** A constructor for the value that explicitly prevents uniform collapsing. */
    public static <T> ClusterValue<T> ofMultiValueNoCollapse(Map<String, T> data) {
        // Never collapse uniform values - used for commands like echo and fcall
        // that need to preserve multi-value structure even when uniform
        var res = new ClusterValue<T>();
        res.multiValue = data;
        return res;
    }

    /** A constructor for the value. */
    public static <T> ClusterValue<T> ofMultiValueBinary(Map<GlideString, T> data) {
        // First convert to String-keyed map
        Map<String,T> converted =
                data.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().getString(), Map.Entry::getValue));
        // Re-enable uniform collapsing by default
        T uniform = tryComputeUniformValue(converted);
        if (uniform != null) {
            return ofSingleValue(uniform);
        }
        var res = new ClusterValue<T>();
        res.multiValue = converted;
        return res;
    }

    /** A constructor for the value that explicitly prevents uniform collapsing (binary version). */
    public static <T> ClusterValue<T> ofMultiValueBinaryNoCollapse(Map<GlideString, T> data) {
        // First convert to String-keyed map
        Map<String,T> converted =
                data.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().getString(), Map.Entry::getValue));
        // Never collapse uniform values - used for commands like echo and fcall
        // that need to preserve multi-value structure even when uniform
        var res = new ClusterValue<T>();
        res.multiValue = converted;
        return res;
    }

    /**
     * Check that multi-value is stored in this object. Should be called prior to {@link
     * #getMultiValue()}.
     */
    public boolean hasMultiData() {
        return multiValue != null;
    }

    /**
     * Check that single-value is stored in this object. Should be called prior to {@link
     * #getSingleValue()}.
     */
    public boolean hasSingleData() {
        return !hasMultiData();
    }

    // -------- Uniform collapse helpers --------
    private static final java.util.Set<Class<?>> UNIFORM_ALLOWED = java.util.Set.of(
            String.class,
            GlideString.class,
            Long.class,
            Integer.class,
            Double.class,
            Boolean.class,
            byte[].class
    );

    /**
     * Returns the uniform value if all entries map to the same scalar allowed type; otherwise null.
     * Null (absence) values are treated as non-collapsible unless ALL values are null.
     */
    @SuppressWarnings("unchecked")
    private static <T> T tryComputeUniformValue(Map<String, T> map) {
        if (map.isEmpty()) return null;
        T first = null;
        boolean firstSet = false;
        for (Map.Entry<String, T> e : map.entrySet()) {
            T v = e.getValue();
            if (!firstSet) {
                first = v;
                firstSet = true;
                if (!isAllowedUniformValue(first)) return null; // early reject on disallowed type
            } else {
                if (!valuesEqual(first, v)) return null;
            }
        }
        // If first is null we only collapse when all values null (already ensured by equality checks)
        if (first == null) return null; // choose not to collapse all-null maps to reduce ambiguity
        return first;
    }

    private static void debugUniformCollapse(int size, Object value) {
        if (Boolean.getBoolean("glide.debugUniformCollapse")) {
            try {
                System.err.println("[glide-debug] uniform collapse size=" + size + " value=" + value);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isAllowedUniformValue(Object v) {
        if (v == null) return true; // allow progression; final decision later
        Class<?> c = v.getClass();
        if (UNIFORM_ALLOWED.contains(c)) return true;
        // Allow numeric cross-type equality (Integer vs Long) by normalizing below; accept any Number
        if (v instanceof Number) return true;
        return false;
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) {
            // Normalize numeric comparison via double (may lose very large precision but fine for typical counters)
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        if (a instanceof byte[] && b instanceof byte[]) {
            return java.util.Arrays.equals((byte[]) a, (byte[]) b);
        }
        return a.equals(b);
    }
}
