/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestConfiguration.STANDALONE_HOSTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.args.GeoUnit;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.GeoSearchParam;
import redis.clients.jedis.resps.GeoRadiusResponse;
import redis.clients.jedis.resps.Tuple;

/**
 * Integration tests covering every option a Jedis caller can set on {@link GeoSearchParam}, for the
 * three compatibility-layer methods that accept one: {@code geosearch}, {@code geosearchStore} and
 * {@code geosearchStoreStoreDist}, in both the {@code String} and {@code byte[]} forms.
 *
 * <p>The expected orders, distances, geohashes and coordinates below were captured from upstream
 * Jedis 5.2.0 making the same calls against the same data set, so a passing assertion here means
 * the compatibility layer agrees with upstream.
 *
 * <p>The searches start from Catania on purpose. Without a sort the server replies in sorted-set
 * score order, which for this data set is Palermo, edge1, Catania, edge2, and that differs from
 * every sorted order asserted below. A test that forgot to send ASC or DESC therefore fails instead
 * of passing by coincidence.
 */
public class JedisGeoSearchParamTest {

    private static final String valkeyHost;
    private static final int valkeyPort;

    // Sicily data set, the one upstream Jedis and the GLIDE client both use in their own geo tests.
    private static final double LON_PALERMO = 13.361389;
    private static final double LAT_PALERMO = 38.115556;
    private static final double LON_CATANIA = 15.087269;
    private static final double LAT_CATANIA = 37.502669;
    private static final double LON_EDGE1 = 12.758489;
    private static final double LAT_EDGE1 = 38.788135;
    private static final double LON_EDGE2 = 17.241510;
    private static final double LAT_EDGE2 = 38.788135;

    private static final double SEARCH_RADIUS_KM = 400;

    // Order of every member within 400km of Catania, nearest first and farthest first.
    private static final List<String> ASC_FROM_CATANIA =
            Arrays.asList("Catania", "Palermo", "edge2", "edge1");
    private static final List<String> DESC_FROM_CATANIA =
            Arrays.asList("edge1", "edge2", "Palermo", "Catania");

    // Distance in km from Catania, rounded the way the server rounds it for WITHDIST.
    private static final double DIST_CATANIA = 0.0;
    private static final double DIST_PALERMO = 166.2742;
    private static final double DIST_EDGE2 = 236.5292;
    private static final double DIST_EDGE1 = 248.8609;
    private static final double DIST_DELTA = 0.0001;

    private static final long HASH_CATANIA = 3479447370796909L;
    private static final long HASH_PALERMO = 3479099956230698L;
    private static final long HASH_EDGE1 = 3479273021651468L;
    private static final long HASH_EDGE2 = 3481342659049484L;

    // Coordinates as the server echoes them back, which differ from the stored values by the
    // precision of the geohash encoding.
    private static final double ECHO_LON_CATANIA = 15.087267458438873;
    private static final double ECHO_LAT_CATANIA = 37.50266842333162;
    private static final double ECHO_LON_PALERMO = 13.361389338970184;
    private static final double ECHO_LAT_PALERMO = 38.1155563954963;
    private static final double COORD_DELTA = 1e-12;

    // STOREDIST writes the distance from the center as the sorted set score, unrounded.
    private static final double SCORE_DIST_PALERMO = 166.27415156960032;
    private static final double SCORE_DELTA = 1e-9;

    private Jedis jedis;
    private String key;
    private String destKey;

    static {
        String[] standaloneHosts = STANDALONE_HOSTS;
        if (standaloneHosts.length == 0 || standaloneHosts[0].trim().isEmpty()) {
            throw new IllegalStateException(
                    "Standalone server configuration not found in system properties. "
                            + "Please set 'test.server.standalone' system property with server address "
                            + "(e.g., -Dtest.server.standalone=localhost:6379)");
        }

        String[] hostPort = standaloneHosts[0].trim().split(":");
        if (hostPort.length != 2) {
            throw new IllegalStateException(
                    "Invalid standalone server format: "
                            + standaloneHosts[0]
                            + ". Expected format: host:port (e.g., localhost:6379)");
        }
        valkeyHost = hostPort[0];
        valkeyPort = Integer.parseInt(hostPort[1]);
    }

