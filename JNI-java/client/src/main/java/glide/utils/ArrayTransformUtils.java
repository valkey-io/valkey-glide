/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import static glide.api.models.GlideString.gs;

import glide.api.commands.GeospatialIndicesBaseCommands;
import glide.api.models.GlideString;
import glide.api.models.commands.geospatial.GeospatialData;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.stream.Stream;

/** Utility methods for data conversion. */
public class ArrayTransformUtils {

    /**
     * Converts a flat array or nested array of member-score pairs to a consistent 2D array format.
     * Handles both standalone mode (nested arrays) and cluster mode (flat arrays).
     *
     * @param result The result from ZRANDMEMBER or similar commands with WITHSCORES
     * @return A 2D array where each sub-array contains [member, score]
     */
    public static Object[][] convertMemberScorePairs(Object result) {
        if (!(result instanceof Object[])) {
            return new Object[0][0];
        }
        
        Object[] objects = (Object[]) result;
        if (objects.length == 0) {
            return new Object[0][0];
        }
        
        // Check if the result is already in pairs format (standalone mode)
        if (objects[0] instanceof Object[]) {
            // Already in pairs format - just reconstruct
            Object[][] pairs = new Object[objects.length][2];
            for (int i = 0; i < objects.length; i++) {
                Object[] pair = (Object[]) objects[i];
                pairs[i][0] = pair[0];
                pairs[i][1] = pair[1];
            }
            return pairs;
        } else {
            // Flat format - need to create pairs
            Object[][] pairs = new Object[objects.length / 2][2];
            for (int i = 0; i < objects.length; i += 2) {
                pairs[i / 2][0] = objects[i];
                pairs[i / 2][1] = objects[i + 1];
            }
            return pairs;
        }
    }
    
    /**
     * Converts a flat array or nested array of member-score pairs to a consistent 2D array format,
     * with GlideString conversion for members.
     *
     * @param result The result from ZRANDMEMBER or similar commands with WITHSCORES (binary)
     * @return A 2D array where each sub-array contains [GlideString member, score]
     */
    public static Object[][] convertMemberScorePairsBinary(Object result) {
        if (!(result instanceof Object[])) {
            return new Object[0][0];
        }
        
        Object[] objects = (Object[]) result;
        if (objects.length == 0) {
            return new Object[0][0];
        }
        
        // Check if the result is already in pairs format (standalone mode)
        if (objects[0] instanceof Object[]) {
            // Already in pairs format - just reconstruct with GlideString conversion
            Object[][] pairs = new Object[objects.length][2];
            for (int i = 0; i < objects.length; i++) {
                Object[] pair = (Object[]) objects[i];
                pairs[i][0] = GlideString.of(pair[0]);
                pairs[i][1] = pair[1];
            }
            return pairs;
        } else {
            // Flat format - need to create pairs with GlideString conversion
            Object[][] pairs = new Object[objects.length / 2][2];
            for (int i = 0; i < objects.length; i += 2) {
                pairs[i / 2][0] = GlideString.of(objects[i]);
                pairs[i / 2][1] = objects[i + 1];
            }
            return pairs;
        }
    }

    /**
     * Converts SMISMEMBER response to Boolean array.
     * SMISMEMBER returns array of integers (1 or 0) that need to be converted to Boolean values.
     * 
     * @param result The raw result from SMISMEMBER command
     * @return Boolean array where true means member exists in set
     */
    public static Boolean[] convertSmismemberResponse(Object result) {
        if (result instanceof Object[]) {
            Object[] objects = (Object[]) result;
            Boolean[] results = new Boolean[objects.length];
            for (int i = 0; i < objects.length; i++) {
                // Check for both integer and string representations
                Object obj = objects[i];
                if (obj instanceof Number) {
                    results[i] = ((Number) obj).intValue() == 1;
                } else if (obj instanceof Boolean) {
                    results[i] = (Boolean) obj;
                } else if (obj instanceof String) {
                    results[i] = "1".equals(obj) || "true".equalsIgnoreCase((String)obj);
                } else {
                    results[i] = "1".equals(obj.toString());
                }
            }
            return results;
        }
        return new Boolean[0];
    }

    /**
     * Converts a map of string keys and values of any type that can be converted in to an array of
     * strings with alternating keys and values.
     *
     * @param args Map of string keys to values of any type to convert.
     * @return Array of strings [key1, value1.toString(), key2, value2.toString(), ...].
     */
    public static String[] convertMapToKeyValueStringArray(Map<String, ?> args) {
        return args.entrySet().stream()
                .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
                .toArray(String[]::new);
    }

    /**
     * Converts a map of GlideString keys and values to an array of GlideStrings.
     *
     * @param args Map of GlideString keys to values of GlideString.
     * @return Array of strings [key1, gs(value1.toString()), key2, gs(value2.toString()), ...].
     */
    public static GlideString[] convertMapToKeyValueGlideStringArray(
            Map<GlideString, GlideString> args) {
        return args.entrySet().stream()
                .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
                .toArray(GlideString[]::new);
    }

    /**
     * Converts a nested array of string keys and values of any type that can be converted in to an
     * array of strings with alternating keys and values.
     *
     * @param args Nested array of string keys to values of any type to convert.
     * @return Array of strings [key1, value1.toString(), key2, value2.toString(), ...].
     */
    public static String[] convertNestedArrayToKeyValueStringArray(String[][] args) {
        for (String[] entry : args) {
            if (entry.length != 2) {
                throw new IllegalArgumentException(
                        "Array entry had the wrong length. Expected length 2 but got length " + entry.length);
            }
        }
        return Arrays.stream(args)
                .flatMap(entry -> Stream.of(entry[0], entry[1]))
                .toArray(String[]::new);
    }

    /**
     * Converts a nested array of GlideString keys and values in to an array of GlideStrings with
     * alternating keys and values.
     *
     * @param args Nested array of GlideString keys and values to convert.
     * @return Array of strings [key1, gs(value1.toString()), key2, gs(value2.toString()), ...].
     */
    public static GlideString[] convertNestedArrayToKeyValueGlideStringArray(GlideString[][] args) {
        for (GlideString[] entry : args) {
            if (entry.length != 2) {
                throw new IllegalArgumentException(
                        "Array entry had the wrong length. Expected length 2 but got length " + entry.length);
            }
        }
        return Arrays.stream(args)
                .flatMap(entry -> Stream.of(entry[0], entry[1]))
                .toArray(GlideString[]::new);
    }

