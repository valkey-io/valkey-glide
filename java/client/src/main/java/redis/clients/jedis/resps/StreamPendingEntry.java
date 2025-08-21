/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import redis.clients.jedis.StreamEntryID;

/**
 * Stream pending entry compatibility class for Valkey GLIDE. Based on original Jedis
 * StreamPendingEntry.
 */
public class StreamPendingEntry {
    private final StreamEntryID id;
    private final String consumerName;
    private final long idleTime;
    private final long deliveredTimes;

    public StreamPendingEntry(
            StreamEntryID id, String consumerName, long idleTime, long deliveredTimes) {
        this.id = id;
        this.consumerName = consumerName;
        this.idleTime = idleTime;
        this.deliveredTimes = deliveredTimes;
    }

    public StreamEntryID getID() {
        return id;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public long getIdleTime() {
        return idleTime;
    }

    public long getDeliveredTimes() {
        return deliveredTimes;
    }
}
