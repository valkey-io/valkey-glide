/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

// Removed static imports to break circular dependency
// import static glide.TestUtilities.commonClientConfig;
// import static glide.TestUtilities.commonClusterClientConfig;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.logging.Logger;
import org.apache.commons.lang3.tuple.Pair;
import org.semver4j.Semver;

public final class TestConfiguration {
    public static final boolean TLS = Boolean.parseBoolean(System.getProperty("test.server.tls", ""));

    private static Semver cachedServerVersion = null;
    private static boolean versionInitialized = false;

    static {
        Logger.init(Logger.Level.OFF);
        Logger.setLoggerConfig(Logger.Level.OFF);
    }

    public static String[] getStandaloneHosts() {
        String hosts = System.getProperty("test.server.standalone", "");
        return hosts.isEmpty() ? new String[] {""} : hosts.split(",");
    }

    public static String[] getClusterHosts() {
        String hosts = System.getProperty("test.server.cluster", "");
        return hosts.isEmpty() ? new String[] {""} : hosts.split(",");
    }

    public static String[] getAzClusterHosts() {
        String hosts = System.getProperty("test.server.azcluster", "");
        return hosts.isEmpty() ? new String[] {""} : hosts.split(",");
    }

    public static synchronized Semver getServerVersion() {
        if (!versionInitialized) {
            System.out.printf(
                    "STANDALONE_HOSTS = %s\n", System.getProperty("test.server.standalone", ""));
            System.out.printf("CLUSTER_HOSTS = %s\n", System.getProperty("test.server.cluster", ""));
            System.out.printf("AZ_CLUSTER_HOSTS = %s\n", System.getProperty("test.server.azcluster", ""));

            var result = getVersionFromStandalone();
            if (result.getKey() != null) {
                cachedServerVersion = result.getKey();
            } else {
                var errorStandalone = result.getValue();
                result = getVersionFromCluster();
                if (result.getKey() != null) {
                    cachedServerVersion = result.getKey();
                } else {
                    var errorCluster = result.getValue();
                    errorStandalone.printStackTrace(System.err);
                    System.err.println();
                    errorCluster.printStackTrace(System.err);
                    throw new RuntimeException("Failed to get server version");
                }
            }
            System.out.printf("SERVER_VERSION = %s\n", cachedServerVersion);
            versionInitialized = true;
        }
        return cachedServerVersion;
    }

    // Backward compatibility fields - initialize directly from system properties (no circular
    // dependency)
    @Deprecated
    public static final String[] STANDALONE_HOSTS =
            getStandaloneHosts(); // Use getStandaloneHosts() instead

    @Deprecated
    public static final String[] CLUSTER_HOSTS = getClusterHosts(); // Use getClusterHosts() instead

    @Deprecated
    public static final String[] AZ_CLUSTER_HOSTS =
            getAzClusterHosts(); // Use getAzClusterHosts() instead

    @Deprecated
    public static final Semver SERVER_VERSION =
            null; // Use getServerVersion() instead - lazy loaded to avoid circular dependency

    private static Pair<Semver, Exception> getVersionFromStandalone() {
        String[] standaloneHosts = getStandaloneHosts();
        if (standaloneHosts[0].isEmpty()) {
            return Pair.of(null, new Exception("No standalone nodes given"));
        }
        try {
            BaseClient client =
                    GlideClient.createClient(TestUtilities.commonClientConfig().build()).get();

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
        String[] clusterHosts = getClusterHosts();
        if (clusterHosts[0].isEmpty()) {
            return Pair.of(null, new Exception("No cluster nodes given"));
        }
        try {
            BaseClient client =
                    GlideClusterClient.createClient(TestUtilities.commonClusterClientConfig().build()).get();

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