    /**
     * Converts a map of string keys and values of any type into an array of strings with alternating
     * values and keys.
     *
     * @param args Map of string keys to values of Double type to convert.
     * @return Array of strings [value1.toString(), key1, value2.toString(), key2, ...].
     */
    public static String[] convertMapToValueKeyStringArray(Map<String, Double> args) {
        return args.entrySet().stream()
                .flatMap(entry -> Stream.of(entry.getValue().toString(), entry.getKey()))
                .toArray(String[]::new);
    }

    /**
     * Converts a map of GlideString keys and values of any type into an array of GlideStrings with
     * alternating values and keys.
     *
     * @param args Map of GlideString keys to values of Double type to convert.
     * @return Array of GlideStrings [gs(value1.toString()), key1, gs(value2.toString()), key2, ...].
     */
    public static GlideString[] convertMapToValueKeyStringArrayBinary(Map<GlideString, Double> args) {
        return args.entrySet().stream()
                .flatMap(entry -> Stream.of(gs(entry.getValue().toString()), entry.getKey()))
                .toArray(GlideString[]::new);
    }

    /**
     * Converts a geospatial members to geospatial data mapping in to an array of arguments in the
     * form of [Longitude, Latitude, Member ...].
     *
     * @param args A mapping of member names to their corresponding positions.
     * @return An array of strings to be used in {@link GeospatialIndicesBaseCommands#geoadd}.
     */
    public static String[] mapGeoDataToArray(Map<String, GeospatialData> args) {
        return args.entrySet().stream()
                .flatMap(
                        entry ->
                                Stream.of(
                                        Double.toString(entry.getValue().getLongitude()),
                                        Double.toString(entry.getValue().getLatitude()),
                                        entry.getKey()))
                .toArray(String[]::new);
    }

    /**
     * Converts a geospatial members to geospatial data mapping in to an array of arguments in the
     * form of [Longitude, Latitude, Member ...].
     *
     * @param args A mapping of member names to their corresponding positions.
     * @return An array of GlideStrings to be used in {@link GeospatialIndicesBaseCommands#geoadd}.
     */
    public static <ArgType> GlideString[] mapGeoDataToGlideStringArray(
            Map<ArgType, GeospatialData> args) {
        return args.entrySet().stream()
                .flatMap(
                        entry ->
                                Stream.of(
                                        GlideString.of(Double.toString(entry.getValue().getLongitude())),
                                        GlideString.of(Double.toString(entry.getValue().getLatitude())),
                                        GlideString.of(entry.getKey())))
                .toArray(GlideString[]::new);
    }

    /**
     * Casts an array of objects to an array of type T.
     *
     * @param objectArr Array of objects to cast.
     * @param clazz The class of the array elements to cast to.
     * @return An array of type U, containing the elements from the input array.
     * @param <T> The base type from which the elements are being cast.
     * @param <U> The subtype of T to which the elements are cast.
     */
    @SuppressWarnings("unchecked")
    public static <T, U extends T> U[] castArray(T[] objectArr, Class<U> clazz) {
        if (objectArr == null) {
            return null;
        }
        return Arrays.stream(objectArr)
                .map(clazz::cast)
                .toArray(size -> (U[]) Array.newInstance(clazz, size));
    }

    /**
     * Casts an <code>Object[][]</code> to <code>T[][]</code> by casting each nested array and every
     * array element.
     *
     * @param outerObjectArr Array of arrays of objects to cast.
     * @param clazz The class of the array elements to cast to.
     * @return An array of arrays of type U, containing the elements from the input array.
     * @param <T> The base type from which the elements are being cast.
     * @param <U> The subtype of T to which the elements are cast.
     */
    @SuppressWarnings("unchecked")
    public static <T, U extends T> U[][] castArrayofArrays(T[] outerObjectArr, Class<U> clazz) {
        if (outerObjectArr == null) {
            return null;
        }
        T[] convertedArr = (T[]) new Object[outerObjectArr.length];
        for (int i = 0; i < outerObjectArr.length; i++) {
            convertedArr[i] = (T) castArray((T[]) outerObjectArr[i], clazz);
        }
        return (U[][]) castArray(convertedArr, Array.newInstance(clazz, 0).getClass());
    }

