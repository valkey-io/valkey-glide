/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * ConnectionPoolConfig compatibility stub for Valkey GLIDE wrapper. This class exists only for
 * compilation compatibility.
 *
 * <p>NOTE: Connection pooling is not used in GLIDE compatibility layer.
 */
public class ConnectionPoolConfig {

    // Basic configuration properties for compilation compatibility
    private int maxTotal = 8;
    private int maxIdle = 8;
    private int minIdle = 0;

    public ConnectionPoolConfig() {
        // Default constructor
    }

    public int getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    public int getMaxIdle() {
        return maxIdle;
    }

    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }
}
