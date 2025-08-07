/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Map;

/**
 * Command document compatibility class for Valkey GLIDE. Based on original Jedis CommandDocument.
 */
public class CommandDocument {
    private final Map<String, Object> document;

    public CommandDocument(Map<String, Object> map) {
        this.document = map;
    }

    public Map<String, Object> getDocument() {
        return document;
    }

    public String getSummary() {
        return (String) document.get("summary");
    }

    public String getSince() {
        return (String) document.get("since");
    }

    public String getGroup() {
        return (String) document.get("group");
    }
}
