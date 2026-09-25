/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.util;

/**
 * JedisClusterHashTag compatibility class for Valkey GLIDE wrapper. Provides cluster hash tag
 * utilities for compilation compatibility.
 */
public class JedisClusterHashTag {

    /**
     * Check if a key pattern is cluster compliant. Stub implementation for compilation compatibility.
     *
     * @param keyPattern the key pattern to check
     * @return {@code true} if the pattern contains both an opening and a closing brace character,
     *     {@code false} otherwise
     */
    public static boolean isClusterCompliantMatchPattern(String keyPattern) {
        if (keyPattern == null) {
            return false;
        }

        // Simple stub implementation - just check if pattern contains hash tags
        return keyPattern.contains("{") && keyPattern.contains("}");
    }

    /**
     * Extract hash tag from a key. Stub implementation for compilation compatibility.
     *
     * @param key the key to inspect
     * @return the text between the first opening brace and the following closing brace, or the
     *     whole key when no such pair exists
     */
    public static String getHashTag(String key) {
        if (key == null) {
            return null;
        }

        int start = key.indexOf('{');
        int end = key.indexOf('}', start);

        if (start >= 0 && end > start) {
            return key.substring(start + 1, end);
        }

        return key;
    }
}
