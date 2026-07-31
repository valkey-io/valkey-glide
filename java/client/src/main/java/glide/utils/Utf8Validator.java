/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

/**
 * Low-level, high-performance utility methods related to UTF-8 character encoding. Vendored from
 * Google Guava's com.google.common.base.Utf8.
 */
public final class Utf8Validator {

    public static boolean isWellFormed(byte[] bytes) {
        return isWellFormed(bytes, 0, bytes.length);
    }

    public static boolean isWellFormed(byte[] bytes, int off, int len) {
        int end = off + len;
        if (off < 0 || len < 0 || end > bytes.length || end < 0) {
            throw new IndexOutOfBoundsException();
        }
        // Look for the first non-ASCII character.
        for (int i = off; i < end; i++) {
            if (bytes[i] < 0) {
                return isWellFormedSlowPath(bytes, i, end);
            }
        }
        return true;
    }

    private static boolean isWellFormedSlowPath(byte[] bytes, int off, int end) {
        int index = off;
        while (true) {
            int byte1;

            // Optimize for interior runs of ASCII bytes.
            do {
                if (index >= end) {
                    return true;
                }
            } while ((byte1 = bytes[index++]) >= 0);

            if (byte1 < (byte) 0xE0) {
                // Two-byte form.
                if (index == end) {
                    return false;
                }
                // Simultaneously check for illegal trailing-byte in leading position
                // and overlong 2-byte form.
                if (byte1 < (byte) 0xC2 || bytes[index++] > (byte) 0xBF) {
                    return false;
                }
            } else if (byte1 < (byte) 0xF0) {
                // Three-byte form.
                if (index + 1 >= end) {
                    return false;
                }
                int byte2 = bytes[index++];
                if (byte2 > (byte) 0xBF
                        // Overlong? 5 most significant bits must not all be zero.
                        || (byte1 == (byte) 0xE0 && byte2 < (byte) 0xA0)
                        // Check for illegal surrogate codepoints.
                        || (byte1 == (byte) 0xED && byte2 >= (byte) 0xA0)
                        // Third byte trailing-byte test.
                        || bytes[index++] > (byte) 0xBF) {
                    return false;
                }
            } else {
                // Four-byte form.
                if (index + 2 >= end) {
                    return false;
                }
                int byte2 = bytes[index++];
                if (byte2 > (byte) 0xBF
                        // Check that 1 <= plane <= 16. Tricky optimized form of:
                        // if (byte1 > (byte) 0xF4
                        //     || byte1 == (byte) 0xF0 && byte2 < (byte) 0x90
                        //     || byte1 == (byte) 0xF4 && byte2 > (byte) 0x8F)
                        || (((byte1 << 28) + (byte2 - (byte) 0x90)) >> 30) != 0
                        // Third byte trailing-byte test
                        || bytes[index++] > (byte) 0xBF
                        // Fourth byte trailing-byte test
                        || bytes[index++] > (byte) 0xBF) {
                    return false;
                }
            }
        }
    }

    private Utf8Validator() {}
}
