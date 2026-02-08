/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Utility methods for efficient ByteBuffer operations. Provides optimized methods for common buffer
 * manipulations to reduce allocations and improve performance.
 */
public final class BufferUtils {

    private BufferUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Decodes a UTF-8 string directly from a ByteBuffer without creating intermediate byte arrays.
     * This method temporarily adjusts the buffer's limit to decode only the specified length of
     * bytes, then restores the original limit.
     *
     * @param buffer The ByteBuffer to decode from. Position will be advanced by length bytes.
     * @param length The number of bytes to decode
     * @return The decoded UTF-8 string
     * @throws java.nio.charset.CharacterCodingException if the bytes are not valid UTF-8
     */
    public static String decodeUtf8(ByteBuffer buffer, int length) {
        if (length == 0) {
            return "";
        }

        // Save current limit and set temporary limit for decoding
        int savedLimit = buffer.limit();
        try {
            buffer.limit(buffer.position() + length);
            // decode() automatically advances the position
            return StandardCharsets.UTF_8.decode(buffer).toString();
        } finally {
            // Always restore the original limit
            buffer.limit(savedLimit);
        }
    }

    /**
     * Decodes all remaining bytes in the ByteBuffer as a UTF-8 string.
     *
     * @param buffer The ByteBuffer to decode from. Position will be advanced to the limit.
     * @return The decoded UTF-8 string
     * @throws java.nio.charset.CharacterCodingException if the bytes are not valid UTF-8
     */
    public static String decodeUtf8(ByteBuffer buffer) {
        if (buffer.remaining() == 0) {
            return "";
        }
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }
}
