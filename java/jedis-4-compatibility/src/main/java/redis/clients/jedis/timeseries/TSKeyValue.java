/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.timeseries;

import java.util.Collections;
import java.util.Map;
import redis.clients.jedis.util.KeyValue;

public class TSKeyValue<V> extends KeyValue<String, V> {

    private final Map<String, String> labels;

    public TSKeyValue(String key, Map<String, String> labels, V value) {
        super(key, value);
        this.labels = labels;
    }

    public Map<String, String> getLabels() {
        return Collections.unmodifiableMap(
                labels); // ✅ Return unmodifiable view to prevent external modification
    }
}
