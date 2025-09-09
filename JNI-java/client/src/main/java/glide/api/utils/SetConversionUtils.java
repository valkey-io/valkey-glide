package glide.api.utils;

import glide.api.models.GlideString;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for converting JNI command results to Java Sets.
 * 
 * This addresses the common pattern where JNI layer returns results as:
 * - Object[] (most common)
 * - Collection<?> (in some configurations)
 * - String (single element edge case)
 * - null (empty result)
 */
public final class SetConversionUtils {

    private SetConversionUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts a JNI command result to a Set of Strings.
     * Handles Object[], Collection<?>, String, and null inputs.
     * 
     * @param result the result from JNI executeCommand
     * @return an immutable Set of Strings, never null
     */
    public static Set<String> convertToStringSet(Object result) {
        if (result == null) return Set.of();
        
        Set<String> set = new HashSet<>();
        if (result instanceof Object[]) {
            Object[] objects = (Object[]) result;
            for (Object obj : objects) {
                if (obj != null) set.add(obj.toString());
            }
        } else if (result instanceof Collection) {
            for (Object obj : (Collection<?>) result) {
                if (obj != null) set.add(obj.toString());
            }
        } else if (result instanceof String) { // single element edge case
            set.add(result.toString());
        }
        return Set.copyOf(set);
    }

    /**
     * Converts a JNI command result to a Set of GlideStrings.
     * Handles Object[], Collection<?>, String, and null inputs.
     * 
     * @param result the result from JNI executeCommand
     * @return an immutable Set of GlideStrings, never null
     */
    public static Set<GlideString> convertToGlideStringSet(Object result) {
        if (result == null) return Set.of();
        
        Set<GlideString> set = new HashSet<>();
        if (result instanceof Object[]) {
            Object[] objects = (Object[]) result;
            for (Object obj : objects) {
                if (obj != null) set.add(GlideString.of(obj));
            }
        } else if (result instanceof Collection) {
            for (Object obj : (Collection<?>) result) {
                if (obj != null) set.add(GlideString.of(obj));
            }
        } else if (result instanceof String) { // single element edge case
            set.add(GlideString.of(result));
        }
        return Set.copyOf(set);
    }

    /**
     * Converts a JNI command result to a Boolean.
     * Handles Boolean, Number, String, and null inputs.
     * 
     * @param result the result from JNI executeCommand
     * @return Boolean value based on the result
     */
    public static Boolean convertToBoolean(Object result) {
        if (result instanceof Boolean) return (Boolean) result;
        if (result instanceof Number) return ((Number) result).longValue() != 0L;
        return "1".equals(result.toString());
    }
}