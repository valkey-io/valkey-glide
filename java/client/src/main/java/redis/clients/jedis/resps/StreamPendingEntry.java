/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import redis.clients.jedis.StreamEntryID;

/** StreamPendingEntry compatibility stub for Valkey GLIDE wrapper. */
public class StreamPendingEntry {
    private final StreamEntryID id;
    private final String consumerName;
    private final long idleTime;
    private final long deliveredTimes;

    public StreamPendingEntry() {
        this.id = new StreamEntryID(0, 0);
        this.consumerName = "";
        this.idleTime = 0;
        this.deliveredTimes = 0;
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
