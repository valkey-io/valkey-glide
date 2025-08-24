/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.timeseries;

/** TSElement compatibility stub for Valkey GLIDE wrapper. */
public class TSElement {
    private final long timestamp;
    private final double value;

    public TSElement(long timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "TSElement{timestamp=" + timestamp + ", value=" + value + "}";
    }
}
