/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.utils;

/** Valkey-client settings */
public class ConnectionSettings {
    public final String host;
    public final int port;
    public final boolean useSsl;
    public final boolean clusterMode;
    public final boolean tcpNoDelay;
    public int requestTimeoutMs;
    public final int inflightLimit;

    public ConnectionSettings(
            String host, int port, boolean useSsl, boolean clusterMode, boolean tcpNoDelay, int requestTimeoutMs) {
        this(host, port, useSsl, clusterMode, tcpNoDelay, requestTimeoutMs, 0);
    }

    public ConnectionSettings(
            String host, int port, boolean useSsl, boolean clusterMode, boolean tcpNoDelay, int requestTimeoutMs, int inflightLimit) {
        this.host = host;
        this.port = port;
        this.useSsl = useSsl;
        this.clusterMode = clusterMode;
        this.tcpNoDelay = tcpNoDelay;
        this.requestTimeoutMs = requestTimeoutMs;
        this.inflightLimit = inflightLimit;
    }
}
