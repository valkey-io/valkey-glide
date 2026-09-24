/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.ArrayList;
import java.util.List;

/** Parameters for RESTORE command. */
public abstract class AbstractRestoreParams<T extends AbstractRestoreParams<T>> {

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    private List<String> params = new ArrayList<>();

    protected AbstractRestoreParams() {}

    /**
     * Replace existing key.
     *
     * @return this instance, for chaining
     */
    public T replace() {
        params.add("REPLACE");
        return self();
    }

    /**
     * Don't set TTL if key already exists.
     *
     * @return this instance, for chaining
     */
    public T absTtl() {
        params.add("ABSTTL");
        return self();
    }

    /**
     * Set idle time.
     *
     * @param seconds the idle time, in seconds, to record for the restored key
     * @return this instance, for chaining
     */
    public T idleTime(long seconds) {
        params.add("IDLETIME");
        params.add(String.valueOf(seconds));
        return self();
    }

    /**
     * Set frequency.
     *
     * @param frequency the LFU access frequency to record for the restored key
     * @return this instance, for chaining
     */
    public T freq(int frequency) {
        params.add("FREQ");
        params.add(String.valueOf(frequency));
        return self();
    }

    /**
     * Get the parameters as a string array.
     *
     * @return the configured options as RESTORE command arguments
     */
    public String[] getParams() {
        return params.toArray(new String[0]);
    }
}
