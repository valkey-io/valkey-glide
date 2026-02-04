/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

/** Utility methods for Java 8 compatibility. Provides alternatives for Java 9+ APIs. */
public class Java8Utils {

    /**
     * Compares two byte arrays lexicographically. Java 8 compatible alternative to Arrays.compare
     * (Java 9+).
     *
     * @param a the first array to compare
     * @param b the second array to compare
     * @return the value 0 if the first and second array are equal and contain the same elements in
     *     the same order; a value less than 0 if the first array is lexicographically less than the
     *     second array; and a value greater than 0 if the first array is lexicographically greater
     *     than the second array
     */
    public static int compareByteArrays(byte[] a, byte[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int cmp = Byte.compare(a[i], b[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    private Java8Utils() {
        // Utility class, prevent instantiation
    }
}
