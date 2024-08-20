/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import com.vdurmont.semver4j.Semver;

public final class TestConfiguration {
    // All servers are hosted on localhost
    public static final String[] STANDALONE_HOSTS =
            System.getProperty("test.server.standalone", "").split(",");
    public static final String[] CLUSTER_HOSTS =
            System.getProperty("test.server.cluster", "").split(",");
    public static final Semver SERVER_VERSION = new Semver(System.getProperty("test.server.version"));
}
