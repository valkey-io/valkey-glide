/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import static glide.api.models.GlideString.gs;

import glide.api.commands.GeospatialIndicesBaseCommands;
import glide.api.models.GlideString;
import glide.api.models.commands.geospatial.GeospatialData;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Utility methods for data conversion. */
public class ArrayTransformUtils {

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
                                        GlideString.of(entry.getValue().getLongitude()),
                                        GlideString.of(entry.getValue().getLatitude()),
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
    public static <ArgType> GlideString[] toGlideStringArray(ArgType[] args) {
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
}
