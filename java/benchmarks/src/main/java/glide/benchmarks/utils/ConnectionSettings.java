/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.utils;

import glide.connectors.resources.ThreadPoolResource;

/** Redis-client settings */
public class ConnectionSettings {
    public final String host;
    public final int port;
    public final boolean useSsl;
    public final boolean clusterMode;
    public ThreadPoolResource threadPoolResource;

    public ConnectionSettings(
            String host,
            int port,
            boolean useSsl,
            boolean clusterMode,
            ThreadPoolResource threadPoolResource) {
        this.host = host;
        this.port = port;
        this.useSsl = useSsl;
        this.clusterMode = clusterMode;
        this.threadPoolResource = threadPoolResource;
    }
}
