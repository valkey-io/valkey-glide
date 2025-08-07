/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.Map;

/** AccessControlLogEntry compatibility stub for Valkey GLIDE wrapper. */
public class AccessControlLogEntry {
    private final long count;
    private final String reason;
    private final String context;
    private final String object;
    private final String username;
    private final String ageSeconds;
    private final Map<String, Object> clientInfo;
    private final long entryId;
    private final long timestampCreated;
    private final long timestampLastUpdated;

    public AccessControlLogEntry() {
        this.count = 0;
        this.reason = "";
        this.context = "";
        this.object = "";
        this.username = "";
        this.ageSeconds = "";
        this.clientInfo = Collections.emptyMap();
        this.entryId = 0;
        this.timestampCreated = 0;
        this.timestampLastUpdated = 0;
    }

    public long getCount() {
        return count;
    }

    public String getReason() {
        return reason;
    }

    public String getContext() {
        return context;
    }

    public String getObject() {
        return object;
    }

    public String getUsername() {
        return username;
    }

    public String getAgeSeconds() {
        return ageSeconds;
    }

    public Map<String, Object> getClientInfo() {
        return clientInfo;
    }

    public long getEntryId() {
        return entryId;
    }

    public long getTimestampCreated() {
        return timestampCreated;
    }

    public long getTimestampLastUpdated() {
        return timestampLastUpdated;
    }
}
