/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

public class CommandUtils {

    /**
     * Converts a map to an array of strings with alternating keys and values.
     *
     * @param args Map of string pairs to convert.
     * @return Array of strings [key1, value1, key2, value2, ...].
     */
    public static String[] convertMapToArgArray(Map<String, String> args) {
        return args.entrySet().stream()
                .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
                .toArray(String[]::new);
    }

    /**
     * Casts an array of objects to an array of a specific type.
     *
     * @param objectArr Array of objects to cast.
     * @param clazz The class of the array elements to cast to.
     * @return An array of type T, containing the elements from the input array.
     * @param <T> The type to which the elements are cast.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] objectArrayToTypedArray(Object[] objectArr, Class<T> clazz) {
        return Arrays.stream(objectArr)
                .map(clazz::cast)
                .toArray(size -> (T[]) Array.newInstance(clazz, size));
    }
}
