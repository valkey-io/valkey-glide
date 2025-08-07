/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.List;

/** CommandDocument compatibility stub for Valkey GLIDE wrapper. */
public class CommandDocument {
    private final String summary;
    private final String since;
    private final String group;
    private final String complexity;
    private final List<String> history;

    public CommandDocument() {
        this.summary = "";
        this.since = "";
        this.group = "";
        this.complexity = "";
        this.history = Collections.emptyList();
    }

    public String getSummary() {
        return summary;
    }

    public String getSince() {
        return since;
    }

    public String getGroup() {
        return group;
    }

    public String getComplexity() {
        return complexity;
    }

    public List<String> getHistory() {
        return history;
    }
}
