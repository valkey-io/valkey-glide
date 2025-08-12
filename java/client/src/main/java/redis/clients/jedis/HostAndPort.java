/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.util.Objects;

/**
 * HostAndPort compatibility class for Valkey GLIDE wrapper. Represents a host and port combination
 * for cluster node addressing.
 */
public class HostAndPort {

    private final String host;
    private final int port;

    /**
     * Create a new HostAndPort instance.
     *
     * @param host the hostname or IP address
     * @param port the port number
     */
    public HostAndPort(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Get the host.
     *
     * @return the hostname or IP address
     */
    public String getHost() {
        return host;
    }

    /**
     * Get the port.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /** Parse host:port string into HostAndPort object. */
    public static HostAndPort from(String hostAndPortStr) {
        if (hostAndPortStr == null || hostAndPortStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Host and port string cannot be null or empty");
        }

        String[] parts = hostAndPortStr.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid host:port format: " + hostAndPortStr);
        }

        try {
            String host = parts[0].trim();
            int port = Integer.parseInt(parts[1].trim());
            return new HostAndPort(host, port);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number in: " + hostAndPortStr, e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        HostAndPort that = (HostAndPort) obj;
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    /**
     * Parse a host:port string into a HostAndPort instance.
     *
     * @param hostPort the host:port string
     * @return HostAndPort instance
     * @throws IllegalArgumentException if the format is invalid
     */
    public static HostAndPort parseString(String hostPort) {
        String[] parts = hostPort.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid host:port format: " + hostPort);
        }

        try {
            return new HostAndPort(parts[0], Integer.parseInt(parts[1]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number in: " + hostPort, e);
        }
    }
}
