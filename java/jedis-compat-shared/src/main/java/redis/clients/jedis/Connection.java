/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.io.Closeable;

/** Represents a connection to a server. This is part of the Jedis compatibility layer. */
public class Connection implements Closeable {

    private final HostAndPort hostAndPort;

    public Connection(HostAndPort hostAndPort) {
        this.hostAndPort = hostAndPort;
    }

    /**
     * Get the host and port for this connection
     *
     * @return the address this connection targets
     */
    public HostAndPort getHostAndPort() {
        return hostAndPort;
    }

    /**
     * Get the host
     *
     * @return the host this connection targets
     */
    public String getHost() {
        return hostAndPort.getHost();
    }

    /**
     * Get the port
     *
     * @return the port this connection targets
     */
    public int getPort() {
        return hostAndPort.getPort();
    }

    @Override
    public void close() {
        // Implementation for closing connection
    }
}
