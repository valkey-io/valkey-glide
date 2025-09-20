/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class JedisCompatibilityTest {

    @Test
    public void testProtocolCommandExists() {
        // Test that Protocol.Command enum is accessible
        assertNotNull(Protocol.Command.SET);
        assertNotNull(Protocol.Command.GET);
        assertEquals("SET", Protocol.Command.SET.name());
        assertEquals("GET", Protocol.Command.GET.name());
    }

    @Test
    public void testHostAndPortCreation() {
        HostAndPort hostAndPort = new HostAndPort("localhost", 6379);
        assertEquals("localhost", hostAndPort.getHost());
        assertEquals(6379, hostAndPort.getPort());
    }

    @Test
    public void testDefaultJedisClientConfigBuilder() {
        JedisClientConfig config =
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(5000)
                        .socketTimeoutMillis(5000)
                        .database(0)
                        .build();

        assertNotNull(config);
        assertEquals(5000, config.getConnectionTimeoutMillis());
        assertEquals(5000, config.getSocketTimeoutMillis());
        assertEquals(0, config.getDatabase());
    }
}
