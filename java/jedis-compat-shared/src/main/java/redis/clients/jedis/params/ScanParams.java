/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.nio.charset.StandardCharsets;

/** Parameters for SCAN command in Jedis compatibility layer. */
public class ScanParams {
    public static final String SCAN_POINTER_START = "0";
    public static final byte[] SCAN_POINTER_START_BINARY =
            SCAN_POINTER_START.getBytes(StandardCharsets.UTF_8);

    private String matchPattern;
    private Long count;
    private String type;

    /**
     * Set the MATCH pattern for the scan.
     *
     * @param pattern the pattern to match
     * @return this ScanParams instance
     */
    public ScanParams match(String pattern) {
        this.matchPattern = pattern;
        return this;
    }

    /**
     * Set the MATCH pattern for the scan.
     *
     * @param pattern the pattern to match
     * @return this ScanParams instance
     */
    public ScanParams match(byte[] pattern) {
        this.matchPattern = new String(pattern, StandardCharsets.UTF_8);
        return this;
    }

    /**
     * Set the COUNT hint for the scan.
     *
     * @param count the count hint
     * @return this ScanParams instance
     */
    public ScanParams count(int count) {
        this.count = (long) count;
        return this;
    }

    /**
     * Set the TYPE filter for the scan.
     *
     * @param type the type to filter by
     * @return this ScanParams instance
     */
    public ScanParams type(String type) {
        this.type = type;
        return this;
    }

    /**
     * Get the match pattern.
     *
     * @return the match pattern
     */
    public String getMatchPattern() {
        return matchPattern;
    }

    /**
     * Get the count hint.
     *
     * @return the count hint
     */
    public Long getCount() {
        return count;
    }

    /**
     * Get the type filter.
     *
     * @return the type filter
     */
    public String getType() {
        return type;
    }
}
