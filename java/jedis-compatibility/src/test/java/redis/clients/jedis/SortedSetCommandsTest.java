/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;
import redis.clients.jedis.resps.ScanResult;

/**
 * Integration tests for Jedis sorted set commands. These tests require a running Valkey/Redis
 * instance.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SortedSetCommandsTest {

    private static Jedis jedis;
    private static final String TEST_KEY = "test:sortedset";
    private static final String TEST_KEY_2 = "test:sortedset2";
    private static final String DEST_KEY = "test:sortedset:dest";

    @BeforeAll
    public static void setUp() {
        // These tests require a running Valkey/Redis instance
        // They will be skipped if no instance is available
        try {
            jedis = new Jedis("localhost", 6379);
            jedis.ping();
        } catch (Exception e) {
            // Connection failed - tests will be skipped
            jedis = null;
        }
    }

    @AfterAll
    public static void tearDown() {
        if (jedis != null) {
            try {
                jedis.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @BeforeEach
    public void cleanUp() {
        if (jedis != null) {
            try {
                jedis.del(TEST_KEY);
                jedis.del(TEST_KEY_2);
                jedis.del(DEST_KEY);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    @Order(1)
    public void testZaddSingleMember() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        long result = jedis.zadd(TEST_KEY, 1.0, "member1");
        assertEquals(1L, result);

        Double score = jedis.zscore(TEST_KEY, "member1");
        assertEquals(1.0, score);
    }

    @Test
    @Order(2)
    public void testZaddMultipleMembers() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);

        long result = jedis.zadd(TEST_KEY, members);
        assertEquals(3L, result);

        long cardinality = jedis.zcard(TEST_KEY);
        assertEquals(3L, cardinality);
    }

    @Test
    @Order(3)
    public void testZaddIncr() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        jedis.zadd(TEST_KEY, 1.0, "member1");

        double newScore = jedis.zaddIncr(TEST_KEY, 2.5, "member1");
        assertEquals(3.5, newScore, 0.001);

        Double score = jedis.zscore(TEST_KEY, "member1");
        assertEquals(3.5, score, 0.001);
    }

    @Test
    @Order(4)
    public void testZrem() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        jedis.zadd(TEST_KEY, members);

        long removed = jedis.zrem(TEST_KEY, "member1", "member2");
        assertEquals(2L, removed);

        long cardinality = jedis.zcard(TEST_KEY);
        assertEquals(1L, cardinality);
    }

    @Test
    @Order(5)
    public void testZcard() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        long emptyCard = jedis.zcard(TEST_KEY);
        assertEquals(0L, emptyCard);

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        jedis.zadd(TEST_KEY, members);

        long cardinality = jedis.zcard(TEST_KEY);
        assertEquals(2L, cardinality);
    }

    @Test
    @Order(6)
    public void testZscore() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        jedis.zadd(TEST_KEY, 5.5, "member1");

        Double score = jedis.zscore(TEST_KEY, "member1");
        assertEquals(5.5, score, 0.001);

        Double nonExistent = jedis.zscore(TEST_KEY, "nonexistent");
        assertNull(nonExistent);
    }

    @Test
    @Order(7)
    public void testZmscore() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        jedis.zadd(TEST_KEY, members);

        List<Double> scores = jedis.zmscore(TEST_KEY, "member1", "nonexistent", "member3");
        assertEquals(3, scores.size());
        assertEquals(1.0, scores.get(0), 0.001);
        assertNull(scores.get(1));
        assertEquals(3.0, scores.get(2), 0.001);
    }

    @Test
    @Order(8)
    public void testZrange() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        jedis.zadd(TEST_KEY, members);

        List<String> range = jedis.zrange(TEST_KEY, 0, -1);
        assertEquals(3, range.size());
        assertEquals("member1", range.get(0));
        assertEquals("member2", range.get(1));
        assertEquals("member3", range.get(2));

        List<String> partialRange = jedis.zrange(TEST_KEY, 0, 1);
        assertEquals(2, partialRange.size());
    }

    @Test
    @Order(9)
    public void testZrangeWithScores() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        jedis.zadd(TEST_KEY, members);

        Map<String, Double> rangeWithScores = jedis.zrangeWithScores(TEST_KEY, 0, -1);
        assertEquals(3, rangeWithScores.size());
        assertEquals(1.0, rangeWithScores.get("member1"), 0.001);
        assertEquals(2.0, rangeWithScores.get("member2"), 0.001);
        assertEquals(3.0, rangeWithScores.get("member3"), 0.001);
    }

    @Test
    @Order(10)
    public void testZrank() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        jedis.zadd(TEST_KEY, members);

        Long rank = jedis.zrank(TEST_KEY, "member2");
        assertEquals(1L, rank);

        Long nonExistent = jedis.zrank(TEST_KEY, "nonexistent");
        assertNull(nonExistent);
    }

    @Test
    @Order(11)
    public void testZrevrank() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        jedis.zadd(TEST_KEY, members);

        Long revrank = jedis.zrevrank(TEST_KEY, "member2");
        assertEquals(1L, revrank);

        Long nonExistent = jedis.zrevrank(TEST_KEY, "nonexistent");
        assertNull(nonExistent);
    }

    @Test
    @Order(12)
    public void testZcount() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        members.put("member4", 4.0);
        jedis.zadd(TEST_KEY, members);

        long count = jedis.zcount(TEST_KEY, 1.5, 3.5);
        assertEquals(2L, count);

        long allCount = jedis.zcount(TEST_KEY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        assertEquals(4L, allCount);
    }

    @Test
    @Order(13)
    public void testZincrby() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        jedis.zadd(TEST_KEY, 1.0, "member1");

        double newScore = jedis.zincrby(TEST_KEY, 2.5, "member1");
        assertEquals(3.5, newScore, 0.001);

        // Test incrementing non-existent member
        double newMemberScore = jedis.zincrby(TEST_KEY, 5.0, "member2");
        assertEquals(5.0, newMemberScore, 0.001);
    }

    @Test
    @Order(14)
    public void testZpopmin() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        jedis.zadd(TEST_KEY, members);

        Map<String, Double> popped = jedis.zpopmin(TEST_KEY, 2);
        assertEquals(2, popped.size());
        assertTrue(popped.containsKey("member1"));
        assertTrue(popped.containsKey("member2"));

        long remaining = jedis.zcard(TEST_KEY);
        assertEquals(1L, remaining);
    }

    @Test
    @Order(15)
    public void testZpopmax() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        jedis.zadd(TEST_KEY, members);

        Map<String, Double> popped = jedis.zpopmax(TEST_KEY, 2);
        assertEquals(2, popped.size());
        assertTrue(popped.containsKey("member3"));
        assertTrue(popped.containsKey("member2"));

        long remaining = jedis.zcard(TEST_KEY);
        assertEquals(1L, remaining);
    }

    @Test
    @Order(16)
    public void testZunionstore() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members1 = new HashMap<>();
        members1.put("member1", 1.0);
        members1.put("member2", 2.0);
        jedis.zadd(TEST_KEY, members1);

        Map<String, Double> members2 = new HashMap<>();
        members2.put("member2", 3.0);
        members2.put("member3", 4.0);
        jedis.zadd(TEST_KEY_2, members2);

        long result = jedis.zunionstore(DEST_KEY, TEST_KEY, TEST_KEY_2);
        assertEquals(3L, result);

        long cardinality = jedis.zcard(DEST_KEY);
        assertEquals(3L, cardinality);
    }

    @Test
    @Order(17)
    public void testZinterstore() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members1 = new HashMap<>();
        members1.put("member1", 1.0);
        members1.put("member2", 2.0);
        jedis.zadd(TEST_KEY, members1);

        Map<String, Double> members2 = new HashMap<>();
        members2.put("member2", 3.0);
        members2.put("member3", 4.0);
        jedis.zadd(TEST_KEY_2, members2);

        long result = jedis.zinterstore(DEST_KEY, TEST_KEY, TEST_KEY_2);
        assertEquals(1L, result);

        long cardinality = jedis.zcard(DEST_KEY);
        assertEquals(1L, cardinality);

        Double score = jedis.zscore(DEST_KEY, "member2");
        assertNotNull(score);
    }

    @Test
    @Order(18)
    public void testZremrangebyrank() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        members.put("member4", 4.0);
        jedis.zadd(TEST_KEY, members);

        long removed = jedis.zremrangebyrank(TEST_KEY, 0, 1);
        assertEquals(2L, removed);

        long remaining = jedis.zcard(TEST_KEY);
        assertEquals(2L, remaining);
    }

    @Test
    @Order(19)
    public void testZremrangebyscore() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        members.put("member4", 4.0);
        jedis.zadd(TEST_KEY, members);

        long removed = jedis.zremrangebyscore(TEST_KEY, 1.5, 3.5);
        assertEquals(2L, removed);

        long remaining = jedis.zcard(TEST_KEY);
        assertEquals(2L, remaining);
    }

    @Test
    @Order(20)
    public void testZscan() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        Map<String, Double> members = new HashMap<>();
        members.put("member1", 1.0);
        members.put("member2", 2.0);
        members.put("member3", 3.0);
        jedis.zadd(TEST_KEY, members);

        ScanResult<Map.Entry<String, Double>> result = jedis.zscan(TEST_KEY, "0");
        assertNotNull(result);
        assertNotNull(result.getCursor());
        assertNotNull(result.getResult());
        assertEquals(3, result.getResult().size());
    }

    @Test
    @Order(21)
    public void testBinaryZadd() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        byte[] key = "test:binary:sortedset".getBytes();
        byte[] member = "member1".getBytes();

        long result = jedis.zadd(key, 1.0, member);
        assertEquals(1L, result);

        Double score = jedis.zscore(key, member);
        assertEquals(1.0, score);

        // Cleanup
        jedis.del(key);
    }

    @Test
    @Order(22)
    public void testBinaryZrange() {
        Assumptions.assumeTrue(jedis != null, "Jedis connection not available");

        byte[] key = "test:binary:sortedset".getBytes();
        Map<byte[], Double> members = new HashMap<>();
        members.put("member1".getBytes(), 1.0);
        members.put("member2".getBytes(), 2.0);
        jedis.zadd(key, members);

        List<byte[]> range = jedis.zrange(key, 0, -1);
        assertEquals(2, range.size());

        // Cleanup
        jedis.del(key);
    }
}
