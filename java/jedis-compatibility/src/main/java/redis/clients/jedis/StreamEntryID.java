/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * StreamEntryID compatibility class for Valkey GLIDE wrapper. Represents a Redis stream entry ID.
 */
public class StreamEntryID {

    private final long time;
    private final long sequence;

    public StreamEntryID(long time, long sequence) {
        this.time = time;
        this.sequence = sequence;
    }

    public StreamEntryID(String id) {
        String[] parts = id.split("-");
        this.time = Long.parseLong(parts[0]);
        this.sequence = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
    }

    public long getTime() {
        return time;
    }

    public long getSequence() {
        return sequence;
    }

    @Override
    public String toString() {
        return time + "-" + sequence;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        StreamEntryID that = (StreamEntryID) obj;
        return time == that.time && sequence == that.sequence;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(time, sequence);
    }
}
