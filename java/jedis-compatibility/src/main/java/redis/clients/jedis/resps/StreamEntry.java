/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Map;
import redis.clients.jedis.StreamEntryID;

/** StreamEntry compatibility class for Valkey GLIDE wrapper. Represents a Redis stream entry. */
public class StreamEntry {

    private final StreamEntryID id;
    private final Map<String, String> fields;

    public StreamEntry(StreamEntryID id, Map<String, String> fields) {
        this.id = id;
        this.fields = fields;
    }

    public StreamEntryID getID() {
        return id;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    @Override
    public String toString() {
        return "StreamEntry{id=" + id + ", fields=" + fields + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        StreamEntry that = (StreamEntry) obj;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, fields);
    }
}
