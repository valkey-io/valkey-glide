/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.BitPosParams;
import redis.clients.jedis.params.MigrateParams;
import redis.clients.jedis.params.SortingParams;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XTrimParams;

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
    public void bitPosParamsConstructorsSetStartAndEnd() {
        assertEquals(Long.valueOf(2L), new BitPosParams(2L).getStart());
        assertNull(new BitPosParams(2L).getEnd());
        BitPosParams r = new BitPosParams(0, 10);
        assertEquals(Long.valueOf(0L), r.getStart());
        assertEquals(Long.valueOf(10L), r.getEnd());
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

    @Test
    public void clusterConnectionProvider_rejectsEmptyNodes() {
        assertThrows(
                JedisException.class,
                () ->
                        new ClusterConnectionProvider(
                                Collections.emptySet(), DefaultJedisClientConfig.builder().build()));
    }

    @Test
    public void clusterNodePoolStub_usesHostPortKeyAndReturnsConnection() {
        HostAndPort node = new HostAndPort("127.0.0.1", 7000);
        java.util.Map<String, ConnectionPool> nodeMap = new java.util.LinkedHashMap<>();
        nodeMap.put(node.toString(), new ConnectionPool(node));

        assertNotNull(nodeMap.get("127.0.0.1:7000"));
        try (Connection c = nodeMap.get("127.0.0.1:7000").getResource()) {
            assertNotNull(c);
            assertEquals("127.0.0.1", c.getHost());
            assertEquals(7000, c.getPort());
        }
    }

    @Test
    public void clusterConnectionProvider_rejectsNullNodes() {
        assertThrows(
                NullPointerException.class,
                () -> new ClusterConnectionProvider(null, DefaultJedisClientConfig.builder().build()));
    }

    @Test
    public void xAddParams_documentsTrimPriorityViaConversion() {
        XAddParams p = XAddParams.xAddParams().maxLen(50).minId("1-0").limit(5);
        assertEquals(Long.valueOf(50), p.getMaxLen());
        assertEquals("1-0", p.getMinId());
        assertEquals(Long.valueOf(5), p.getLimit());
        assertNotNull(p.toStreamAddOptions());
    }

    @Test
    public void xTrimParams_sharesTrimConversionWithXAdd() {
        XTrimParams trim = XTrimParams.xTrimParams().maxLen(100).limit(3);
        assertNotNull(trim.toStreamTrimOptions());
    }

    @Test
    public void sortingParams_orderAlphaAndLimitAreIdempotent() {
        SortingParams p = new SortingParams().desc().desc().alpha().alpha().limit(0, 2).limit(0, 2);
        assertArrayEquals(new String[] {"LIMIT", "0", "2", "DESC", "ALPHA"}, p.getParams());
    }

    @Test
    public void sortingParams_ascAndDescLastCallWins() {
        SortingParams ascThenDesc = new SortingParams().asc().desc();
        assertArrayEquals(new String[] {"DESC"}, ascThenDesc.getParams());

        SortingParams descThenAsc = new SortingParams().desc().asc();
        assertArrayEquals(new String[] {"ASC"}, descThenAsc.getParams());
    }

    @Test
    public void sortingParams_byReplacesPreviousGetPatternsAccumulate() {
        SortingParams p =
                new SortingParams().by("weight-*").by("score-*").get("#").get("field-*").get("#");
        assertArrayEquals(
                new String[] {"BY", "score-*", "GET", "#", "GET", "field-*", "GET", "#"}, p.getParams());
    }
}
