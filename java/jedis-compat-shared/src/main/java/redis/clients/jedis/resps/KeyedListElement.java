/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.AbstractMap;

/** KeyedListElement compatibility stub for Valkey GLIDE wrapper. */
public class KeyedListElement extends AbstractMap.SimpleImmutableEntry<String, String> {

    public KeyedListElement(String key, String element) {
        super(key, element);
    }

    public String getElement() {
        return getValue();
    }

    @Override
    public String toString() {
        return "KeyedListElement{key='" + getKey() + "', element='" + getValue() + "'}";
    }
}
