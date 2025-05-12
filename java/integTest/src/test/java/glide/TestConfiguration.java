/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.logging.Logger;
import org.apache.commons.lang3.tuple.Pair;
import org.semver4j.Semver;

public final class TestConfiguration {
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

        System.out.printf("STANDALONE_HOSTS = %s\n", System.getProperty("test.server.standalone", ""));
        System.out.printf("CLUSTER_HOSTS = %s\n", System.getProperty("test.server.cluster", ""));
        System.out.printf("AZ_CLUSTER_HOSTS = %s\n", System.getProperty("test.server.azcluster", ""));

        var result = getVersionFromStandalone();
        if (result.getKey() != null) {
            SERVER_VERSION = result.getKey();
        } else {
            var errorStandalone = result.getValue();
            result = getVersionFromCluster();
            if (result.getKey() != null) {
                SERVER_VERSION = result.getKey();
            } else {
                var errorCluster = result.getValue();
                errorStandalone.printStackTrace(System.err);
                System.err.println();
                errorCluster.printStackTrace(System.err);
                throw new RuntimeException("Failed to get server version");
            }
        }
        System.out.printf("SERVER_VERSION = %s\n", SERVER_VERSION);
    }

    private static Pair<Semver, Exception> getVersionFromStandalone() {
        if (STANDALONE_HOSTS[0].isEmpty()) {
            return Pair.of(null, new Exception("No standalone nodes given"));
        }
        try {
            BaseClient client = GlideClient.createClient(commonClientConfig().build()).get();

            String serverVersion = TestUtilities.getServerVersion(client);
            if (serverVersion != null) {
                return Pair.of(new Semver(serverVersion), null);
            } else {
                return Pair.of(null, new Exception("Failed to parse version"));
            }
        } catch (Exception e) {
            return Pair.of(null, e);
        }
    }

    private static Pair<Semver, Exception> getVersionFromCluster() {
        if (CLUSTER_HOSTS[0].isEmpty()) {
            return Pair.of(null, new Exception("No cluster nodes given"));
        }
        try {
            BaseClient client =
                    GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

            String serverVersion = TestUtilities.getServerVersion(client);
            if (serverVersion != null) {
                return Pair.of(new Semver(serverVersion), null);
            } else {
                return Pair.of(null, new Exception("Failed to parse version"));
            }
        } catch (Exception e) {
            return Pair.of(null, e);
        }
    }
}
