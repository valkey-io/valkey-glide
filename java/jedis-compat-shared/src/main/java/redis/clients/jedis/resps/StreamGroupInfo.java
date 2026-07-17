/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.io.Serializable;
import java.util.Map;
import redis.clients.jedis.StreamEntryID;

/**
 * Stream group information compatibility class for Valkey GLIDE. Based on original Jedis
 * StreamGroupInfo.
 */
public class StreamGroupInfo implements Serializable {

    public static final String NAME = "name";
    public static final String CONSUMERS = "consumers";
    public static final String PENDING = "pending";
    public static final String LAST_DELIVERED = "last-delivered-id";

    private final String name;
    private final long consumers;
    private final long pending;
    private final StreamEntryID lastDeliveredId;
    private final Map<String, Object> groupInfo;

    public StreamGroupInfo(Map<String, Object> map) {
        groupInfo = map;
        name = (String) map.get(NAME);
        consumers = (long) map.get(CONSUMERS);
        pending = (long) map.get(PENDING);
        lastDeliveredId = (StreamEntryID) map.get(LAST_DELIVERED);
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

    public Map<String, Object> getGroupInfo() {
        return groupInfo;
    }
}
