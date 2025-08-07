/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.Map;

/** FunctionStats compatibility stub for Valkey GLIDE wrapper. */
public class FunctionStats {
    private final Map<String, Object> runningScript;
    private final Map<String, Object> engines;

    public FunctionStats() {
        this.runningScript = Collections.emptyMap();
        this.engines = Collections.emptyMap();
    }

    public Map<String, Object> getRunningScript() {
        return runningScript;
    }

    public Map<String, Object> getEngines() {
        return engines;
    }
}
