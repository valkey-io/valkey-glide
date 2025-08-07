/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * Connection compatibility stub for Valkey GLIDE wrapper. This class exists only for compilation
 * compatibility with Redis JDBC driver.
 *
 * <p>NOTE: This class is not used in the GLIDE compatibility layer. All Redis operations go through
 * the Jedis class which uses GLIDE client internally.
 */
public class Connection implements AutoCloseable {

    private final String host;
    private final int port;

    public Connection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Connection(HostAndPort hostAndPort) {
        this.host = hostAndPort.getHost();
        this.port = hostAndPort.getPort();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isConnected() {
        return true; // Stub implementation
    }

    public boolean isClosed() {
        return false; // Stub implementation
    }

    @Override
    public void close() {
        // Stub implementation - no-op
    }
}
