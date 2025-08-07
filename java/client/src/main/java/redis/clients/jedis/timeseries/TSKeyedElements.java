/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.timeseries;

import java.util.List;

/** TSKeyedElements compatibility stub for Valkey GLIDE wrapper. */
public class TSKeyedElements {
    private final String key;
    private final List<TSElement> value;

    public TSKeyedElements(String key, List<TSElement> value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public List<TSElement> getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "TSKeyedElements{key='" + key + "', value=" + value + "}";
    }
}
