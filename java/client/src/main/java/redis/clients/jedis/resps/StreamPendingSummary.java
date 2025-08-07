/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.Map;
import redis.clients.jedis.StreamEntryID;

/** StreamPendingSummary compatibility stub for Valkey GLIDE wrapper. */
public class StreamPendingSummary {
    private final long total;
    private final StreamEntryID minId;
    private final StreamEntryID maxId;
    private final Map<String, Long> consumerMessageCount;

    public StreamPendingSummary() {
        this.total = 0;
        this.minId = new StreamEntryID(0, 0);
        this.maxId = new StreamEntryID(0, 0);
        this.consumerMessageCount = Collections.emptyMap();
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
