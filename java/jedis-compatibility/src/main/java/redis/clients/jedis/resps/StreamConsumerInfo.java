/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Map;

/**
 * Stream consumer information compatibility class for Valkey GLIDE. Based on original Jedis
 * StreamConsumerInfo.
 */
public class StreamConsumerInfo {

    public static final String NAME = "name";
    public static final String IDLE = "idle";
    public static final String PENDING = "pending";
    public static final String INACTIVE = "inactive";

    private final String name;
    private final long idle;
    private final long pending;
    private final Long inactive;
    private final Map<String, Object> consumerInfo;

    public StreamConsumerInfo(Map<String, Object> map) {
        consumerInfo = map;
        name = (String) map.get(NAME);
        idle = (Long) map.get(IDLE);
        pending = (Long) map.get(PENDING);
        inactive = (Long) map.get(INACTIVE);
    }

    public String getName() {
        return name;
    }

    public long getIdle() {
        return idle;
    }

    public long getPending() {
        return pending;
    }

    public Long getInactive() {
        return inactive;
    }

    public Map<String, Object> getConsumerInfo() {
        return consumerInfo;
    }
}