    /**
     * Processes a two-element array where the first element is used as a key and the second element
     * is a Map where its values are cast to type <code>U</code>.
     *
     * @param outerObjectArr A two-element array with array[0] as the key, array[1] as the value/map.
     * @param clazz The class to which values should be cast.
     * @return A Map with a single entry with the first element as the key, and the second element is
     *     the value of type Map<String, U>
     * @param <U> The type to which the elements are cast.
     */
    public static <U> Map<String, Object> convertKeyValueArrayToMap(
            Object[] outerObjectArr, Class<U> clazz) {
        if (outerObjectArr == null) {
            return null;
        }

        String key = outerObjectArr[0].toString();
        Map<?, ?> values = (Map<?, ?>) outerObjectArr[1];
        Map<String, U> innerMap = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String subKey = entry.getKey().toString();
            U score = clazz.cast(entry.getValue());
            innerMap.put(subKey, score);
        }
        return Map.of(key, innerMap);
    }

    /**
     * Processes a two-element array where the first element is used as a key and the second element
     * is a Map where its values are cast to type <code>U</code>.
     *
     * @param outerObjectArr A two-element array with array[0] as the key, array[1] as the value/map.
     * @param clazz The class to which values should be cast.
     * @return A Map with a single entry with the first element as the key, and the second element is
     *     the value of type Map<GlideString, U>
     * @param <U> The type to which the elements are cast.
     */
    public static <U> Map<GlideString, Object> convertBinaryStringKeyValueArrayToMap(
            Object[] outerObjectArr, Class<U> clazz) {
        if (outerObjectArr == null) {
            return null;
        }

        GlideString key = gs(outerObjectArr[0].toString());
        Map<?, ?> values = (Map<?, ?>) outerObjectArr[1];
        Map<GlideString, U> innerMap = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : values.entrySet()) {
            GlideString subKey = gs(entry.getKey().toString());
            U score = clazz.cast(entry.getValue());
            innerMap.put(subKey, score);
        }
        return Map.of(key, innerMap);
    }

    /**
     * Casts an <code>Object[][][]</code> to <code>T[][][]</code> by casting each nested array and
     * every array element.
     *
     * @param outerObjectArr 3D array of objects to cast.
     * @param clazz The class of the array elements to cast to.
     * @return An array of arrays of type U, containing the elements from the input array.
     * @param <T> The base type from which the elements are being cast.
     * @param <U> The subtype of T to which the elements are cast.
     */
    public static <T, U extends T> U[][][] cast3DArray(T[] outerObjectArr, Class<U> clazz) {
        if (outerObjectArr == null) {
            return null;
        }
        T[] convertedArr = (T[]) new Object[outerObjectArr.length];
        for (int i = 0; i < outerObjectArr.length; i++) {
            convertedArr[i] = (T) castArrayofArrays((T[]) outerObjectArr[i], clazz);
        }
        return (U[][][]) castArrayofArrays(convertedArr, Array.newInstance(clazz, 0).getClass());
    }

    /**
     * Maps a Map of Arrays with value type T[] to value of U[].
     *
     * @param mapOfArrays Map of Array values to cast.
     * @param clazz The class of the array values to cast to.
     * @return A Map of arrays of type U[], containing the key/values from the input Map.
     * @param <T> The target type which the elements are cast.
     */
    public static <T> Map<String, T[]> castMapOfArrays(
            Map<String, Object[]> mapOfArrays, Class<T> clazz) {
        if (mapOfArrays == null) {
            return null;
        }
        return mapOfArrays.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> castArray(e.getValue(), clazz)));
    }

    /**
     * Maps a Map of Arrays with value type T[] to value of U[].
     *
     * @param mapOfArrays Map of Array values to cast.
     * @param clazz The class of the array values to cast to.
     * @return A Map of arrays of type U[], containing the key/values from the input Map.
     * @param <T> The target type which the elements are cast.
     */
    public static <T> Map<GlideString, T[]> castBinaryStringMapOfArrays(
            Map<GlideString, Object[]> mapOfArrays, Class<T> clazz) {
        if (mapOfArrays == null) {
            return null;
        }
        return mapOfArrays.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> castArray(e.getValue(), clazz)));
    }

    /**
     * Maps a Map of Object[][] with value type T[][] to value of U[][].
     *
     * @param mapOfArrays Map of 2D Array values to cast.
     * @param clazz The class of the array values to cast to.
     * @return A Map of arrays of type U[][], containing the key/values from the input Map.
     * @param <T> The target type which the elements are cast.
     * @param <S> String type, could be either {@link String} or {@link GlideString}.
     */
    public static <S, T> Map<S, T[][]> castMapOf2DArray(
            Map<S, Object[][]> mapOfArrays, Class<T> clazz) {
        if (mapOfArrays == null) {
            return null;
        }
        return mapOfArrays.entrySet().stream()
                .collect(
                        LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), castArrayofArrays(e.getValue(), clazz)),
                        LinkedHashMap::putAll);
    }

    /**
     * Concatenates multiple arrays of type T and returns a single concatenated array.
     *
     * @param arrays Varargs parameter for arrays to be concatenated.
     * @param <T> The type of the elements in the arrays.
     * @return A concatenated array of type T.
     */
    @SafeVarargs
    public static <T> T[] concatenateArrays(T[]... arrays) {
        return Stream.of(arrays).flatMap(Stream::of).toArray(size -> Arrays.copyOf(arrays[0], size));
    }

    /**
     * Converts a map of any type of keys and values in to an array of GlideString with alternating
     * keys and values.
     *
     * @param args Map of keys to values of any type to convert.
     * @return Array of GlideString [key1, value1, key2, value2, ...].
     */
    public static GlideString[] flattenMapToGlideStringArray(Map<?, ?> args) {
        return args.entrySet().stream()
                .flatMap(
                        entry -> Stream.of(GlideString.of(entry.getKey()), GlideString.of(entry.getValue())))
                .toArray(GlideString[]::new);
    }

    /**
     * Converts a nested array of any type of keys and values in to an array of GlideString with
     * alternating keys and values.
     *
     * @param args Nested array of keys to values of any type to convert.
     * @return Array of GlideString [key1, value1, key2, value2, ...].
     */
    public static <T> GlideString[] flattenNestedArrayToGlideStringArray(T[][] args) {
        for (T[] entry : args) {
            if (entry.length != 2) {
                throw new IllegalArgumentException(
                        "Array entry had the wrong length. Expected length 2 but got length " + entry.length);
            }
        }
        return Arrays.stream(args)
                .flatMap(entry -> Stream.of(GlideString.of(entry[0]), GlideString.of(entry[1])))
                .toArray(GlideString[]::new);
    }

    /**
     * Converts a map of any type of keys and values in to an array of GlideString with alternating
     * values and keys.
     *
     * <p>This method is similar to flattenMapToGlideStringArray, but it places the value before the
     * key
     *
     * @param args Map of keys to values of any type to convert.
     * @return Array of GlideString [value1, key1, value2, key2...].
     */
    public static GlideString[] flattenMapToGlideStringArrayValueFirst(Map<?, ?> args) {
        return args.entrySet().stream()
                .flatMap(
                        entry -> Stream.of(GlideString.of(entry.getValue()), GlideString.of(entry.getKey())))
                .toArray(GlideString[]::new);
    }

    /**
     * Converts a map of any type of keys and values in to an array of GlideString where all keys are
     * placed first, followed by the values.
     *
     * @param args Map of keys to values of any type to convert.
     * @return Array of GlideString [key1, key2, value1, value2...].
     */
    public static GlideString[] flattenAllKeysFollowedByAllValues(Map<?, ?> args) {
        List<GlideString> keysList = new ArrayList<>();
        List<GlideString> valuesList = new ArrayList<>();

        for (var entry : args.entrySet()) {
            keysList.add(GlideString.of(entry.getKey()));
            valuesList.add(GlideString.of(entry.getValue()));
        }

        return concatenateArrays(
                keysList.toArray(GlideString[]::new), valuesList.toArray(GlideString[]::new));
    }

    /**
     * Converts any array into GlideString array keys and values.
     *
     * @param args Map of keys to values of any type to convert.
     * @return Array of strings [key1, value1, key2, value2, ...].
     */
    public static <ArgType> GlideString[] convertToGlideStringArray(ArgType[] args) {
        return Arrays.stream(args).map(GlideString::of).toArray(GlideString[]::new);
    }

    /**
     * Given an inputMap of any key / value pairs, create a new Map of <GlideString, GlideString>
     *
     * @param inputMap Map of values to convert.
     * @return A Map of <GlideString, GlideString>
     */
    public static Map<GlideString, GlideString> convertMapToGlideStringMap(Map<?, ?> inputMap) {
        if (inputMap == null) {
            return null;
        }
        return inputMap.entrySet().stream()
                .collect(
                        LinkedHashMap::new,
                        (m, e) -> m.put(GlideString.of(e.getKey()), GlideString.of(e.getValue())),
                        LinkedHashMap::putAll);
    }

    /**
     * Converts an Object array to a String array. This is used when the JNI layer
     * returns
     * Object[] but the Java code expects String[].
     *
     * @param result The result object from JNI, expected to be either String[] or
     *               Object[]
     * @return A String array
     */
    public static String[] toStringArray(Object result) {
        if (result instanceof String[]) {
            return (String[]) result;
        } else if (result instanceof Object[]) {
            Object[] objArray = (Object[]) result;
            
            // Check for cluster wrapping pattern: single-element array containing the actual array
            if (objArray.length == 1 && objArray[0] != null) {
                Object firstElement = objArray[0];
                if (firstElement instanceof String[]) {
                    // Unwrap the string array
                    return (String[]) firstElement;
                } else if (firstElement instanceof Object[]) {
                    // Recursively handle nested array
                    return toStringArray(firstElement);
                }
            }
            
            // Not cluster wrapping - convert each element to string
            String[] strArray = new String[objArray.length];
            for (int i = 0; i < objArray.length; i++) {
                strArray[i] = objArray[i] == null ? null : objArray[i].toString();
            }
            return strArray;
        }
        throw new IllegalArgumentException("Expected String[] or Object[] but got: " +
                (result == null ? "null" : result.getClass().getName()));
    }

    /**
     * Converts an Object array to a GlideString array. This is used when the JNI
     * layer returns
     * Object[] but the Java code expects GlideString[].
     *
     * @param result The result object from JNI, expected to be either String[] or
     *               Object[]
     * @return A GlideString array
     */
    public static GlideString[] toGlideStringArray(Object result) {
        if (result instanceof GlideString[]) {
            return (GlideString[]) result;
        } else if (result instanceof Object[]) {
            Object[] objArray = (Object[]) result;
            
            // Check for cluster wrapping pattern: single-element array containing the actual array
            if (objArray.length == 1 && objArray[0] != null) {
                Object firstElement = objArray[0];
                if (firstElement instanceof GlideString[]) {
                    // Unwrap the GlideString array
                    return (GlideString[]) firstElement;
                } else if (firstElement instanceof String[]) {
                    // Unwrap and convert String array to GlideString array
                    String[] strArray = (String[]) firstElement;
                    return Arrays.stream(strArray)
                            .map(s -> s == null ? null : GlideString.of(s))
                            .toArray(GlideString[]::new);
                } else if (firstElement instanceof Object[]) {
                    // Recursively handle nested array
                    return toGlideStringArray(firstElement);
                }
            }
            
            // Not cluster wrapping - convert to GlideString array
            GlideString[] gsArray = new GlideString[objArray.length];
            for (int i = 0; i < objArray.length; i++) {
                gsArray[i] = objArray[i] == null ? null : GlideString.of(objArray[i]);
            }
            return gsArray;
        } else if (result instanceof String[]) {
            String[] strArray = (String[]) result;
            return Arrays.stream(strArray)
                    .map(s -> s == null ? null : GlideString.of(s))
                    .toArray(GlideString[]::new);
        }
        throw new IllegalArgumentException("Expected String[], GlideString[] or Object[] but got: " +
                (result == null ? "null" : result.getClass().getName()));
    }

    /**
     * Converts an Object array to a Long array. This is used when the JNI layer
     * returns
     * Object[] but the Java code expects Long[].
     *
     * @param result The result object from JNI
     * @return A Long array
     */
    public static Long[] toLongArray(Object result) {
        if (result instanceof Long[]) {
            return (Long[]) result;
        } else if (result instanceof Object[]) {
            Object[] objArray = (Object[]) result;
            Long[] longArray = new Long[objArray.length];
            for (int i = 0; i < objArray.length; i++) {
                if (objArray[i] != null) {
                    longArray[i] = ((Number) objArray[i]).longValue();
                }
            }
            return longArray;
        }
        throw new IllegalArgumentException("Expected Long[] or Object[] but got: " +
                (result == null ? "null" : result.getClass().getName()));
    }

    /**
     * Converts an Object array to a Double array. This is used when the JNI layer
     * returns
     * Object[] but the Java code expects Double[].
     *
     * @param result The result object from JNI
     * @return A Double array
     */
    public static Double[] toDoubleArray(Object result) {
        if (result instanceof Double[]) {
            return (Double[]) result;
        } else if (result instanceof Object[]) {
            Object[] objArray = (Object[]) result;
            Double[] doubleArray = new Double[objArray.length];
            for (int i = 0; i < objArray.length; i++) {
                if (objArray[i] != null) {
                    doubleArray[i] = ((Number) objArray[i]).doubleValue();
                }
            }
            return doubleArray;
        }
        throw new IllegalArgumentException("Expected Double[] or Object[] but got: " +
                (result == null ? "null" : result.getClass().getName()));
    }

    /**
     * Converts an Object array to a Boolean array. This is used when the JNI layer
     * returns
     * Object[] but the Java code expects Boolean[].
     *
     * @param result The result object from JNI
     * @return A Boolean array
     */
    public static Boolean[] toBooleanArray(Object result) {
        if (result instanceof Boolean[]) {
            return (Boolean[]) result;
        } else if (result instanceof Object[]) {
            Object[] objArray = (Object[]) result;
            Boolean[] boolArray = new Boolean[objArray.length];
            for (int i = 0; i < objArray.length; i++) {
                boolArray[i] = (Boolean) objArray[i];
            }
            return boolArray;
        }
        throw new IllegalArgumentException("Expected Boolean[] or Object[] but got: " +
                (result == null ? "null" : result.getClass().getName()));
    }

    /**
     * Converts an Object array to a Double 2D array. This is used when the JNI
     * layer
     * returns Object[] but the Java code expects Double[][].
     *
     * @param result The result object from JNI
     * @return A Double 2D array
     */
    public static Double[][] toDouble2DArray(Object result) {
        if (result instanceof Double[][]) {
            return (Double[][]) result;
        } else if (result instanceof Object[]) {
            Object[] objArray = (Object[]) result;
            Double[][] doubleArray = new Double[objArray.length][];
            for (int i = 0; i < objArray.length; i++) {
                if (objArray[i] == null) {
                    doubleArray[i] = null;
                } else if (objArray[i] instanceof Object[]) {
                    Object[] innerArray = (Object[]) objArray[i];
                    doubleArray[i] = new Double[innerArray.length];
                    for (int j = 0; j < innerArray.length; j++) {
                        if (innerArray[j] != null) {
                            doubleArray[i][j] = ((Number) innerArray[j]).doubleValue();
                        }
                    }
                }
            }
            return doubleArray;
        }
        throw new IllegalArgumentException("Expected Double[][] or Object[] but got: " +
                (result == null ? "null" : result.getClass().getName()));
    }

    /**
     * Converts an Object array to a Map<String, Object> array. This is used when
     * the JNI layer
     * returns Object[] but the Java code expects Map<String, Object>[].
     *
     * @param result The result object from JNI
     * @return A Map<String, Object> array
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object>[] toMapStringObjectArray(Object result) {
        if (result instanceof Map[]) {
            return (Map<String, Object>[]) result;
        } else if (result instanceof Object[]) {
            Object[] objArray = (Object[]) result;
            Map<String, Object>[] mapArray = new Map[objArray.length];
            for (int i = 0; i < objArray.length; i++) {
                mapArray[i] = (Map<String, Object>) objArray[i];
            }
            return mapArray;
        }
        throw new IllegalArgumentException("Expected Map[] or Object[] but got: " +
                (result == null ? "null" : result.getClass().getName()));
    }

    /**
     * Converts an Object array to a Map<GlideString, Object> array. This is used
     * when the JNI layer
     * returns Object[] but the Java code expects Map<GlideString, Object>[].
     *
     * @param result The result object from JNI
     * @return A Map<GlideString, Object> array
     */
    @SuppressWarnings("unchecked")
    public static Map<GlideString, Object>[] toMapGlideStringObjectArray(Object result) {
        if (result instanceof Map[]) {
            return (Map<GlideString, Object>[]) result;
        } else if (result instanceof Object[]) {
            Object[] objArray = (Object[]) result;
            Map<GlideString, Object>[] mapArray = new Map[objArray.length];
            for (int i = 0; i < objArray.length; i++) {
                mapArray[i] = (Map<GlideString, Object>) objArray[i];
            }
            return mapArray;
        }
        throw new IllegalArgumentException("Expected Map[] or Object[] but got: " +
                (result == null ? "null" : result.getClass().getName()));
    }

    // ==== Deep conversion helpers for FUNCTION LIST binary variant ====
    /**
     * Deeply converts a FUNCTION LIST response (Map<String,Object>[]) into a
     * structure using GlideString keys everywhere (including nested maps) and
     * GlideString values for any textual leaf values (library_name, engine,
     * function name, description, library_code) as well as Set elements (flags).
     *
     * Expected input shape example (string variant):
     * [ {"library_name": "mylib", "engine":"LUA", "functions": [ {"name":"f","description":null,"flags": Set<String> } ], "library_code":"#!lua ..." } ]
     *
     * Output mirrors shape but with GlideString keys/values so tests that use
     * GlideString equality succeed.
     */
    @SuppressWarnings("unchecked")
    public static Map<GlideString,Object>[] deepConvertFunctionList(Object raw) {
        Map<String,Object>[] stringMaps = toMapStringObjectArray(raw);
        Map<GlideString,Object>[] converted = new Map[stringMaps.length];
        for (int i = 0; i < stringMaps.length; i++) {
            converted[i] = deepConvertLibraryEntry(stringMaps[i]);
        }
        return converted;
    }

    private static Map<GlideString,Object> deepConvertLibraryEntry(Map<String,Object> lib) {
        Map<GlideString,Object> out = new LinkedHashMap<>();
        for (Map.Entry<String,Object> e : lib.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            GlideString gkey = GlideString.of(key);
            out.put(gkey, deepConvertValue(val));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object deepConvertValue(Object val) {
        if (val == null) return null;
        if (val instanceof GlideString) return val; // already converted
        if (val instanceof String) return GlideString.of((String) val);
        if (val instanceof Map) {
            Map<String,Object> inner = new LinkedHashMap<>();
            // We must not assume key type; convert via toString
            for (Map.Entry<?,?> me : ((Map<?,?>) val).entrySet()) {
                inner.put(String.valueOf(me.getKey()), me.getValue());
            }
            return deepConvertLibraryEntry(inner);
        }
        if (val instanceof Set) {
            // flags set (Set<String>)
            Set<?> set = (Set<?>) val;
            java.util.LinkedHashSet<GlideString> converted = new java.util.LinkedHashSet<>();
            for (Object o : set) converted.add(GlideString.of(String.valueOf(o)));
            return converted;
        }
        if (val instanceof Object[]) {
            Object[] arr = (Object[]) val;
            Object[] out = new Object[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = deepConvertValue(arr[i]);
            }
            return out;
        }
        // Leave other types (numbers, byte[]) untouched
        return val;
    }
    /**
     * Converts an Object array to an Object 2D array. This is used when the JNI layer
     * returns Object[] but the Java code expects Object[][].
     *
     * @param result The result from the JNI layer
     * @return An Object 2D array
     */
    public static Object[][] toObject2DArray(Object result) {
        if (result instanceof Object[]) {
            Object[] objects = (Object[]) result;
            Object[][] result2D = new Object[objects.length][];
            for (int i = 0; i < objects.length; i++) {
                if (objects[i] instanceof Object[]) {
                    result2D[i] = (Object[]) objects[i];
                } else {
                    result2D[i] = new Object[]{objects[i]};
                }
            }
            return result2D;
        }
        return new Object[0][0];
    }

    /**
     * Converts a Map to Map<GlideString, Double>. This is used when the JNI layer
     * returns a Map with String keys but the Java code expects GlideString keys.
     *
     * @param result The result object from JNI, expected to be a Map
     * @return A Map with GlideString keys and Double values
     */
    @SuppressWarnings("unchecked")
    public static Map<GlideString, Double> toGlideStringDoubleMap(Object result) {
        if (result instanceof Map) {
            Map<?, ?> sourceMap = (Map<?, ?>) result;
            Map<GlideString, Double> convertedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                GlideString key = GlideString.of(entry.getKey());
                Double value = (Double) entry.getValue();
                convertedMap.put(key, value);
            }
            return convertedMap;
        }
        throw new IllegalArgumentException("Expected Map but got: " +
                (result == null ? "null" : result.getClass().getName()));
    }

    /**
     * Converts a scan response to use GlideString for binary mode.
     * Scan responses are [cursor, [elements...]], and in binary mode we need
     * to convert the elements array to GlideString[].
     *
     * @param result The scan response from the server
     * @return Object[] with [GlideString cursor, GlideString[] elements]
     */
    public static Object[] convertScanResponseToBinary(Object result) {
        if (result instanceof Object[]) {
            Object[] scanResult = (Object[]) result;
            if (scanResult.length == 2) {
                // Convert cursor to GlideString
                GlideString cursor = GlideString.of(scanResult[0]);
                
                // Convert elements array to GlideString[]
                Object elements = scanResult[1];
                if (elements instanceof Object[]) {
                    Object[] elemArray = (Object[]) elements;
                    GlideString[] glideElements = new GlideString[elemArray.length];
                    for (int i = 0; i < elemArray.length; i++) {
                        glideElements[i] = GlideString.of(elemArray[i]);
                    }
                    return new Object[] { cursor, glideElements };
                }
            }
        }
        return (Object[]) result;
    }

    // ================= XINFO key normalization helpers =================
    /**
     * Ensures a Map returned from JNI has String keys (by calling toString on each original key)
     * while preserving insertion order and original values. If the input is already a Map<String,?>
     * it is returned as-is (cast) to avoid allocation.
     */
    @SuppressWarnings("unchecked")
    public static Map<String,Object> ensureStringObjectMap(Object raw) {
        if (!(raw instanceof Map)) return null;
        Map<?,?> in = (Map<?,?>) raw;
        boolean allString = true;
        for (Object k : in.keySet()) { if (!(k instanceof String)) { allString = false; break; } }
        if (allString) return (Map<String,Object>) in; // safe cast for our usage
        Map<String,Object> out = new LinkedHashMap<>();
        for (Map.Entry<?,?> e : in.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    /**
     * Ensures a Map returned from JNI has GlideString keys. Existing GlideString keys are reused;
     * other key types are converted via toString().
     */
    @SuppressWarnings("unchecked")
    public static Map<GlideString,Object> ensureGlideStringObjectMap(Object raw) {
        if (!(raw instanceof Map)) return null;
        Map<?,?> in = (Map<?,?>) raw;
        boolean allGlide = true;
        for (Object k : in.keySet()) { if (!(k instanceof GlideString)) { allGlide = false; break; } }
        if (allGlide) return (Map<GlideString,Object>) in;
        Map<GlideString,Object> out = new LinkedHashMap<>();
        for (Map.Entry<?,?> e : in.entrySet()) {
            GlideString gk = e.getKey() instanceof GlideString ? (GlideString) e.getKey() : GlideString.of(String.valueOf(e.getKey()));
            out.put(gk, e.getValue());
        }
        return out;
    }

    /** Normalize an array (Object[]/Map[]) of consumer/group maps to String-keyed maps. */
    public static Map<String,Object>[] ensureStringObjectMapArray(Object raw) {
        Map<String,Object>[] maps = toMapStringObjectArray(raw);
        for (int i = 0; i < maps.length; i++) {
            Map<String,Object> normalized = ensureStringObjectMap(maps[i]);
            if (normalized != null) maps[i] = normalized;
        }
        return maps;
    }

    /** Normalize an array of maps to GlideString-keyed maps. */
    public static Map<GlideString,Object>[] ensureGlideStringObjectMapArray(Object raw) {
        Map<GlideString,Object>[] maps = toMapGlideStringObjectArray(raw);
        for (int i = 0; i < maps.length; i++) {
            Map<GlideString,Object> normalized = ensureGlideStringObjectMap(maps[i]);
            if (normalized != null) maps[i] = normalized;
        }
        return maps;
    }

    /**
     * Converts binary command result to GlideString array.
     * This is a utility for handling binary command responses that return arrays.
     *
     * @param result The raw result from a binary command
     * @return GlideString array, handling various result formats
     */
    public static GlideString[] toBinaryGlideStringArray(Object result) {
        if (result == null) {
            return new GlideString[0];
        }
        if (result instanceof GlideString[]) {
            return (GlideString[]) result;
        }
        if (result instanceof Object[]) {
            Object[] objects = (Object[]) result;
            GlideString[] gstrings = new GlideString[objects.length];
            for (int i = 0; i < objects.length; i++) {
                gstrings[i] = objects[i] instanceof GlideString ? 
                    (GlideString) objects[i] : 
                    GlideString.of(objects[i]);
            }
            return gstrings;
        }
        return new GlideString[0];
    }

    /**
     * Converts XREAD binary command result to a properly structured Map.
     * The result format from the server is a Map where:
     * - Keys are stream names (as GlideString)
     * - Values are arrays of [messageId, fields] where fields is a flat array [field1, value1, field2, value2, ...]
     * 
     * @param result The raw result from the XREAD binary command
     * @return Map<GlideString, Map<GlideString, GlideString[][]>> with properly structured stream data
     */
    @SuppressWarnings("unchecked")
    public static Map<GlideString, Map<GlideString, GlideString[][]>> convertXReadResultToBinary(Object result) {
        if (result == null) {
            return null;
        }
        
        Map<GlideString, Map<GlideString, GlideString[][]>> binaryResult = new LinkedHashMap<>();
        
        if (result instanceof Map) {
            Map<?, ?> streamMap = (Map<?, ?>) result;
            
            for (Map.Entry<?, ?> streamEntry : streamMap.entrySet()) {
                GlideString streamKey = GlideString.of(streamEntry.getKey());
                Map<GlideString, GlideString[][]> binaryStreamData = new LinkedHashMap<>();
                
                Object streamValue = streamEntry.getValue();
                
                // Check if value is already a Map (nested Map structure)
                if (streamValue instanceof Map) {
                    Map<?, ?> messageMap = (Map<?, ?>) streamValue;
                    for (Map.Entry<?, ?> msgEntry : messageMap.entrySet()) {
                        GlideString messageId = GlideString.of(msgEntry.getKey());
                        Object fieldsObj = msgEntry.getValue();
                        
                        if (fieldsObj instanceof Object[]) {
                            Object[] fieldsArray = (Object[]) fieldsObj;
                            if (fieldsArray.length == 0) {
                                binaryStreamData.put(messageId, new GlideString[0][]);
                            } else {
                                // Check if array contains field pairs (each element is Object[2])
                                if (fieldsArray[0] instanceof Object[]) {
                                    // Array of arrays format - each element is a field pair
                                    GlideString[][] binaryFields = new GlideString[fieldsArray.length][2];
                                    for (int i = 0; i < fieldsArray.length; i++) {
                                        Object[] fieldPair = (Object[]) fieldsArray[i];
                                        binaryFields[i][0] = GlideString.of(fieldPair[0]);
                                        binaryFields[i][1] = GlideString.of(fieldPair[1]);
                                    }
                                    binaryStreamData.put(messageId, binaryFields);
                                } else {
                                    // Flat array format - alternating field names and values
                                    // Round up for odd number of elements
                                    GlideString[][] binaryFields = new GlideString[(fieldsArray.length + 1) / 2][2];
                                    for (int i = 0; i < fieldsArray.length; i += 2) {
                                        binaryFields[i / 2][0] = GlideString.of(fieldsArray[i]);
                                        binaryFields[i / 2][1] = i + 1 < fieldsArray.length ? 
                                            GlideString.of(fieldsArray[i + 1]) : GlideString.of("");
                                    }
                                    binaryStreamData.put(messageId, binaryFields);
                                }
                            }
                        }
                    }
                } else if (streamValue instanceof Object[]) {
                    // Array format (original format)
                    Object[] streamData = (Object[]) streamValue;
                    for (Object messageData : streamData) {
                        Object[] messageArray = (Object[]) messageData;
                        GlideString messageId = GlideString.of(messageArray[0]);
                        
                        // Fields come as flat array
                        Object[] fieldsFlat = messageArray.length > 1 ? (Object[]) messageArray[1] : null;
                        
                        // Handle empty or null fields array
                        if (fieldsFlat == null || fieldsFlat.length == 0) {
                            binaryStreamData.put(messageId, new GlideString[0][]);
                            continue;
                        }
                        
                        // Convert flat array to array of pairs
                        // Round up for odd number of elements
                        GlideString[][] binaryFields = new GlideString[(fieldsFlat.length + 1) / 2][2];
                        for (int i = 0; i < fieldsFlat.length; i += 2) {
                            binaryFields[i / 2][0] = GlideString.of(fieldsFlat[i]);
                            binaryFields[i / 2][1] = i + 1 < fieldsFlat.length ? 
                                GlideString.of(fieldsFlat[i + 1]) : GlideString.of("");
                        }
                        
                        binaryStreamData.put(messageId, binaryFields);
                    }
                }
                
                binaryResult.put(streamKey, binaryStreamData);
            }
        }
        
        return binaryResult;
    }

    /**
     * Unwrap cluster mode response that may have an extra Object[] wrapper.
     * In cluster mode, responses are sometimes wrapped in a single-element array.
     * 
     * @param result The raw response from the server
     * @return The unwrapped response
     */
    public static Object unwrapClusterResponse(Object result) {
        if (result instanceof Object[] && ((Object[]) result).length == 1) {
            Object[] arr = (Object[]) result;
            // If it's a single-element array, check if we should unwrap
            Object firstElement = arr[0];
            
            // Common patterns to unwrap:
            // 1. Single element containing the actual array
            if (firstElement instanceof Object[] || firstElement instanceof String[] || 
                firstElement instanceof GlideString[] || firstElement instanceof Map ||
                firstElement instanceof Collection) {
                return firstElement;
            }
        }
        return result;
    }

    /**
     * Normalize stream entry response to Map<String, String[][]> format.
     * Handles various response formats from XCLAIM, XREAD, etc.
     * 
     * @param raw The raw response
     * @return Normalized map of stream IDs to field-value pairs
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String[][]> normalizeStringStreamEntryMap(Object raw) {
        if (raw == null) return null;
        
        // Unwrap cluster response first
        raw = unwrapClusterResponse(raw);
        
        if (raw instanceof Collection) {
            raw = ((Collection<?>) raw).toArray();
        }
        
        if (raw instanceof Map) {
            // Handle Map response - convert values properly
            Map<?, ?> map = (Map<?, ?>) raw;
            Map<String, String[][]> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String id = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                String[][] fv = extractStringFieldValuePairs(value);
                out.put(id, fv);
            }
            return out;
        }
        
        if (raw instanceof Object[]) {
            Object[] entries = (Object[]) raw;
            Map<String, String[][]> out = new LinkedHashMap<>();
            for (Object e : entries) {
                if (e instanceof Collection) e = ((Collection<?>) e).toArray();
                if (!(e instanceof Object[])) continue;
                Object[] pair = (Object[]) e;
                if (pair.length == 2) {
                    String id = String.valueOf(pair[0]);
                    String[][] fv = extractStringFieldValuePairs(pair[1]);
                    out.put(id, fv);
                }
            }
            return out;
        }
        return null;
    }

    /**
     * Normalize stream entry response to Map<GlideString, GlideString[][]> format.
     * Binary-safe version for stream commands.
     * 
     * @param raw The raw response
     * @return Normalized map of stream IDs to field-value pairs
     */
    @SuppressWarnings("unchecked")
    public static Map<GlideString, GlideString[][]> normalizeGlideStringStreamEntryMap(Object raw) {
        if (raw == null) return null;
        
        // Unwrap cluster response first
        raw = unwrapClusterResponse(raw);
        
        if (raw instanceof Collection) {
            raw = ((Collection<?>) raw).toArray();
        }
        
        if (raw instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) raw;
            Map<GlideString, GlideString[][]> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                GlideString id = entry.getKey() instanceof GlideString 
                    ? (GlideString) entry.getKey() 
                    : GlideString.of(String.valueOf(entry.getKey()));
                Object value = entry.getValue();
                GlideString[][] fv = extractGlideStringFieldValuePairs(value);
                out.put(id, fv);
            }
            return out;
        }
        
        if (raw instanceof Object[]) {
            Object[] entries = (Object[]) raw;
            Map<GlideString, GlideString[][]> out = new LinkedHashMap<>();
            for (Object e : entries) {
                if (e instanceof Collection) e = ((Collection<?>) e).toArray();
                if (!(e instanceof Object[])) continue;
                Object[] pair = (Object[]) e;
                if (pair.length == 2) {
                    GlideString id = pair[0] instanceof GlideString 
                        ? (GlideString) pair[0] 
                        : GlideString.of(String.valueOf(pair[0]));
                    GlideString[][] fv = extractGlideStringFieldValuePairs(pair[1]);
                    out.put(id, fv);
                }
            }
            return out;
        }
        return null;
    }

    /**
     * Extract field-value pairs from various response formats.
     * Handles nested arrays and flat arrays.
     */
    private static String[][] extractStringFieldValuePairs(Object value) {
        // Handle the nested array structure: Object[] containing Object[]
        if (value instanceof Object[] && ((Object[]) value).length == 1) {
            Object firstElem = ((Object[]) value)[0];
            if (firstElem instanceof Object[]) {
                value = firstElem;
            }
        }
        
        if (value instanceof Collection) value = ((Collection<?>) value).toArray();
        if (!(value instanceof Object[])) return new String[0][0];
        Object[] arr = (Object[]) value;
        if (arr.length == 0) return new String[0][0];
        
        // Check if this is already a single pair structure [[field, value]]
        if (arr.length == 1 && arr[0] instanceof Object[] && ((Object[]) arr[0]).length == 2) {
            Object[] pair = (Object[]) arr[0];
            String[][] out = new String[1][2];
            out[0][0] = String.valueOf(pair[0]);
            out[0][1] = String.valueOf(pair[1]);
            return out;
        }
        
        if (arr[0] instanceof Object[] && ((Object[]) arr[0]).length == 2) {
            Object[][] pairs = (Object[][]) arr;
            String[][] out = new String[pairs.length][2];
            for (int i = 0; i < pairs.length; i++) {
                out[i][0] = String.valueOf(pairs[i][0]);
                out[i][1] = String.valueOf(pairs[i][1]);
            }
            return out;
        }
        
        if (arr.length % 2 != 0) return new String[0][0];
        int n = arr.length / 2;
        String[][] out = new String[n][2];
        for (int i = 0; i < n; i++) {
            out[i][0] = String.valueOf(arr[2 * i]);
            out[i][1] = String.valueOf(arr[2 * i + 1]);
        }
        return out;
    }

    /**
     * Extract field-value pairs from various response formats (binary-safe).
     * Handles nested arrays and flat arrays.
     */
    private static GlideString[][] extractGlideStringFieldValuePairs(Object value) {
        // Handle the nested array structure: Object[] containing Object[]
        if (value instanceof Object[] && ((Object[]) value).length == 1) {
            Object firstElem = ((Object[]) value)[0];
            if (firstElem instanceof Object[]) {
                value = firstElem;
            }
        }
        
        if (value instanceof Collection) value = ((Collection<?>) value).toArray();
        if (!(value instanceof Object[])) return new GlideString[0][0];
        Object[] arr = (Object[]) value;
        if (arr.length == 0) return new GlideString[0][0];
        
        // Check if this is already a single pair structure [[field, value]]
        if (arr.length == 1 && arr[0] instanceof Object[] && ((Object[]) arr[0]).length == 2) {
            Object[] pair = (Object[]) arr[0];
            GlideString[][] out = new GlideString[1][2];
            out[0][0] = pair[0] instanceof GlideString ? (GlideString) pair[0] : GlideString.of(String.valueOf(pair[0]));
            out[0][1] = pair[1] instanceof GlideString ? (GlideString) pair[1] : GlideString.of(String.valueOf(pair[1]));
            return out;
        }
        
        if (arr[0] instanceof Object[] && ((Object[]) arr[0]).length == 2) {
            Object[][] pairs = (Object[][]) arr;
            GlideString[][] out = new GlideString[pairs.length][2];
            for (int i = 0; i < pairs.length; i++) {
                out[i][0] = pairs[i][0] instanceof GlideString ? (GlideString) pairs[i][0] : GlideString.of(String.valueOf(pairs[i][0]));
                out[i][1] = pairs[i][1] instanceof GlideString ? (GlideString) pairs[i][1] : GlideString.of(String.valueOf(pairs[i][1]));
            }
            return out;
        }
        
        if (arr.length % 2 != 0) return new GlideString[0][0];
        int n = arr.length / 2;
        GlideString[][] out = new GlideString[n][2];
        for (int i = 0; i < n; i++) {
            out[i][0] = arr[2 * i] instanceof GlideString ? (GlideString) arr[2 * i] : GlideString.of(String.valueOf(arr[2 * i]));
            out[i][1] = arr[2 * i + 1] instanceof GlideString ? (GlideString) arr[2 * i + 1] : GlideString.of(String.valueOf(arr[2 * i + 1]));
        }
        return out;
    }
}