    @BeforeEach
    void setup() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"),
                "GEOSEARCH requires Valkey 6.2.0 or higher");

        jedis = new Jedis(valkeyHost, valkeyPort);
        jedis.connect();

        String suffix = UUID.randomUUID().toString();
        key = "geoSearchParam_" + suffix;
        destKey = "geoSearchParamDest_" + suffix;

        jedis.geoadd(key, LON_PALERMO, LAT_PALERMO, "Palermo");
        jedis.geoadd(key, LON_CATANIA, LAT_CATANIA, "Catania");
        jedis.geoadd(key, LON_EDGE1, LAT_EDGE1, "edge1");
        jedis.geoadd(key, LON_EDGE2, LAT_EDGE2, "edge2");
    }

    @AfterEach
    void teardown() {
        if (jedis != null) {
            jedis.del(key);
            jedis.del(destKey);
            jedis.close();
        }
    }

    private byte[] keyBytes() {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] destKeyBytes() {
        return destKey.getBytes(StandardCharsets.UTF_8);
    }

    private GeoSearchParam fromCatania() {
        return GeoSearchParam.fromMember("Catania").byRadius(SEARCH_RADIUS_KM, GeoUnit.KM);
    }

    private List<String> members(List<GeoRadiusResponse> responses) {
        List<String> names = new ArrayList<>();
        for (GeoRadiusResponse response : responses) {
            names.add(response.getMemberByString());
        }
        return names;
    }

    private GeoRadiusResponse findMember(List<GeoRadiusResponse> responses, String member) {
        for (GeoRadiusResponse response : responses) {
            if (member.equals(response.getMemberByString())) {
                return response;
            }
        }
        throw new AssertionError("member " + member + " missing from " + members(responses));
    }

    // ==================== COUNT and ANY ====================

    @Test
    void geosearch_params_count_limits_results() {
        List<GeoRadiusResponse> results = jedis.geosearch(key, fromCatania().count(2));

        assertEquals(2, results.size(), "COUNT 2 should cap the reply at two members");
    }

    @Test
    void geosearch_params_count_limits_results_binary() {
        List<GeoRadiusResponse> results = jedis.geosearch(keyBytes(), fromCatania().count(2));

        assertEquals(2, results.size(), "COUNT 2 should cap the reply at two members");
    }

    @Test
    void geosearch_params_count_any_limits_results() {
        List<GeoRadiusResponse> results = jedis.geosearch(key, fromCatania().count(2, true));

        assertEquals(2, results.size(), "COUNT 2 ANY should cap the reply at two members");
    }

    @Test
    void geosearch_params_count_zero_is_rejected_like_upstream() {
        // Upstream Jedis forwards COUNT 0 and the server rejects it with "COUNT must be > 0".
        assertThrows(JedisDataException.class, () -> jedis.geosearch(key, fromCatania().count(0)));
    }

    @Test
    void geosearch_params_count_zero_is_rejected_like_upstream_binary() {
        assertThrows(
                JedisDataException.class, () -> jedis.geosearch(keyBytes(), fromCatania().count(0)));
    }

    // ==================== ASC and DESC ====================

    @Test
    void geosearch_params_asc_sorts_nearest_first() {
        List<GeoRadiusResponse> results = jedis.geosearch(key, fromCatania().asc());

        assertEquals(ASC_FROM_CATANIA, members(results));
    }

    @Test
    void geosearch_params_asc_sorts_nearest_first_binary() {
        List<GeoRadiusResponse> results = jedis.geosearch(keyBytes(), fromCatania().asc());

        assertEquals(ASC_FROM_CATANIA, members(results));
    }

    @Test
    void geosearch_params_desc_sorts_farthest_first() {
        List<GeoRadiusResponse> results = jedis.geosearch(key, fromCatania().desc());

        assertEquals(DESC_FROM_CATANIA, members(results));
    }

    @Test
    void geosearch_params_desc_sorts_farthest_first_binary() {
        List<GeoRadiusResponse> results = jedis.geosearch(keyBytes(), fromCatania().desc());

        assertEquals(DESC_FROM_CATANIA, members(results));
    }

    @Test
    void geosearch_params_asc_with_count_returns_the_nearest_ones() {
        List<GeoRadiusResponse> results = jedis.geosearch(key, fromCatania().asc().count(2));

        assertEquals(Arrays.asList("Catania", "Palermo"), members(results));
    }

    @Test
    void geosearch_params_desc_with_count_returns_the_farthest_ones() {
        List<GeoRadiusResponse> results = jedis.geosearch(key, fromCatania().desc().count(2));

        assertEquals(Arrays.asList("edge1", "edge2"), members(results));
    }

    // ==================== WITHCOORD, WITHDIST and WITHHASH ====================

    @Test
    void geosearch_params_with_dist_populates_distance() {
        List<GeoRadiusResponse> results = jedis.geosearch(key, fromCatania().asc().withDist());

        assertEquals(ASC_FROM_CATANIA, members(results));
        assertEquals(DIST_CATANIA, findMember(results, "Catania").getDistance(), DIST_DELTA);
        assertEquals(DIST_PALERMO, findMember(results, "Palermo").getDistance(), DIST_DELTA);
        assertEquals(DIST_EDGE2, findMember(results, "edge2").getDistance(), DIST_DELTA);
        assertEquals(DIST_EDGE1, findMember(results, "edge1").getDistance(), DIST_DELTA);

        // Upstream leaves the fields it did not ask for at their defaults.
        for (GeoRadiusResponse response : results) {
            assertEquals(0L, response.getRawScore(), "WITHHASH was not requested");
            assertNull(response.getCoordinate(), "WITHCOORD was not requested");
        }
    }

    @Test
    void geosearch_params_with_hash_populates_raw_score() {
        List<GeoRadiusResponse> results = jedis.geosearch(key, fromCatania().asc().withHash());

        assertEquals(ASC_FROM_CATANIA, members(results));
        assertEquals(HASH_CATANIA, findMember(results, "Catania").getRawScore());
        assertEquals(HASH_PALERMO, findMember(results, "Palermo").getRawScore());
        assertEquals(HASH_EDGE2, findMember(results, "edge2").getRawScore());
        assertEquals(HASH_EDGE1, findMember(results, "edge1").getRawScore());

        for (GeoRadiusResponse response : results) {
            assertEquals(0.0, response.getDistance(), DIST_DELTA, "WITHDIST was not requested");
            assertNull(response.getCoordinate(), "WITHCOORD was not requested");
        }
    }

    @Test
    void geosearch_params_with_coord_populates_coordinate() {
        List<GeoRadiusResponse> results = jedis.geosearch(key, fromCatania().asc().withCoord());

        assertEquals(ASC_FROM_CATANIA, members(results));

        GeoCoordinate catania = findMember(results, "Catania").getCoordinate();
        assertNotNull(catania);
        assertEquals(ECHO_LON_CATANIA, catania.getLongitude(), COORD_DELTA);
        assertEquals(ECHO_LAT_CATANIA, catania.getLatitude(), COORD_DELTA);

        GeoCoordinate palermo = findMember(results, "Palermo").getCoordinate();
        assertNotNull(palermo);
        assertEquals(ECHO_LON_PALERMO, palermo.getLongitude(), COORD_DELTA);
        assertEquals(ECHO_LAT_PALERMO, palermo.getLatitude(), COORD_DELTA);

        for (GeoRadiusResponse response : results) {
            assertEquals(0.0, response.getDistance(), DIST_DELTA, "WITHDIST was not requested");
            assertEquals(0L, response.getRawScore(), "WITHHASH was not requested");
        }
    }

    @Test
    void geosearch_params_with_all_three_populates_every_field() {
        List<GeoRadiusResponse> results =
                jedis.geosearch(key, fromCatania().asc().withCoord().withDist().withHash());

        assertEquals(ASC_FROM_CATANIA, members(results));
        assertEveryFieldPopulated(results);
    }

    @Test
    void geosearch_params_with_all_three_populates_every_field_binary() {
        List<GeoRadiusResponse> results =
                jedis.geosearch(keyBytes(), fromCatania().asc().withCoord().withDist().withHash());

        assertEquals(ASC_FROM_CATANIA, members(results));
        assertEveryFieldPopulated(results);
    }

    private void assertEveryFieldPopulated(List<GeoRadiusResponse> results) {
        GeoRadiusResponse catania = findMember(results, "Catania");
        assertEquals(DIST_CATANIA, catania.getDistance(), DIST_DELTA);
        assertEquals(HASH_CATANIA, catania.getRawScore());
        assertNotNull(catania.getCoordinate());
        assertEquals(ECHO_LON_CATANIA, catania.getCoordinate().getLongitude(), COORD_DELTA);
        assertEquals(ECHO_LAT_CATANIA, catania.getCoordinate().getLatitude(), COORD_DELTA);

        GeoRadiusResponse palermo = findMember(results, "Palermo");
        assertEquals(DIST_PALERMO, palermo.getDistance(), DIST_DELTA);
        assertEquals(HASH_PALERMO, palermo.getRawScore());
        assertNotNull(palermo.getCoordinate());
        assertEquals(ECHO_LON_PALERMO, palermo.getCoordinate().getLongitude(), COORD_DELTA);
        assertEquals(ECHO_LAT_PALERMO, palermo.getCoordinate().getLatitude(), COORD_DELTA);
    }

    // Without a sort or a count the WITH flags go through a different GLIDE overload, so that
    // combination gets its own coverage.
    @Test
    void geosearch_params_with_dist_without_sort_or_count_populates_distance() {
        List<GeoRadiusResponse> results = jedis.geosearch(key, fromCatania().withDist());

        assertEquals(4, results.size());
        assertEquals(DIST_CATANIA, findMember(results, "Catania").getDistance(), DIST_DELTA);
        assertEquals(DIST_PALERMO, findMember(results, "Palermo").getDistance(), DIST_DELTA);
        assertEquals(DIST_EDGE2, findMember(results, "edge2").getDistance(), DIST_DELTA);
        assertEquals(DIST_EDGE1, findMember(results, "edge1").getDistance(), DIST_DELTA);

        for (GeoRadiusResponse response : results) {
            assertEquals(0L, response.getRawScore(), "WITHHASH was not requested");
            assertNull(response.getCoordinate(), "WITHCOORD was not requested");
        }
    }

    @Test
    void geosearch_params_with_all_three_without_sort_or_count_populates_every_field_binary() {
        List<GeoRadiusResponse> results =
                jedis.geosearch(keyBytes(), fromCatania().withCoord().withDist().withHash());

        assertEquals(4, results.size());
        assertEveryFieldPopulated(results);
    }

    @Test
    void geosearch_params_combines_with_options_count_and_sort() {
        List<GeoRadiusResponse> results =
                jedis.geosearch(key, fromCatania().asc().count(2).withDist().withHash());

        assertEquals(Arrays.asList("Catania", "Palermo"), members(results));
        assertEquals(DIST_PALERMO, results.get(1).getDistance(), DIST_DELTA);
        assertEquals(HASH_PALERMO, results.get(1).getRawScore());
    }

    // ==================== BYBOX and FROMLONLAT ====================

    @Test
    void geosearch_params_by_box_from_coordinate_honors_count_and_sort() {
        List<GeoRadiusResponse> results =
                jedis.geosearch(
                        key, GeoSearchParam.fromLonLat(15, 37).byBox(400, 400, GeoUnit.KM).asc().count(2));

        assertEquals(Arrays.asList("Catania", "Palermo"), members(results));
    }

    @Test
    void geosearch_params_by_box_from_coordinate_honors_count_and_sort_binary() {
        List<GeoRadiusResponse> results =
                jedis.geosearch(
                        keyBytes(),
                        GeoSearchParam.fromLonLat(15, 37).byBox(400, 400, GeoUnit.KM).asc().count(2));

        assertEquals(Arrays.asList("Catania", "Palermo"), members(results));
    }

    // ==================== no options ====================

    @Test
    void geosearch_params_without_options_returns_members_only() {
        List<GeoRadiusResponse> results = jedis.geosearch(key, fromCatania());

        assertEquals(4, results.size());
        for (GeoRadiusResponse response : results) {
            assertEquals(0.0, response.getDistance(), DIST_DELTA);
            assertEquals(0L, response.getRawScore());
            assertNull(response.getCoordinate());
        }
    }

    // ==================== GEOSEARCHSTORE ====================

    @Test
    void geosearchStore_params_honors_count_and_sort() {
        long stored = jedis.geosearchStore(destKey, key, fromCatania().asc().count(2));

        assertEquals(2, stored, "ASC COUNT 2 should store only the two nearest members");
        assertEquals(Arrays.asList("Palermo", "Catania"), jedis.zrange(destKey, 0, -1));
    }

    @Test
    void geosearchStore_params_honors_count_and_sort_binary() {
        long stored = jedis.geosearchStore(destKeyBytes(), keyBytes(), fromCatania().asc().count(2));

        assertEquals(2, stored, "ASC COUNT 2 should store only the two nearest members");
        assertEquals(Arrays.asList("Palermo", "Catania"), jedis.zrange(destKey, 0, -1));
    }

    @Test
    void geosearchStore_params_honors_desc_with_count() {
        long stored = jedis.geosearchStore(destKey, key, fromCatania().desc().count(1));

        assertEquals(1, stored);
        assertEquals(Arrays.asList("edge1"), jedis.zrange(destKey, 0, -1));
    }

    @Test
    void geosearchStore_params_honors_desc_with_count_binary() {
        long stored = jedis.geosearchStore(destKeyBytes(), keyBytes(), fromCatania().desc().count(1));

        assertEquals(1, stored);
        assertEquals(Arrays.asList("edge1"), jedis.zrange(destKey, 0, -1));
    }

    @Test
    void geosearchStore_params_honors_count_any() {
        long stored = jedis.geosearchStore(destKey, key, fromCatania().count(2, true));

        assertEquals(2, stored, "COUNT 2 ANY should store only two members");
    }

    @Test
    void geosearchStore_params_stores_geohash_scores() {
        jedis.geosearchStore(destKey, key, fromCatania().asc().count(2));

        List<Tuple> stored = jedis.zrangeWithScores(destKey, 0, -1);
        assertEquals(2, stored.size());
        assertEquals("Palermo", stored.get(0).getElement());
        assertEquals((double) HASH_PALERMO, stored.get(0).getScore(), SCORE_DELTA);
    }

    // GEOSEARCHSTORE rejects WITHCOORD, WITHDIST and WITHHASH, so the store variants must not
    // forward them.
    @Test
    void geosearchStore_params_does_not_forward_the_with_options() {
        long stored =
                jedis.geosearchStore(
                        destKey, key, fromCatania().asc().count(2).withCoord().withDist().withHash());

        assertEquals(2, stored);
        assertEquals(Arrays.asList("Palermo", "Catania"), jedis.zrange(destKey, 0, -1));
    }

    // ==================== GEOSEARCHSTORE with STOREDIST ====================

    @Test
    void geosearchStoreStoreDist_params_honors_count_and_sort() {
        long stored = jedis.geosearchStoreStoreDist(destKey, key, fromCatania().asc().count(2));

        assertEquals(2, stored, "ASC COUNT 2 should store only the two nearest members");
        assertStoredDistances();
    }

    @Test
    void geosearchStoreStoreDist_params_honors_count_and_sort_binary() {
        long stored =
                jedis.geosearchStoreStoreDist(destKeyBytes(), keyBytes(), fromCatania().asc().count(2));

        assertEquals(2, stored, "ASC COUNT 2 should store only the two nearest members");
        assertStoredDistances();
    }

    private void assertStoredDistances() {
        List<Tuple> results = jedis.zrangeWithScores(destKey, 0, -1);
        assertEquals(2, results.size());
        assertEquals("Catania", results.get(0).getElement());
        assertEquals(DIST_CATANIA, results.get(0).getScore(), SCORE_DELTA);
        assertEquals("Palermo", results.get(1).getElement());
        assertEquals(SCORE_DIST_PALERMO, results.get(1).getScore(), SCORE_DELTA);
    }

    @Test
    void geosearchStoreStoreDist_params_honors_desc_with_count() {
        long stored = jedis.geosearchStoreStoreDist(destKey, key, fromCatania().desc().count(1));

        assertEquals(1, stored);
        assertEquals(Arrays.asList("edge1"), jedis.zrange(destKey, 0, -1));
    }

    @Test
    void geosearchStoreStoreDist_params_honors_count_any() {
        long stored = jedis.geosearchStoreStoreDist(destKey, key, fromCatania().count(2, true));

        assertEquals(2, stored, "COUNT 2 ANY should store only two members");
    }

    @Test
    void geosearchStoreStoreDist_params_does_not_forward_the_with_options() {
        long stored =
                jedis.geosearchStoreStoreDist(
                        destKey, key, fromCatania().asc().count(2).withCoord().withDist().withHash());

        assertEquals(2, stored);
        assertEquals(SCORE_DIST_PALERMO, jedis.zscore(destKey, "Palermo"), SCORE_DELTA);
    }
}
