/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import redis.clients.jedis.util.Pool;

/** ConnectionPool compatibility stub for Valkey GLIDE wrapper. */
public class ConnectionPool extends Pool<Connection> {

    public ConnectionPool() {
        // Initialize with a dummy factory since this is just a stub
        initPool(new DummyConnectionFactory());
    }

    private static class DummyConnectionFactory
            implements org.apache.commons.pool2.PooledObjectFactory<Connection> {
        @Override
        public org.apache.commons.pool2.PooledObject<Connection> makeObject() throws Exception {
            return new org.apache.commons.pool2.impl.DefaultPooledObject<>(new Connection());
        }

        @Override
        public void destroyObject(org.apache.commons.pool2.PooledObject<Connection> p)
                throws Exception {
            // No-op for stub
        }

        @Override
        public boolean validateObject(org.apache.commons.pool2.PooledObject<Connection> p) {
            return true;
        }

        @Override
        public void activateObject(org.apache.commons.pool2.PooledObject<Connection> p)
                throws Exception {
            // No-op for stub
        }

        @Override
        public void passivateObject(org.apache.commons.pool2.PooledObject<Connection> p)
                throws Exception {
            // No-op for stub
        }
    }
}
