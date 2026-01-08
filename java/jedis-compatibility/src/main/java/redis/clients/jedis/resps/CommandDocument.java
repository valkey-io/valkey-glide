/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandDocument {

    private static final String SUMMARY_STR = "summary";
    private static final String SINCE_STR = "since";
    private static final String GROUP_STR = "group";
    private static final String COMPLEXITY_STR = "complexity";
    private static final String HISTORY_STR = "history";

    private final String summary;
    private final String since;
    private final String group;
    private final String complexity;
    private final List<String> history;
    private final Map<String, Object> document;

    @SuppressWarnings("unchecked")
    public CommandDocument(Map<String, Object> map) {
        this.document = map;
        this.summary = (String) map.get(SUMMARY_STR);
        this.since = (String) map.get(SINCE_STR);
        this.group = (String) map.get(GROUP_STR);
        this.complexity = (String) map.get(COMPLEXITY_STR);

        List<Object> historyObject = (List<Object>) map.get(HISTORY_STR);
        if (historyObject == null) {
            this.history = null;
        } else if (historyObject.isEmpty()) {
            this.history = Collections.emptyList();
        } else {
            // Handle different formats of history data
            this.history =
                    historyObject.stream()
                            .map(
                                    o -> {
                                        if (o instanceof List) {
                                            List<Object> l = (List<Object>) o;
                                            return l.get(0) + ": " + l.get(1);
                                        } else {
                                            return o.toString();
                                        }
                                    })
                            .collect(Collectors.toList());
        }
    }

    public Map<String, Object> getDocument() {
        return document;
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
