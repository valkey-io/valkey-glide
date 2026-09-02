/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * JedisClusterInfoCache compatibility class for Valkey GLIDE wrapper. Stub implementation for
 * compilation compatibility.
 *
 * <p>NOTE: This class is not used in the GLIDE compatibility layer. GLIDE handles cluster topology
 * internally.
 */
public class JedisClusterInfoCache {

    /** Get node key from HostAndPort. Stub implementation for compilation compatibility. */
    public static String getNodeKey(HostAndPort nodeHostAndPort) {
        if (nodeHostAndPort == null) {
            return null;
        }
        return nodeHostAndPort.getHost() + ":" + nodeHostAndPort.getPort();
    }

    /** Get node key from host and port. Stub implementation for compilation compatibility. */
    public static String getNodeKey(String host, int port) {
        return host + ":" + port;
    }
}
