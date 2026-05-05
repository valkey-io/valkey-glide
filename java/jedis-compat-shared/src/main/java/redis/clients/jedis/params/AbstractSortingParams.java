/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.ArrayList;
import java.util.List;

/** Parameters for SORT command. */
public abstract class AbstractSortingParams<T extends AbstractSortingParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    private List<String> params = new ArrayList<>();

    protected AbstractSortingParams() {}

    /** Sort in ascending order (default). */
    public T asc() {
        params.add("ASC");
        return self();
    }

    /** Sort in descending order. */
    public T desc() {
        params.add("DESC");
        return self();
    }

    /** Sort lexicographically. */
    public T alpha() {
        params.add("ALPHA");
        return self();
    }

    /** Limit the number of returned elements. */
    public T limit(int offset, int count) {
        params.add("LIMIT");
        params.add(String.valueOf(offset));
        params.add(String.valueOf(count));
        return self();
    }

    /** Sort by external key pattern. */
    public T by(String pattern) {
        params.add("BY");
        params.add(pattern);
        return self();
    }

    /** Get external key pattern. */
    public T get(String pattern) {
        params.add("GET");
        params.add(pattern);
        return self();
    }

    /** Get the parameters as a string array. */
    public String[] getParams() {
        return params.toArray(new String[0]);
    }
}
