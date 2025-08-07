/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** ScanResult compatibility stub for Valkey GLIDE wrapper. */
public class ScanResult<T> {
    private final String cursor;
    private final List<T> result;

    public ScanResult(String cursor, List<T> result) {
        this.cursor = cursor;
        // Defensive copy to prevent external modification (fixes SpotBugs EI_EXPOSE_REP2)
        this.result = result != null ? new ArrayList<>(result) : new ArrayList<>();
    }

    public String getCursor() {
        return cursor;
    }

    public List<T> getResult() {
        // Return unmodifiable view to prevent external modification (fixes SpotBugs EI_EXPOSE_REP)
        return Collections.unmodifiableList(result);
    }

    @Override
    public String toString() {
        return "ScanResult{cursor='" + cursor + "', result=" + result + "}";
    }
}
