/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import java.util.Arrays;

public final class TestConfiguration {
    // All redis servers are hosted on localhost
    public static final int[] STANDALONE_PORTS = getPortsFromProperty("test.redis.standalone.ports");
    public static final int[] CLUSTER_PORTS = getPortsFromProperty("test.redis.cluster.ports");
    public static final String REDIS_VERSION = System.getProperty("test.redis.version");

    private static int[] getPortsFromProperty(String propName) {
        return Arrays.stream(System.getProperty(propName).split(","))
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}
