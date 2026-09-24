/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * HostAndPortMapper compatibility interface for Valkey GLIDE wrapper. Stub implementation for
 * compilation compatibility.
 */
public interface HostAndPortMapper {

    /**
     * Map a HostAndPort to another HostAndPort. Used for host mapping in cluster configurations.
     *
     * @param hostAndPort the address reported by the cluster
     * @return the address to connect to instead, or {@code null} to keep the reported one
     */
    HostAndPort getHostAndPort(HostAndPort hostAndPort);
}
