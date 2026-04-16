/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.List;
import redis.clients.jedis.StreamEntryID;

/** StreamGroupFullInfo compatibility stub for Valkey GLIDE wrapper. */
public class StreamGroupFullInfo {
    private final String name;
    private final List<StreamConsumerFullInfo> consumers;
    private final List<Object> pending;
    private final long pelCount;
    private final StreamEntryID lastDeliveredId;

    public StreamGroupFullInfo(String name) {
        this.name = name;
        this.consumers = Collections.emptyList();
        this.pending = Collections.emptyList();
        this.pelCount = 0;
        this.lastDeliveredId = new StreamEntryID(0, 0);
    }

    public String getName() {
        return name;
    }

    public List<StreamConsumerFullInfo> getConsumers() {
        return consumers;
    }

    public List<Object> getPending() {
        return pending;
    }

    public long getPelCount() {
        return pelCount;
    }

    public StreamEntryID getLastDeliveredId() {
        return lastDeliveredId;
    }
}
