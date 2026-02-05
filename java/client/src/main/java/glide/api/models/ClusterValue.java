/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a returned value object from a the server with cluster-mode enabled. The response type
 * may depend on the submitted {@link Route}.
 *
 * @remarks ClusterValue stores values in a union-like object. It contains a single-value or
 *     multi-value response from the server. If the command's routing is to a single node use {@link
 *     #getSingleValue()} to return a response of type <code>T</code>. Otherwise, use {@link
 *     #getMultiValue()} to return a <code>Map</code> of <code>address: nodeResponse</code> where
 *     <code>address</code> is of type <code>string</code> and <code>nodeResponse</code> is of type
 *     <code>T</code>.
 * @see <a href="https://valkey.io/docs/topics/cluster-spec/">Valkey cluster specification</a>
 * @param <T> The wrapped response type
 */
public abstract class ClusterValue<T> {

    /**
     * Get per-node value.<br>
     * Asserts if {@link #hasMultiData()} is false.
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Subscriptions are wrapped with unmodifiable maps and safe to expose")
    public abstract Map<String, T> getMultiValue();

    /**
     * Get the single value.<br>
     * Asserts if {@link #hasSingleData()} is false.
     */
    public abstract T getSingleValue();

    /**
     * Check that multi-value is stored in this object. Should be called prior to {@link
     * #getMultiValue()}.
     */
    public abstract boolean hasMultiData();

    /**
     * Check that single-value is stored in this object. Should be called prior to {@link
     * #getSingleValue()}.
     */
    public abstract boolean hasSingleData();

    /** A constructor for the value with type auto-detection. */
    @SuppressWarnings("unchecked")
    public static <T> ClusterValue<T> of(Object data) {
        return of(data, false);
    }

    /**
     * A constructor for the value with routing context.
     *
     * @param data The response data from the server
     * @param isSingleNodeRoute Whether this response came from a single-node route
     */
    @SuppressWarnings("unchecked")
    public static <T> ClusterValue<T> of(Object data, boolean isSingleNodeRoute) {
        if (data instanceof Map) {
            var map = (Map<?, T>) data;
            if (map.isEmpty() || map.keySet().toArray()[0] instanceof String) {
                return isSingleNodeRoute
                        ? ofSingleNodeMap((Map<String, T>) data)
                        : ofMultiValue((Map<String, T>) data);
            } else { // GlideString
                return isSingleNodeRoute
                        ? ofSingleNodeMapBinary((Map<GlideString, T>) data)
                        : ofMultiValueBinary((Map<GlideString, T>) data);
            }
        } else {
            return ofSingleValue((T) data);
        }
    }

    /** A constructor for the value. */
    public static <T> ClusterValue<T> ofSingleValue(T data) {
        return new SingleValue<>(data);
    }

    /** A constructor for the value. */
    public static <T> ClusterValue<T> ofMultiValue(Map<String, T> data) {
        return new MultiValue<>(Map.copyOf(data));
    }

    /** A constructor for the value. */
    public static <T> ClusterValue<T> ofMultiValueBinary(Map<GlideString, T> data) {
        return new MultiValue<>(
                Map.copyOf(
                        data.entrySet().stream()
                                .collect(Collectors.toMap(e -> e.getKey().getString(), Map.Entry::getValue))));
    }

    /** A constructor for single-node Map results (e.g., HGETALL). */
    public static <T> ClusterValue<T> ofSingleNodeMap(Map<String, T> data) {
        return new SingleNodeMap<>(Map.copyOf(data));
    }

    /** A constructor for single-node Map results with GlideString keys. */
    public static <T> ClusterValue<T> ofSingleNodeMapBinary(Map<GlideString, T> data) {
        return new SingleNodeMap<>(
                Map.copyOf(
                        data.entrySet().stream()
                                .collect(Collectors.toMap(e -> e.getKey().getString(), Map.Entry::getValue))));
    }

    /** Single value implementation. */
    private static class SingleValue<T> extends ClusterValue<T> {
        private final T value;

        SingleValue(T value) {
            this.value = value;
        }

        @Override
        public T getSingleValue() {
            return value;
        }

        @Override
        public Map<String, T> getMultiValue() {
            throw new AssertionError("No multi value stored");
        }

        @Override
        public boolean hasSingleData() {
            return true;
        }

        @Override
        public boolean hasMultiData() {
            return false;
        }
    }

    /** Multi-node value implementation. */
    private static class MultiValue<T> extends ClusterValue<T> {
        private final Map<String, T> value;

        MultiValue(Map<String, T> value) {
            this.value = value;
        }

        @Override
        public T getSingleValue() {
            throw new AssertionError("No single value stored");
        }

        @Override
        public Map<String, T> getMultiValue() {
            return value;
        }

        @Override
        public boolean hasSingleData() {
            return false;
        }

        @Override
        public boolean hasMultiData() {
            return true;
        }
    }

    /** Single-node Map result implementation (e.g., HGETALL). */
    private static class SingleNodeMap<T> extends ClusterValue<T> {
        private final Map<String, T> value;

        SingleNodeMap(Map<String, T> value) {
            this.value = value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T getSingleValue() {
            return (T) value;
        }

        @Override
        public Map<String, T> getMultiValue() {
            return value;
        }

        @Override
        public boolean hasSingleData() {
            return true;
        }

        @Override
        public boolean hasMultiData() {
            return true;
        }
    }
}
