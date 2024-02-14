/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

public class CommandUtils {
    public static String[] convertMapToArgArray(Map<String, String> args) {
        return args.entrySet().stream()
                .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
                .toArray(String[]::new);
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] objectArrayToTypedArray(Object[] objectArr, Class<T> clazz) {
        return Arrays.stream(objectArr)
                .map(clazz::cast)
                .toArray(size -> (T[]) Array.newInstance(clazz, size));
    }
}
