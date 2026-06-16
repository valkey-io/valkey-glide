/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.ArrayList;
import java.util.List;

/** Parameters for SORT command. */
public abstract class AbstractSortingParams<T extends AbstractSortingParams<T>> implements IParams {

    private enum Order {
        ASC,
        DESC
    }

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    private Order order;
    private boolean alpha;
    private Integer limitOffset;
    private Integer limitCount;
    private String byPattern;
    private final List<String> getPatterns = new ArrayList<>();

    protected AbstractSortingParams() {}

    /** Sort in ascending order (default). */
    public T asc() {
        order = Order.ASC;
        return self();
    }

    /** Sort in descending order. */
    public T desc() {
        order = Order.DESC;
        return self();
    }

    /** Sort lexicographically. */
    public T alpha() {
        alpha = true;
        return self();
    }

    /** Limit the number of returned elements. */
    public T limit(int offset, int count) {
        limitOffset = offset;
        limitCount = count;
        return self();
    }

    /** Sort by external key pattern. */
    public T by(String pattern) {
        byPattern = pattern;
        return self();
    }

    /** Get external key pattern. */
    public T get(String pattern) {
        getPatterns.add(pattern);
        return self();
    }

    /** Get the parameters as a string array. */
    public String[] getParams() {
        List<String> params = new ArrayList<>();
        if (byPattern != null) {
            params.add("BY");
            params.add(byPattern);
        }
        if (limitOffset != null && limitCount != null) {
            params.add("LIMIT");
            params.add(String.valueOf(limitOffset));
            params.add(String.valueOf(limitCount));
        }
        for (String pattern : getPatterns) {
            params.add("GET");
            params.add(pattern);
        }
        if (order == Order.ASC) {
            params.add("ASC");
        } else if (order == Order.DESC) {
            params.add("DESC");
        }
        if (alpha) {
            params.add("ALPHA");
        }
        return params.toArray(new String[0]);
    }
}
