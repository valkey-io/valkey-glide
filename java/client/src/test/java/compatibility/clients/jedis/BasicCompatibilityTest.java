/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Basic compatibility tests for the 4 Jedis client wrappers. These tests verify that basic GET/SET
 * operations work.
 */
public class BasicCompatibilityTest {

    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 6379;
    private static final String TEST_KEY = "test:key";
    private static final String TEST_VALUE = "test:value";

    @Test
    public void testJedisBasicOperations() {
        try (Jedis jedis = new Jedis(TEST_HOST, TEST_PORT)) {
            // Test SET
            String setResult = jedis.set(TEST_KEY, TEST_VALUE);
            assertEquals("OK", setResult);

            // Test GET
            String getValue = jedis.get(TEST_KEY);
            assertEquals(TEST_VALUE, getValue);

            // Test PING
            String pingResult = jedis.ping();
            assertEquals("PONG", pingResult);
        }
    }

    @Test
    public void testJedisPoolBasicOperations() {
        try (JedisPool pool = new JedisPool(TEST_HOST, TEST_PORT)) {
            try (Jedis jedis = pool.getResource()) {
                // Test SET
                String setResult = jedis.set(TEST_KEY, TEST_VALUE);
                assertEquals("OK", setResult);

                // Test GET
                String getValue = jedis.get(TEST_KEY);
                assertEquals(TEST_VALUE, getValue);

                // Test PING
                String pingResult = jedis.ping();
                assertEquals("PONG", pingResult);
            }
        }
    }

    @Test
    public void testJedisPooledBasicOperations() {
        try (JedisPooled jedisPooled = new JedisPooled(TEST_HOST, TEST_PORT)) {
            // Test SET
            String setResult = jedisPooled.set(TEST_KEY, TEST_VALUE);
            assertEquals("OK", setResult);

            // Test GET
            String getValue = jedisPooled.get(TEST_KEY);
            assertEquals(TEST_VALUE, getValue);

            // Test PING
            String pingResult = jedisPooled.ping();
            assertEquals("PONG", pingResult);
        }
    }

    @Test
    public void testUnifiedJedisBasicOperations() {
        try (UnifiedJedis unifiedJedis = new UnifiedJedis(TEST_HOST, TEST_PORT)) {
            // Test SET
            String setResult = unifiedJedis.set(TEST_KEY, TEST_VALUE);
            assertEquals("OK", setResult);

            // Test GET
            String getValue = unifiedJedis.get(TEST_KEY);
            assertEquals(TEST_VALUE, getValue);

            // Test PING
            String pingResult = unifiedJedis.ping();
            assertEquals("PONG", pingResult);
        }
    }

    // Note: JedisCluster test would require a cluster setup
    // @Test
    // public void testJedisClusterBasicOperations() {
    //     Set<HostAndPort> nodes = Set.of(new HostAndPort(TEST_HOST, 7000));
    //     try (JedisCluster cluster = new JedisCluster(nodes)) {
    //         String setResult = cluster.set(TEST_KEY, TEST_VALUE);
    //         assertEquals("OK", setResult);
    //
    //         String getValue = cluster.get(TEST_KEY);
    //         assertEquals(TEST_VALUE, getValue);
    //     }
    // }
}
