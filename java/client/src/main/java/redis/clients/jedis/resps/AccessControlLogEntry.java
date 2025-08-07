/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Map;

/**
 * Access control log entry compatibility class for Valkey GLIDE. Based on original Jedis
 * AccessControlLogEntry.
 */
public class AccessControlLogEntry {
    private final Map<String, Object> logEntry;

    public AccessControlLogEntry(Map<String, Object> map) {
        this.logEntry = map;
    }

    public Map<String, Object> getLogEntry() {
        return logEntry;
    }

    public long getCount() {
        return (Long) logEntry.get("count");
    }

    public String getReason() {
        return (String) logEntry.get("reason");
    }

    public String getContext() {
        return (String) logEntry.get("context");
    }

    public String getObject() {
        return (String) logEntry.get("object");
    }

    public String getUsername() {
        return (String) logEntry.get("username");
    }

    public double getAgeSeconds() {
        return (Double) logEntry.get("age-seconds");
    }

    public String getClientInfo() {
        return (String) logEntry.get("client-info");
    }
}
