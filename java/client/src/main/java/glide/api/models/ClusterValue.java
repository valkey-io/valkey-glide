/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import java.util.Map;

/**
 * union-like type which can store single-value or multi-value retrieved from Redis. The
 * multi-value, if defined, contains the routed value as a Map<String, Object> containing a cluster
 * node address to cluster node value.
 *
 * @param <T> The wrapped data type
 */
public class ClusterValue<T> {
    private Map<String, T> multiValue = null;

    private T singleValue = null;

    private ClusterValue() {}

    /**
     * Get per-node value.<br>
     * Check with {@link #hasMultiData()} prior to accessing the data.
     */
    public Map<String, T> getMultiValue() {
        assert hasMultiData() : "No multi value stored";
        return multiValue;
    }

    /**
     * Get the single value.<br>
     * Check with {@link #hasSingleData()} ()} prior to accessing the data.
     */
    public T getSingleValue() {
        assert hasSingleData() : "No single value stored";
        return singleValue;
    }

    /** A constructor for the value with type auto-detection. */
    @SuppressWarnings("unchecked")
    public static <T> ClusterValue<T> of(Object data) {
        var res = new ClusterValue<T>();
        if (data instanceof Map) {
            res.multiValue = (Map<String, T>) data;
        } else {
            res.singleValue = (T) data;
        }
        return res;
    }

    /** A constructor for the value. */
    public static <T> ClusterValue<T> ofSingleValue(T data) {
        var res = new ClusterValue<T>();
        res.singleValue = data;
        return res;
    }

    /** A constructor for the value. */
    public static <T> ClusterValue<T> ofMultiValue(Map<String, T> data) {
        var res = new ClusterValue<T>();
        res.multiValue = data;
        return res;
    }

    /** Check that multi-value is stored in this object. Use it prior to accessing the data. */
    public boolean hasMultiData() {
        return multiValue != null;
    }

    /** Check that single-value is stored in this object. Use it prior to accessing the data. */
    public boolean hasSingleData() {
        return !hasMultiData();
    }
}
