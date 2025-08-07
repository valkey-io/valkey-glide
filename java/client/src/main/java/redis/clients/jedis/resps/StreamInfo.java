/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import redis.clients.jedis.StreamEntryID;

/** StreamInfo compatibility stub for Valkey GLIDE wrapper. */
public class StreamInfo {
    private final long length;
    private final long radixTreeKeys;
    private final long radixTreeNodes;
    private final long groups;
    private final StreamEntryID lastGeneratedId;
    private final StreamEntry firstEntry;
    private final StreamEntry lastEntry;

    public StreamInfo() {
        this.length = 0;
        this.radixTreeKeys = 0;
        this.radixTreeNodes = 0;
        this.groups = 0;
        this.lastGeneratedId = new StreamEntryID(0, 0);
        this.firstEntry = null;
        this.lastEntry = null;
    }

    public long getLength() {
        return length;
    }

    public long getRadixTreeKeys() {
        return radixTreeKeys;
    }

    public long getRadixTreeNodes() {
        return radixTreeNodes;
    }

    public long getGroups() {
        return groups;
    }

    public StreamEntryID getLastGeneratedId() {
        return lastGeneratedId;
    }

    public StreamEntry getFirstEntry() {
        return firstEntry;
    }

    public StreamEntry getLastEntry() {
        return lastEntry;
    }
}
