/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.CLUSTER_PORTS;
import static glide.TestConfiguration.STANDALONE_PORTS;
import static org.junit.jupiter.api.Assertions.fail;

import glide.api.models.ClusterValue;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.exceptions.RequestException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;

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
     * Run a command and expect a response or a given error. Both cases are OK.
     *
     * @param lambda Client command wrapped by a lambda to execute.
     * @param error An error to expect. Any other error is rethrown.
     * @return Pair of response + null if command succeeded; or null + error if got the expected
     *     error.
     * @param <T> Command return type.
     */
    @SneakyThrows
    public static <T> Pair<T, String> tryCommandWithExpectedError(
            Supplier<CompletableFuture<T>> lambda, String error) {
        try {
            return Pair.of(lambda.get().get(), null);
        } catch (ExecutionException ee) {
            // Check for specific error
            if (ee.getCause() instanceof RequestException && ee.getCause().getMessage().contains(error)) {
                return Pair.of(null, ee.getCause().getMessage());
            }
            throw ee;
        }
    }
}
