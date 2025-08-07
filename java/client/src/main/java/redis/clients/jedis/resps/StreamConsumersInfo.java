/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

/** StreamConsumersInfo compatibility stub for Valkey GLIDE wrapper. */
public class StreamConsumersInfo {
    private final String name;
    private final long idle;
    private final long pending;
    private final long inactive;

    public StreamConsumersInfo(String name) {
        this.name = name;
        this.idle = 0;
        this.pending = 0;
        this.inactive = 0;
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

    public long getInactive() {
        return inactive;
    }
}
