/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/** Connection compatibility stub for Valkey GLIDE wrapper. */
public class Connection {

    public void close() {
        // Stub implementation
    }

    public boolean isConnected() {
        return true;
    }

    public String getHost() {
        return "localhost";
    }

    public int getPort() {
        return 6379;
    }
}
