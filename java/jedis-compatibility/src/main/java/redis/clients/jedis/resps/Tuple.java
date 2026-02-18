/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Arrays;
import java.util.Objects;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Tuple compatibility class for Valkey GLIDE wrapper. Represents a sorted set element with element
 * and score. Supports both String and binary (byte[]) element representations for full Jedis
 * compatibility.
 *
 * <p>Note: This class is compatible with Jedis Tuple and follows the same behavior. Null scores
 * are allowed in the constructor but will cause NullPointerException when calling hashCode(),
 * equals(), getScore(), or compareTo() methods.
 */
public class Tuple implements Comparable<Tuple> {

    private byte[] element;
    private Double score;

    public Tuple(String element, Double score) {
        this(SafeEncoder.encode(element), score);
    }

    public Tuple(byte[] element, Double score) {
        this.element = element;
        this.score = score;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result;
        if (null != element) {
            for (final byte b : element) {
                result = prime * result + b;
            }
        }
        long temp = Double.doubleToLongBits(score);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (!(obj instanceof Tuple)) return false;

        Tuple other = (Tuple) obj;
        if (!Arrays.equals(element, other.element)) return false;
        return Objects.equals(score, other.score);
    }

    /**
     * Compares this Tuple with another Tuple for order. First compares by score, then by element
     * lexicographically if scores are equal.
     *
     * @param other the Tuple to compare with (must not be null)
     * @return a negative integer, zero, or a positive integer as this Tuple is less than, equal
     *     to, or greater than the specified Tuple
     * @throws NullPointerException if other is null or if either Tuple has a null score
     */
    @Override
    public int compareTo(Tuple other) {
        return compare(this, other);
    }

    /**
     * Compares two Tuple objects for order.
     *
     * @param t1 the first Tuple (must not be null)
     * @param t2 the second Tuple (must not be null)
     * @return a negative integer, zero, or a positive integer as t1 is less than, equal to, or
     *     greater than t2
     * @throws NullPointerException if t1 or t2 is null, or if either has a null score
     */
    public static int compare(Tuple t1, Tuple t2) {
        int compScore = Double.compare(t1.score, t2.score);
        if (compScore != 0) return compScore;

        return compareByteArrays(t1.element, t2.element);
    }

    private static int compareByteArrays(byte[] a, byte[] b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        int minLength = Math.min(a.length, b.length);
        for (int i = 0; i < minLength; i++) {
            int cmp = Byte.compare(a[i], b[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }

    public String getElement() {
        if (null != element) {
            return SafeEncoder.encode(element);
        } else {
            return null;
        }
    }

    public byte[] getBinaryElement() {
        return element;
    }

    /**
     * Returns the score of this Tuple.
     *
     * @return the score as a primitive double
     * @throws NullPointerException if the score is null (due to auto-unboxing)
     */
    public double getScore() {
        return score;
    }

    @Override
    public String toString() {
        return '[' + SafeEncoder.encode(element) + ',' + score + ']';
    }
}
