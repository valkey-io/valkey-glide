/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

/** KeyedZSetElement compatibility stub for Valkey GLIDE wrapper. */
public class KeyedZSetElement extends Tuple {
    private final String key;

    public KeyedZSetElement(String key, String element, double score) {
        super(element, score);
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "KeyedZSetElement{key='"
                + key
                + "', element='"
                + getElement()
                + "', score="
                + getScore()
                + "}";
    }
}
