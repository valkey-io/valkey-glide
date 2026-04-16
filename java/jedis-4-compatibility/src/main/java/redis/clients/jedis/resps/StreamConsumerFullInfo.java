/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.List;

/** StreamConsumerFullInfo compatibility stub for Valkey GLIDE wrapper. */
public class StreamConsumerFullInfo {
    private final String name;
    private final long seenTime;
    private final long activeTime;
    private final long pelCount;
    private final List<Object> pending;

    public StreamConsumerFullInfo(String name) {
        this.name = name;
        this.seenTime = 0;
        this.activeTime = 0;
        this.pelCount = 0;
        this.pending = Collections.emptyList();
    }

    public String getName() {
        return name;
    }

    public long getSeenTime() {
        return seenTime;
    }

    public long getActiveTime() {
        return activeTime;
    }

    public long getPelCount() {
        return pelCount;
    }

    public List<Object> getPending() {
        return pending;
    }
}
