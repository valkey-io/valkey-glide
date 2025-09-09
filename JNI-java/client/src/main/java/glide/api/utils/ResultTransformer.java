package glide.api.utils;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Utility class for transforming JNI results to expected types.
 * Handles type conversion differences between JNI layer and expected Java types.
 * 
 * This addresses the issue where the JNI layer may return different types than expected
 * (e.g., Boolean instead of Long, Integer instead of Long, etc.)
 */
public final class ResultTransformer {

    private ResultTransformer() {
        // Utility class - prevent instantiation
    }

    /**
     * Transform result to Boolean, handling various input types.
     * Used for commands that should return boolean success/failure.
     */
    public static final Function<Object, Boolean> TO_BOOLEAN = result -> {
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result instanceof Number) {
            return ((Number) result).longValue() != 0L;
        }
        return false;
    };

    /**
     * Transform result to Boolean based on numeric equality to 1.
     * Used for commands like PFADD that return 1 for success, 0 for no-op.
     */
    public static final Function<Object, Boolean> TO_BOOLEAN_FROM_LONG = result -> {
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result instanceof Number) {
            return ((Number) result).longValue() == 1L;
        }
        return false;
    };

    /**
     * Transform result to Long, handling various numeric input types.
     * Used for commands that return counts, lengths, etc.
     */
    public static final Function<Object, Long> TO_LONG = result -> {
        if (result instanceof Number) {
            return ((Number) result).longValue();
        }
        return 0L;
    };

    /**
     * Transform result to Double, handling various numeric input types.
     * Used for commands that return floating point values.
     */
    public static final Function<Object, Double> TO_DOUBLE = result -> {
        if (result instanceof Number) {
            return ((Number) result).doubleValue();
        }
        return 0.0;
    };

    /**
     * Transform result to String, handling null cases.
     * Used for commands that return string values.
     */
    public static final Function<Object, String> TO_STRING = result -> {
        if (result == null) {
            return null;
        }
        return result.toString();
    };

    /**
     * Transform result to typed array, with safe casting.
     * Used for commands that return arrays.
     */
    @SuppressWarnings("unchecked")
    public static <T> Function<Object, T[]> toTypedArray(Class<T[]> arrayClass) {
        return result -> {
            try {
                return arrayClass.cast(result);
            } catch (ClassCastException e) {
                // Return empty array of correct type on cast failure
                return (T[]) java.lang.reflect.Array.newInstance(
                    arrayClass.getComponentType(), 0);
            }
        };
    }

    /**
     * Transform result to typed Map, with safe casting.
     * Used for commands that return maps.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Function<Object, Map<K, V>> toTypedMap(Class<Map<K, V>> mapClass) {
        return result -> {
            try {
                return mapClass.cast(result);
            } catch (ClassCastException e) {
                return new java.util.HashMap<>();
            }
        };
    }

    /**
     * Transform result to typed Set, with safe casting.
     * Used for commands that return sets.
     */
    @SuppressWarnings("unchecked")
    public static <T> Function<Object, Set<T>> toTypedSet(Class<Set<T>> setClass) {
        return result -> {
            try {
                return setClass.cast(result);
            } catch (ClassCastException e) {
                return new java.util.HashSet<>();
            }
        };
    }

    // Convenience method for common Map types
    @SuppressWarnings("unchecked")
    public static final Function<Object, Map<String, Object>> TO_STRING_OBJECT_MAP = result -> {
        try {
            return (Map<String, Object>) result;
        } catch (ClassCastException e) {
            return new java.util.HashMap<>();
        }
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Map<String, String>> TO_STRING_STRING_MAP = result -> {
        try {
            return (Map<String, String>) result;
        } catch (ClassCastException e) {
            return new java.util.HashMap<>();
        }
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Map<String, Long>> TO_STRING_LONG_MAP = result -> {
        try {
            return (Map<String, Long>) result;
        } catch (ClassCastException e) {
            return new java.util.HashMap<>();
        }
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Map<String, Double>> TO_STRING_DOUBLE_MAP = result -> {
        try {
            return (Map<String, Double>) result;
        } catch (ClassCastException e) {
            return new java.util.HashMap<>();
        }
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Set<String>> TO_STRING_SET = result -> {
        try {
            Set<String> set = (Set<String>) result;
            return Set.copyOf(set);
        } catch (ClassCastException e) {
            return Set.of();
        }
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, String[]> TO_STRING_ARRAY = result -> {
        try {
            return (String[]) result;
        } catch (ClassCastException e) {
            return new String[0];
        }
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Object[]> TO_OBJECT_ARRAY = result -> {
        try {
            return (Object[]) result;
        } catch (ClassCastException e) {
            return new Object[0];
        }
    };

    // GlideString-specific transformations
    @SuppressWarnings("unchecked")
    public static final Function<Object, glide.api.models.GlideString> TO_GLIDE_STRING = result -> {
        if (result == null) {
            return null;
        }
        if (result instanceof glide.api.models.GlideString) {
            return (glide.api.models.GlideString) result;
        }
        // Convert from String or byte array to GlideString
        if (result instanceof String) {
            return glide.api.models.GlideString.of((String) result);
        }
        if (result instanceof byte[]) {
            return glide.api.models.GlideString.of((byte[]) result);
        }
        return glide.api.models.GlideString.of(result);
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Map<glide.api.models.GlideString, Object>> TO_GLIDESTRING_OBJECT_MAP = result -> {
        try {
            return (Map<glide.api.models.GlideString, Object>) result;
        } catch (ClassCastException e) {
            return new java.util.HashMap<>();
        }
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Map<glide.api.models.GlideString, glide.api.models.GlideString[][]>> TO_GLIDESTRING_ARRAY2D_MAP = result -> {
        try {
            return (Map<glide.api.models.GlideString, glide.api.models.GlideString[][]>) result;
        } catch (ClassCastException e) {
            return new java.util.HashMap<>();
        }
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Map<String, String[][]>> TO_STRING_ARRAY2D_MAP = result -> {
        try {
            return (Map<String, String[][]>) result;
        } catch (ClassCastException e) {
            return new java.util.HashMap<>();
        }
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Set<glide.api.models.GlideString>> TO_GLIDESTRING_SET = result -> {
        try {
            Set<glide.api.models.GlideString> set = (Set<glide.api.models.GlideString>) result;
            return Set.copyOf(set);
        } catch (ClassCastException e) {
            return Set.of();
        }
    };
}