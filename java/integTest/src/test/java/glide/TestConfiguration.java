/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;

import com.vdurmont.semver4j.Semver;
import glide.api.GlideClient;
import java.util.Arrays;

public final class TestConfiguration {
    // All servers are hosted on localhost
    public static final int[] STANDALONE_PORTS = getPortsFromProperty("test.server.standalone.ports");
    public static final int[] CLUSTER_PORTS = getPortsFromProperty("test.server.cluster.ports");
    public static final Semver SERVER_VERSION;

    static {
        try {
            String serverVersion =
                    TestUtilities.getServerVersion(
                            GlideClient.createClient(commonClientConfig().build()).get());
            if (serverVersion != null) {
                SERVER_VERSION = new Semver(serverVersion);
            } else {
                throw new Exception("Failed to get server version");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to get server version", e);
        }
    }

    private static int[] getPortsFromProperty(String propName) {
        return Arrays.stream(System.getProperty(propName).split(","))
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}
