/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/** SingleConnectionPoolConfig compatibility stub for Valkey GLIDE wrapper. */
public class SingleConnectionPoolConfig {

    public SingleConnectionPoolConfig() {
        // Default configuration
    }

    public int getMaxTotal() {
        return 1;
    }

    public int getMaxIdle() {
        return 1;
    }

    public int getMinIdle() {
        return 0;
    }
}
