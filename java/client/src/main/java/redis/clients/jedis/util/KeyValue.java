/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.util;

/**
 * KeyValue compatibility class for Valkey GLIDE wrapper. Simple key-value pair holder for
 * compilation compatibility.
 */
public class KeyValue<K, V> {

    private final K key;
    private final V value;

    public KeyValue(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "KeyValue{key=" + key + ", value=" + value + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        KeyValue<?, ?> keyValue = (KeyValue<?, ?>) obj;
        return java.util.Objects.equals(key, keyValue.key)
                && java.util.Objects.equals(value, keyValue.value);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(key, value);
    }
}
