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
     * recursively.
     *
     * @apiNote Maps comparison ignores their order, regardless of `orderMatters` argument. Map
     *     entries could be reordered, but values stored in them compared according to this parameter.
     */
    public static void assertDeepEquals(Object expected, Object actual, boolean orderMatters) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
        } else if (expected.getClass().isArray()) {
            var expectedArray = (Object[]) expected;
            var actualArray = (Object[]) actual;
            assertEquals(expectedArray.length, actualArray.length);
            for (int i = 0; i < expectedArray.length; i++) {
                assertDeepEquals(expectedArray[i], actualArray[i], orderMatters);
            }
        } else if (expected instanceof List) {
            var expectedList = (List<?>) expected;
            var actualList = (List<?>) actual;
            assertEquals(expectedList.size(), actualList.size());
            if (orderMatters) {
                for (int i = 0; i < expectedList.size(); i++) {
                    assertDeepEquals(expectedList.get(i), actualList.get(i), orderMatters);
                }
            } else {
                assertTrue(expectedList.containsAll(actualList) && actualList.containsAll(expectedList));
            }
        } else if (expected instanceof Set) {
            var expectedSet = (Set<?>) expected;
            var actualSet = (Set<?>) actual;
            var expectedArray = expectedSet.toArray();
            var actualArray = actualSet.toArray();
            assertEquals(expectedArray.length, actualArray.length);
            if (orderMatters) {
                for (int i = 0; i < expectedArray.length; i++) {
                    assertDeepEquals(expectedArray[i], actualArray[i], orderMatters);
                }
            } else {
                assertTrue(expectedSet.containsAll(actualSet) && actualSet.containsAll(expectedSet));
            }
        } else if (expected instanceof Map) {
            var expectedMap = (Map<?, ?>) expected;
            var actualMap = (Map<?, ?>) actual;
            assertEquals(expectedMap.size(), actualMap.size());
            assertDeepEquals(expectedMap.keySet(), actualMap.keySet(), false);
            for (var key : expectedMap.keySet()) {
                assertDeepEquals(expectedMap.get(key), actualMap.get(key), orderMatters);
            }
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
