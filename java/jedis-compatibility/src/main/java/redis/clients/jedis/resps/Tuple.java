/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

/**
 * Tuple compatibility class for Valkey GLIDE wrapper. Represents a scored set element with element
 * and score.
 */
public class Tuple {

    private final String element;
    private final double score;

    public Tuple(String element, double score) {
        this.element = element;
        this.score = score;
    }

    public String getElement() {
        return element;
    }

    public double getScore() {
        return score;
    }

    @Override
    public String toString() {
        return "Tuple{element='" + element + "', score=" + score + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Tuple tuple = (Tuple) obj;
        return Double.compare(tuple.score, score) == 0
                && java.util.Objects.equals(element, tuple.element);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(element, score);
    }
}
