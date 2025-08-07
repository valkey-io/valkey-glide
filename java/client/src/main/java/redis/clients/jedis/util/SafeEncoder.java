/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.util;

import java.nio.charset.StandardCharsets;

/**
 * SafeEncoder compatibility class for Valkey GLIDE wrapper. Provides basic string/byte encoding
 * utilities for compilation compatibility.
 */
public class SafeEncoder {

    /** Encode string to bytes using UTF-8. */
    public static byte[] encode(String str) {
        if (str == null) {
            return null;
        }
        return str.getBytes(StandardCharsets.UTF_8);
    }

    /** Encode multiple strings to byte arrays. */
    public static byte[][] encodeMany(String... strs) {
        if (strs == null) {
            return null;
        }
        byte[][] result = new byte[strs.length][];
        for (int i = 0; i < strs.length; i++) {
            result[i] = encode(strs[i]);
        }
        return result;
    }

    /** Decode bytes to string using UTF-8. */
    public static String encode(byte[] data) {
        if (data == null) {
            return null;
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
