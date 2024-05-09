/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import glide.api.commands.GeospatialIndicesBaseCommands;
import glide.api.models.commands.geospatial.GeospatialData;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Utility methods for data conversion. */
public class ArrayTransformUtils {

    /**
     * Converts a map of string keys and values of any type in to an array of strings with alternating
     * keys and values.
     *
     * @param args Map of string keys to values of any type to convert.
     * @return Array of strings [key1, value1.toString(), key2, value2.toString(), ...].
     */
    public static String[] convertMapToKeyValueStringArray(Map<String, ?> args) {
        return args.entrySet().stream()
                .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue().toString()))
                .toArray(String[]::new);
    }

    /**
     * Converts a map of string keys and values of any type into an array of strings with alternating
     * values and keys.
     *
     * @param args Map of string keys to values of any type to convert.
     * @return Array of strings [value1.toString(), key1, value2.toString(), key2, ...].
     */
    public static String[] convertMapToValueKeyStringArray(Map<String, ?> args) {
        return args.entrySet().stream()
                .flatMap(entry -> Stream.of(entry.getValue().toString(), entry.getKey()))
                .toArray(String[]::new);
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
     * Maps a Map of Arrays with value type T[] to value of U[].
     *
     * @param mapOfArrays Map of Array values to cast.
     * @param clazz The class of the array values to cast to.
     * @return A Map of arrays of type U[], containing the key/values from the input Map.
     * @param <T> The base type from which the elements are being cast.
     * @param <U> The subtype of T to which the elements are cast.
     */
    @SuppressWarnings("unchecked")
    public static <T, U extends T> Map<String, U[]> castMapOfArrays(
            Map<String, T[]> mapOfArrays, Class<U> clazz) {
        return mapOfArrays.entrySet().stream()
                .collect(Collectors.toMap(k -> k.getKey(), e -> castArray(e.getValue(), clazz)));
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
}
