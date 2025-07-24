/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis.resps;

import java.util.List;

/** Result of a SCAN command in Jedis compatibility layer. */
public class ScanResult<T> {
    private final String cursor;
    private final List<T> results;

    public ScanResult(String cursor, List<T> results) {
        this.cursor = cursor;
        this.results = results;
    }

    public ScanResult(byte[] cursor, List<T> results) {
        this.cursor = new String(cursor);
        this.results = results;
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
        return cursor.getBytes();
    }

    /**
     * Returns the scan results.
     *
     * @return the scan results
     */
    public List<T> getResult() {
        return results;
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
