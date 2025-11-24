/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import glide.api.logging.Logger;
import glide.api.models.configuration.NodeAddress;
import glide.cluster.ValkeyCluster;
import java.util.List;
import org.semver4j.Semver;

public final class TestConfiguration {
    public static final boolean TLS = Boolean.parseBoolean(System.getProperty("test.server.tls", ""));
    public static final String[] STANDALONE_TLS_HOSTS = {}; // Not used
    public static final String[] CLUSTER_TLS_HOSTS = {}; // Not used
    public static final Semver SERVER_VERSION = new Semver("9.0.0");
    
    private static String[] standaloneHosts = null;
    private static String[] clusterHosts = null;
    private static String[] azClusterHosts = null;

    static {
        Logger.init(Logger.Level.OFF);
        Logger.setLoggerConfig(Logger.Level.OFF);
        System.out.printf("SERVER_VERSION = %s\n", SERVER_VERSION);
    }
    
    public static String[] getStandaloneHosts() {
        if (standaloneHosts == null) {
            standaloneHosts = getHostsFromCluster(TestUtilities.getSharedStandalone());
        }
        return standaloneHosts;
    }
    
    public static String[] getClusterHosts() {
        if (clusterHosts == null) {
            clusterHosts = getHostsFromCluster(TestUtilities.getSharedCluster());
        }
        return clusterHosts;
    }
    
    public static String[] getAzClusterHosts() {
        if (azClusterHosts == null) {
            azClusterHosts = getHostsFromCluster(TestUtilities.getSharedAzCluster());
        }
        return azClusterHosts;
    }
    
    // For backward compatibility
    public static final String[] STANDALONE_HOSTS = new String[0];
    public static final String[] CLUSTER_HOSTS = new String[0];
    public static final String[] AZ_CLUSTER_HOSTS = new String[0];
    
    private static String[] getHostsFromCluster(ValkeyCluster cluster) {
        if (cluster == null) {
            return new String[0];
        }
        List<NodeAddress> addresses = cluster.getNodesAddr();
        if (addresses == null || addresses.isEmpty()) {
            return new String[0];
        }
        return addresses.stream()
                .map(addr -> addr.getHost() + ":" + addr.getPort())
                .toArray(String[]::new);
    }
}
