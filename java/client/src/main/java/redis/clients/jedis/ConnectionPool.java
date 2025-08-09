/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import redis.clients.jedis.util.Pool;

/** ConnectionPool compatibility stub for Valkey GLIDE wrapper. */
public class ConnectionPool extends Pool<Connection> {

    @Override
    public Connection getResource() {
        return new Connection();
    }

    @Override
    public void returnResource(Connection resource) {
        // Stub implementation
    }

    @Override
    public void returnBrokenResource(Connection resource) {
        // Stub implementation
    }

    @Override
    public void destroy() {
        // Stub implementation
    }

    @Override
    public boolean isClosed() {
        return false;
    }
}
