/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.search.aggr;

import java.util.List;
import java.util.Map;

/** AggregationResult compatibility stub for Valkey GLIDE wrapper. */
public class AggregationResult {

    private final List<Map<String, Object>> results;
    private final long totalResults;
    private final long cursorId;

    public AggregationResult(List<Map<String, Object>> results) {
        this.results = results;
        this.totalResults = results != null ? results.size() : 0;
        this.cursorId = 0;
    }

    public List<Map<String, Object>> getResults() {
        return results;
    }

    public long getTotalResults() {
        return totalResults;
    }

    public long getCursorId() {
        return cursorId;
    }

    @Override
    public String toString() {
        return "AggregationResult{results=" + results + "}";
    }
}
