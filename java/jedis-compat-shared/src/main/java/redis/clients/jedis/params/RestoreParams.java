/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.ArrayList;
import java.util.List;

/** Parameters for RESTORE command. */
public class RestoreParams {
    private List<String> params = new ArrayList<>();

    public RestoreParams() {}

    /** Replace existing key. */
    public RestoreParams replace() {
        params.add("REPLACE");
        return this;
    }

    /** Don't set TTL if key already exists. */
    public RestoreParams absTtl() {
        params.add("ABSTTL");
        return this;
    }

    /** Set idle time. */
    public RestoreParams idleTime(long seconds) {
        params.add("IDLETIME");
        params.add(String.valueOf(seconds));
        return this;
    }

    /** Set frequency. */
    public RestoreParams freq(int frequency) {
        params.add("FREQ");
        params.add(String.valueOf(frequency));
        return this;
    }

    /** Get the parameters as a string array. */
    public String[] getParams() {
        return params.toArray(new String[0]);
    }
}
