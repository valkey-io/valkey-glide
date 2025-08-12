/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.search;

import java.util.Map;
import java.util.Set;

/** Document compatibility stub for Valkey GLIDE wrapper. Represents a search document. */
public class Document {

    private final String id;
    private final Map<String, Object> properties;

    public Document(String id, Map<String, Object> properties) {
        this.id = id;
        this.properties = properties;
    }

    public String getId() {
        return id;
    }

    public Set<Map.Entry<String, Object>> getProperties() {
        return properties.entrySet();
    }

    public double getScore() {
        return 0.0;
    }

    @Override
    public String toString() {
        return "Document{id='" + id + "', properties=" + properties + "}";
    }
}
