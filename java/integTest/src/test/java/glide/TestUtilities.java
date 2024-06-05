/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.CLUSTER_PORTS;
import static glide.TestConfiguration.STANDALONE_PORTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import glide.api.models.ClusterValue;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtilities {
    /** Extract integer parameter value from INFO command output */
    public static int getValueFromInfo(String data, String value) {
        for (var line : data.split("\r\n")) {
            if (line.contains(value)) {
                return Integer.parseInt(line.split(":")[1]);
            }
        }
        fail();
        return 0;
    }

    /** Extract first value from {@link ClusterValue} assuming it contains a multi-value. */
    public static <T> T getFirstEntryFromMultiValue(ClusterValue<T> data) {
        return data.getMultiValue().get(data.getMultiValue().keySet().toArray(String[]::new)[0]);
    }

    /** Generates a random string of a specified length using ASCII letters. */
    public static String getRandomString(int length) {
        String asciiLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(asciiLetters.length());
            char randomChar = asciiLetters.charAt(index);
            sb.append(randomChar);
        }

        return sb.toString();
    }

    /**
     * Transforms server info string into a Map, using lines with ":" to create key-value pairs,
     * replacing duplicates with the last encountered value.
     */
    public static Map<String, String> parseInfoResponseToMap(String serverInfo) {
        return serverInfo
                .lines()
                .filter(line -> line.contains(":"))
                .map(line -> line.split(":", 2))
                .collect(
                        Collectors.toMap(
                                parts -> parts[0],
                                parts -> parts[1],
                                (existingValue, newValue) -> newValue,
                                HashMap::new));
    }

    public static RedisClientConfiguration.RedisClientConfigurationBuilder<?, ?>
            commonClientConfig() {
        return RedisClientConfiguration.builder()
                .address(NodeAddress.builder().port(STANDALONE_PORTS[0]).build());
    }

    public static RedisClusterClientConfiguration.RedisClusterClientConfigurationBuilder<?, ?>
            commonClusterClientConfig() {
        return RedisClusterClientConfiguration.builder()
                .address(NodeAddress.builder().port(CLUSTER_PORTS[0]).build());
    }

    /**
     * Deep traverse and compare two objects, including comparing content of all nested collections
     * recursively. Floating point numbers comparison performed with <code>1e-6</code> delta.
     *
     * @apiNote <code>Map</code> and <code>Set</code> comparison ignores element order.<br>
     *     <code>List</code> and <code>Array</code> comparison is order-sensitive.
     */
    public static void assertDeepEquals(Object expected, Object actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
        } else if (expected.getClass().isArray()) {
            var expectedArray = (Object[]) expected;
            var actualArray = (Object[]) actual;
            assertEquals(expectedArray.length, actualArray.length);
            for (int i = 0; i < expectedArray.length; i++) {
                assertDeepEquals(expectedArray[i], actualArray[i]);
            }
        } else if (expected instanceof List) {
            var expectedList = (List<?>) expected;
            var actualList = (List<?>) actual;
            assertEquals(expectedList.size(), actualList.size());
            for (int i = 0; i < expectedList.size(); i++) {
                assertDeepEquals(expectedList.get(i), actualList.get(i));
            }
        } else if (expected instanceof Set) {
            var expectedSet = (Set<?>) expected;
            var actualSet = (Set<?>) actual;
            assertEquals(expectedSet.size(), actualSet.size());
            assertTrue(expectedSet.containsAll(actualSet) && actualSet.containsAll(expectedSet));
        } else if (expected instanceof Map) {
            var expectedMap = (Map<?, ?>) expected;
            var actualMap = (Map<?, ?>) actual;
            assertEquals(expectedMap.size(), actualMap.size());
            for (var key : expectedMap.keySet()) {
                assertDeepEquals(expectedMap.get(key), actualMap.get(key));
            }
        } else if (expected instanceof Double || actual instanceof Double) {
            assertEquals((Double) expected, (Double) actual, 1e-6);
        } else {
            assertEquals(expected, actual);
        }
    }

    /**
     * Validate whether `FUNCTION LIST` response contains required info.
     *
     * @param response The response from redis.
     * @param libName Expected library name.
     * @param functionDescriptions Expected function descriptions. Key - function name, value -
     *     description.
     * @param functionFlags Expected function flags. Key - function name, value - flags set.
     * @param libCode Expected library to check if given.
     */
    @SuppressWarnings("unchecked")
    public static void checkFunctionListResponse(
            Map<String, Object>[] response,
            String libName,
            Map<String, String> functionDescriptions,
            Map<String, Set<String>> functionFlags,
            Optional<String> libCode) {
        assertTrue(response.length > 0);
        boolean hasLib = false;
        for (var lib : response) {
            hasLib = lib.containsValue(libName);
            if (hasLib) {
                var functions = (Object[]) lib.get("functions");
                assertEquals(functionDescriptions.size(), functions.length);
                for (var functionInfo : functions) {
                    var function = (Map<String, Object>) functionInfo;
                    var functionName = (String) function.get("name");
                    assertEquals(functionDescriptions.get(functionName), function.get("description"));
                    assertEquals(functionFlags.get(functionName), function.get("flags"));
                }
                if (libCode.isPresent()) {
                    assertEquals(libCode.get(), lib.get("library_code"));
                }
                break;
            }
        }
        assertTrue(hasLib);
    }
}
