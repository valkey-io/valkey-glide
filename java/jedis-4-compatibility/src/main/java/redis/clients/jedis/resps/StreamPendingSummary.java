/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Map;
import redis.clients.jedis.StreamEntryID;

/**
 * Stream pending summary compatibility class for Valkey GLIDE. Based on original Jedis
 * StreamPendingSummary.
 */
public class StreamPendingSummary {
    private final long total;
    private final StreamEntryID minId;
    private final StreamEntryID maxId;
    private final Map<String, Long> consumerMessageCount;

    public StreamPendingSummary(
            long total,
            StreamEntryID minId,
            StreamEntryID maxId,
            Map<String, Long> consumerMessageCount) {
        this.total = total;
        this.minId = minId;
        this.maxId = maxId;
        this.consumerMessageCount = consumerMessageCount;
    }

    public long getTotal() {
        return total;
    }

    public StreamEntryID getMinId() {
        return minId;
    }

    public StreamEntryID getMaxId() {
        return maxId;
    }

    public Map<String, Long> getConsumerMessageCount() {
        return consumerMessageCount;
    }
}
