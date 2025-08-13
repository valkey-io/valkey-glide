/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.nio.charset.StandardCharsets;
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

    /**
     * Creates a ScanResult with a byte array cursor.
     *
     * <p>This constructor provides compatibility with original Jedis which supports binary cursors.
     *
     * @param cursor the cursor as a byte array
     * @param result the list of scan results
     */
    public ScanResult(byte[] cursor, List<T> result) {
        this.cursor = cursor != null ? new String(cursor, StandardCharsets.UTF_8) : "0";
        // Defensive copy to prevent external modification (fixes SpotBugs EI_EXPOSE_REP2)
        this.result = result != null ? new ArrayList<>(result) : new ArrayList<>();
    }

    public String getCursor() {
        return cursor;
    }

    /**
     * Returns the cursor as a byte array.
     *
     * <p>This method provides compatibility with original Jedis which supports binary cursor
     * operations.
     *
     * @return the cursor as bytes
     */
    public byte[] getCursorAsBytes() {
        return cursor.getBytes(StandardCharsets.UTF_8);
    }

    public List<T> getResult() {
        // Return unmodifiable view to prevent external modification (fixes SpotBugs EI_EXPOSE_REP)
        return Collections.unmodifiableList(result);
    }

    /**
     * Checks if the scan iteration is complete.
     *
     * <p>A scan iteration is complete when the cursor is "0", indicating that all keys have been
     * scanned and the iteration should stop.
     *
     * @return true if the iteration is complete (cursor is "0"), false otherwise
     */
    public boolean isCompleteIteration() {
        return "0".equals(cursor);
    }

    @Override
    public String toString() {
        return "ScanResult{cursor='" + cursor + "', result=" + result + "}";
    }
}
