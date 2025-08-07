/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import redis.clients.jedis.StreamEntryID;

/** StreamGroupInfo compatibility stub for Valkey GLIDE wrapper. */
public class StreamGroupInfo {
    private final String name;
    private final long consumers;
    private final long pending;
    private final StreamEntryID lastDeliveredId;

    public StreamGroupInfo(String name) {
        this.name = name;
        this.consumers = 0;
        this.pending = 0;
        this.lastDeliveredId = new StreamEntryID(0, 0);
    }

    public String getName() {
        return name;
    }

    public long getConsumers() {
        return consumers;
    }

    public long getPending() {
        return pending;
    }

    public StreamEntryID getLastDeliveredId() {
        return lastDeliveredId;
    }
}
