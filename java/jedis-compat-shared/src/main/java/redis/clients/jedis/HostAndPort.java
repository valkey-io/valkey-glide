/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.util.Objects;

/**
 * HostAndPort compatibility class for Valkey GLIDE wrapper. Represents a host and port combination
 * for cluster node addressing.
 *
 * <p>Supports both IPv4 and IPv6:
 *
 * <ul>
 *   <li>IPv4 / hostname: {@code 127.0.0.1:6379}, {@code redis.example.com:6380}
 *   <li>IPv6 bracket form (RFC 5952): {@code [::1]:6379}, {@code [2001:db8::1]:6380}
 *   <li>IPv6 without brackets: {@code ::1:6379} (port is the segment after the last {@code :})
 * </ul>
 *
 * <p>Constructors accept the host portion without brackets; use {@link #from(String)} or {@link
 * #parseString(String)} for {@code host:port} strings.
 */
public class HostAndPort {

    private final String host;
    private final int port;

    /**
     * Create a new HostAndPort instance.
     *
     * @param host the hostname or IP address (IPv4, IPv6 without brackets, or DNS name)
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

    /**
     * Parse a host:port string into a HostAndPort instance. Supports bracketed IPv6 literals.
     *
     * @param hostAndPortStr host:port text, e.g. {@code 127.0.0.1:6379} or {@code [::1]:6379}
     * @return parsed HostAndPort
     * @throws IllegalArgumentException if the format is invalid
     */
    public static HostAndPort from(String hostAndPortStr) {
        return parseHostPort(hostAndPortStr);
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
        if (host != null && host.contains(":")) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }

    /**
     * Parse a host:port string into a HostAndPort instance.
     *
     * @param hostPort the host:port string (supports bracketed IPv6)
     * @return HostAndPort instance
     * @throws IllegalArgumentException if the format is invalid
     */
    public static HostAndPort parseString(String hostPort) {
        return parseHostPort(hostPort);
    }

    private static HostAndPort parseHostPort(String hostAndPortStr) {
        if (hostAndPortStr == null || hostAndPortStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Host and port string cannot be null or empty");
        }

        String trimmed = hostAndPortStr.trim();

        if (trimmed.startsWith("[")) {
            int closeBracket = trimmed.indexOf(']');
            if (closeBracket < 0) {
                throw new IllegalArgumentException("Invalid IPv6 host:port format: " + hostAndPortStr);
            }
            String host = trimmed.substring(1, closeBracket);
            String remainder = trimmed.substring(closeBracket + 1);
            if (!remainder.startsWith(":") || remainder.length() < 2) {
                throw new IllegalArgumentException("Invalid IPv6 host:port format: " + hostAndPortStr);
            }
            try {
                int port = Integer.parseInt(remainder.substring(1).trim());
                return new HostAndPort(host, port);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port number in: " + hostAndPortStr, e);
            }
        }

        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon <= 0 || lastColon == trimmed.length() - 1) {
            throw new IllegalArgumentException("Invalid host:port format: " + hostAndPortStr);
        }

        try {
            String host = trimmed.substring(0, lastColon).trim();
            int port = Integer.parseInt(trimmed.substring(lastColon + 1).trim());
            return new HostAndPort(host, port);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number in: " + hostAndPortStr, e);
        }
    }
}
