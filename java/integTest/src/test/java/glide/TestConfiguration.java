/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;

import com.vdurmont.semver4j.Semver;
import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.logging.Logger;

public final class TestConfiguration {
    // All servers are hosted on localhost
    public static final String[] STANDALONE_HOSTS =
            System.getProperty("test.server.standalone", "").split(",");
    public static final String[] CLUSTER_HOSTS =
            System.getProperty("test.server.cluster", "").split(",");
    public static final String[] AZ_CLUSTER_HOSTS =
            System.getProperty("test.server.azcluster", "").split(",");
    public static final Semver SERVER_VERSION;
    public static final boolean TLS = Boolean.parseBoolean(System.getProperty("test.server.tls", ""));

    static {
        Logger.init(Logger.Level.OFF);
        Logger.setLoggerConfig(Logger.Level.OFF);
        try {
            BaseClient client =
                    !STANDALONE_HOSTS[0].isEmpty()
                            ? GlideClient.createClient(commonClientConfig().build()).get()
                            : GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

            String serverVersion = TestUtilities.getServerVersion(client);
            if (serverVersion != null) {
                SERVER_VERSION = new Semver(serverVersion);
            } else {
                throw new Exception("Failed to get server version");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to get server version", e);
        }
    }
}
