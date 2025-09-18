/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.io.Closeable;

/** Represents a connection to a server. This is part of the Jedis compatibility layer. */
public class Connection implements Closeable {

    private final HostAndPort hostAndPort;

    public Connection(HostAndPort hostAndPort) {
        this.hostAndPort = hostAndPort;
    }

    /** Get the host and port for this connection */
    public HostAndPort getHostAndPort() {
        return hostAndPort;
    }

    /** Get the host */
    public String getHost() {
        return hostAndPort.getHost();
    }

    /** Get the port */
    public int getPort() {
        return hostAndPort.getPort();
    }

    @Override
    public void close() {
        // Implementation for closing connection
    }
}
