/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.params.MigrateParams;

/** Unit tests for Jedis 4.x param helpers (review-driven correctness). */
public class Jedis4ParamBehaviorTest {

    @Test
    public void migrateParams_copyAndReplaceAreIdempotent() {
        MigrateParams p = new MigrateParams().copy().copy().replace().replace();
        assertArrayEquals(new String[] {"COPY", "REPLACE"}, p.getParams());
    }

    @Test
    public void migrateParams_auth2ReplacesAuth() {
        MigrateParams p = new MigrateParams().auth("secret").auth2("user", "pw");
        assertArrayEquals(new String[] {"AUTH2", "user", "pw"}, p.getParams());
    }

    @Test
    public void migrateParams_authReplacesAuth2() {
        MigrateParams p = new MigrateParams().auth2("u", "p").auth("onlypass");
        assertArrayEquals(new String[] {"AUTH", "onlypass"}, p.getParams());
    }

    @Test
    public void clusterConnectionProvider_defensiveCopyAndPools() {
        HostAndPort n1 = new HostAndPort("127.0.0.1", 7000);
        HostAndPort n2 = new HostAndPort("127.0.0.1", 7001);
        Set<HostAndPort> nodes = new HashSet<>();
        nodes.add(n1);
        nodes.add(n2);
        ClusterConnectionProvider provider =
                new ClusterConnectionProvider(nodes, DefaultJedisClientConfig.builder().build());

        Set<HostAndPort> first = provider.getNodes();
        Set<HostAndPort> second = provider.getNodes();
        assertEquals(2, first.size());
        assertNotSame(first, second);
        assertEquals(first, second);

        assertEquals(2, provider.getClusterNodes().size());
        try (Connection c = provider.getClusterNodes().get("127.0.0.1:7000").getResource()) {
            assertNotNull(c);
            assertEquals("127.0.0.1", c.getHost());
            assertEquals(7000, c.getPort());
        }
    }
}
