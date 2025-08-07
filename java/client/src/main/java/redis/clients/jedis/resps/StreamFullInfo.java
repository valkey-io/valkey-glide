/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.List;
import redis.clients.jedis.StreamEntryID;

/** StreamFullInfo compatibility stub for Valkey GLIDE wrapper. */
public class StreamFullInfo {
    private final long length;
    private final long radixTreeKeys;
    private final long radixTreeNodes;
    private final List<StreamGroupFullInfo> groups;
    private final StreamEntryID lastGeneratedId;
    private final List<StreamEntry> entries;

    public StreamFullInfo() {
        this.length = 0;
        this.radixTreeKeys = 0;
        this.radixTreeNodes = 0;
        this.groups = Collections.emptyList();
        this.lastGeneratedId = new StreamEntryID(0, 0);
        this.entries = Collections.emptyList();
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

    public List<StreamGroupFullInfo> getGroups() {
        return groups;
    }

    public StreamEntryID getLastGeneratedId() {
        return lastGeneratedId;
    }

    public List<StreamEntry> getEntries() {
        return entries;
    }
}
