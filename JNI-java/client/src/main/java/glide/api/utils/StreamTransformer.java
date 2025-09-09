package glide.api.utils;

import glide.api.models.GlideString;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for transforming stream-related responses from JNI layer.
 * Handles type conversion issues where JNI returns Object[] instead of properly typed arrays.
 */
public final class StreamTransformer {
    
    private StreamTransformer() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Transform XRANGE/XREVRANGE result to Map<String, String[][]>.
     * Handles case where JNI returns Map<String, Object[]> instead of Map<String, String[][]>.
     */
    public static final Function<Object, Map<String, String[][]>> TO_STRING_STREAM_ENTRIES = result -> {
        if (result == null) return null;
        
        Map<String, String[][]> converted = new HashMap<>();
        Map<String, Object> rawMap = (Map<String, Object>) result;
        
        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String[][]) {
                // Already correct type
                converted.put(entry.getKey(), (String[][]) value);
            } else if (value instanceof Object[]) {
                // Convert Object[] to String[][]
                converted.put(entry.getKey(), convertObjectArrayToStringArray((Object[]) value));
            }
        }
        return converted;
    };
    
    /**
     * Transform XRANGE/XREVRANGE result to Map<GlideString, GlideString[][]>.
     * Handles case where JNI returns Map with String keys or Object[] values.
     */
    public static final Function<Object, Map<GlideString, GlideString[][]>> TO_GLIDESTRING_STREAM_ENTRIES = result -> {
        if (result == null) return null;
        
        Map<GlideString, GlideString[][]> converted = new HashMap<>();
        Map<?, Object> rawMap = (Map<?, Object>) result;
        
        for (Map.Entry<?, Object> entry : rawMap.entrySet()) {
            // Handle key conversion - JNI might return String instead of GlideString
            GlideString key;
            if (entry.getKey() instanceof GlideString) {
                key = (GlideString) entry.getKey();
            } else if (entry.getKey() instanceof String) {
                key = GlideString.of((String) entry.getKey());
            } else {
                key = GlideString.of(entry.getKey());
            }
            
            Object value = entry.getValue();
            if (value instanceof GlideString[][]) {
                // Already correct type
                converted.put(key, (GlideString[][]) value);
            } else if (value instanceof Object[]) {
                // Convert Object[] to GlideString[][]
                converted.put(key, convertObjectArrayToGlideStringArray((Object[]) value));
            }
        }
        return converted;
    };
    
    /**
     * Convert Object[] to String[][] for stream entries.
     * Handles nested arrays where each entry is a field-value pair.
     */
    private static String[][] convertObjectArrayToStringArray(Object[] objArray) {
        String[][] strArray = new String[objArray.length][];
        for (int i = 0; i < objArray.length; i++) {
            if (objArray[i] instanceof String[]) {
                strArray[i] = (String[]) objArray[i];
            } else if (objArray[i] instanceof Object[]) {
                Object[] innerObj = (Object[]) objArray[i];
                String[] innerStr = new String[innerObj.length];
                for (int j = 0; j < innerObj.length; j++) {
                    innerStr[j] = innerObj[j] != null ? innerObj[j].toString() : null;
                }
                strArray[i] = innerStr;
            }
        }
        return strArray;
    }
    
    /**
     * Convert Object[] to GlideString[][] for stream entries.
     * Handles nested arrays where each entry is a field-value pair.
     */
    private static GlideString[][] convertObjectArrayToGlideStringArray(Object[] objArray) {
        GlideString[][] gsArray = new GlideString[objArray.length][];
        for (int i = 0; i < objArray.length; i++) {
            if (objArray[i] instanceof GlideString[]) {
                gsArray[i] = (GlideString[]) objArray[i];
            } else if (objArray[i] instanceof Object[]) {
                Object[] innerObj = (Object[]) objArray[i];
                GlideString[] innerGs = new GlideString[innerObj.length];
                for (int j = 0; j < innerObj.length; j++) {
                    if (innerObj[j] instanceof GlideString) {
                        innerGs[j] = (GlideString) innerObj[j];
                    } else if (innerObj[j] != null) {
                        innerGs[j] = GlideString.of(innerObj[j]);
                    } else {
                        innerGs[j] = null;
                    }
                }
                gsArray[i] = innerGs;
            }
        }
        return gsArray;
    }
}