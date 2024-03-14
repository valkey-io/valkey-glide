/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static org.junit.jupiter.api.Assertions.fail;

import glide.api.models.ClusterValue;
import java.util.Arrays;
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

    /**
     * Replaces 'ping-sent' and 'pong-recv' timestamps in Redis Cluster Nodes output with
     * placeholders.
     */
    public static String removeTimeStampsFromClusterNodesOutput(String rawOutput) {
        return Arrays.stream(rawOutput.split("\n"))
                .map(
                        line -> {
                            String[] parts = line.split(" ");
                            // Format for CLUSTER NODES COMMAND: <id> <ip:port@cport[,hostname]> <flags> <master>
                            // <ping-sent> <pong-recv> <config-epoch> <link-state> <slot> <slot> ... <slot>
                            if (parts.length > 6) {
                                parts[4] = "ping-sent";
                                parts[5] = "pong-recv";
                            }
                            return String.join(" ", parts);
                        })
                .collect(Collectors.joining("\n"));
    }
}
