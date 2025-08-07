/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.timeseries;

import java.util.Collections;
import java.util.Map;

/** TSKeyValue compatibility stub for Valkey GLIDE wrapper. */
public class TSKeyValue<T> {
    private final String key;
    private final T value;
    private final Map<String, String> labels;

    public TSKeyValue(String key, T value) {
        this.key = key;
        this.value = value;
        this.labels = Collections.emptyMap();
    }

    public String getKey() {
        return key;
    }

    public T getValue() {
        return value;
    }

    public Map<String, String> getLabels() {
        return labels;
    }
}
