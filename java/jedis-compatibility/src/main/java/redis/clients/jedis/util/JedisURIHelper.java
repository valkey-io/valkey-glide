/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.util;

import java.net.URI;

/**
 * Utility class for handling Jedis URI parsing and validation. This class provides helper methods
 * for working with Redis/Valkey URIs.
 */
public class JedisURIHelper {

    private JedisURIHelper() {
        // Utility class - no instantiation
    }

    /**
     * Check if the URI uses a Redis SSL scheme.
     *
     * @param uri the URI to check
     * @return true if the URI uses rediss:// scheme, false otherwise
     */
    public static boolean isRedisSSLScheme(URI uri) {
        return uri != null && "rediss".equals(uri.getScheme());
    }

    /**
     * Check if the URI is a valid Redis URI.
     *
     * @param uri the URI to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(URI uri) {
        if (uri == null) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }

        return "redis".equals(scheme) || "rediss".equals(scheme);
    }

    /**
     * Get the default port for a Redis URI scheme.
     *
     * @param uri the URI
     * @return the default port (6379 for redis://, 6380 for rediss://)
     */
    public static int getDefaultPort(URI uri) {
        if (isRedisSSLScheme(uri)) {
            return 6380; // Common SSL convention (not from original Jedis)
        }
        return 6379; // Official Redis standard + original Jedis default
    }
}
