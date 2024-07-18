/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import com.vdurmont.semver4j.Semver;
import java.util.Arrays;

public final class TestConfiguration {
    // All servers are hosted on localhost
    public static final int[] STANDALONE_PORTS = getPortsFromProperty("test.server.standalone.ports");
    public static final int[] CLUSTER_PORTS = getPortsFromProperty("test.server.cluster.ports");
    public static final Semver SERVER_VERSION = new Semver(System.getProperty("test.server.version"));

    private static int[] getPortsFromProperty(String propName) {
        return Arrays.stream(System.getProperty(propName).split(","))
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}
