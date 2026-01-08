/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.ArrayList;
import java.util.List;

/** Parameters for SORT command. */
public class SortingParams {
    private List<String> params = new ArrayList<>();

    public SortingParams() {}

    /** Sort in ascending order (default). */
    public SortingParams asc() {
        params.add("ASC");
        return this;
    }

    /** Sort in descending order. */
    public SortingParams desc() {
        params.add("DESC");
        return this;
    }

    /** Sort lexicographically. */
    public SortingParams alpha() {
        params.add("ALPHA");
        return this;
    }

    /** Limit the number of returned elements. */
    public SortingParams limit(int offset, int count) {
        params.add("LIMIT");
        params.add(String.valueOf(offset));
        params.add(String.valueOf(count));
        return this;
    }

    /** Sort by external key pattern. */
    public SortingParams by(String pattern) {
        params.add("BY");
        params.add(pattern);
        return this;
    }

    /** Get external key pattern. */
    public SortingParams get(String pattern) {
        params.add("GET");
        params.add(pattern);
        return this;
    }

    /** Get the parameters as a string array. */
    public String[] getParams() {
        return params.toArray(new String[0]);
    }
}
