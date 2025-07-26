/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis.resps;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of a SCAN command in Jedis compatibility layer. */
public class ScanResult<T> {
    private final String cursor;
    private final List<T> results;

    public ScanResult(String cursor, List<T> results) {
        this.cursor = cursor;
        this.results = results != null ? new ArrayList<>(results) : new ArrayList<>();
    }

    public ScanResult(byte[] cursor, List<T> results) {
        this.cursor = new String(cursor, StandardCharsets.UTF_8);
        this.results = results != null ? new ArrayList<>(results) : new ArrayList<>();
    }

    /**
     * Returns the new value of the cursor.
     *
     * @return the new cursor value
     */
    public String getCursor() {
        return cursor;
    }

    /**
     * Returns the cursor as bytes.
     *
     * @return the cursor as bytes
     */
    public byte[] getCursorAsBytes() {
        return cursor.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns the scan results.
     *
     * @return an unmodifiable view of the scan results
     */
    public List<T> getResult() {
        return Collections.unmodifiableList(results);
    }

    /**
     * Check if the iteration is complete.
     *
     * @return true if iteration is complete (cursor is "0")
     */
    public boolean isCompleteIteration() {
        return "0".equals(cursor);
    }
}
