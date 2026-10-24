/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.utils.ArrayTransformUtils.castArray;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;

import glide.TestConfiguration;
import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.commands.servermodules.FT;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.FT.FTAggregateOptions;
import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTCreateOptions.DataType;
import glide.api.models.commands.FT.FTCreateOptions.DistanceMetric;
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
import glide.api.models.commands.FT.FTCreateOptions.NumericField;
import glide.api.models.commands.FT.FTCreateOptions.TextField;
import glide.api.models.commands.FT.FTCreateOptions.VectorFieldFlat;
import glide.api.models.commands.FT.FTCreateOptions.VectorType;
import glide.api.models.commands.FT.FTHybridCombine;
import glide.api.models.commands.FT.FTHybridOptions;
import glide.api.models.commands.FT.FTHybridSearchArm;
import glide.api.models.commands.FT.FTHybridVsimArm;
import glide.api.models.commands.FT.FTHybridVsimFilter;
import glide.api.models.commands.FT.FTSearchOptions;
import glide.api.models.commands.FT.FTWithCursor;
import glide.api.models.commands.FT.HybridFilterPolicy;
import glide.api.models.commands.FlushMode;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


/**
 * Live tests for the valkey-search 1.3 stable surface: INKEYS, FLOAT16/BFLOAT16 vector types,
 * FT.CREATE FILTER/SCORE_FIELD/NOHL, per-field UNF, and count's LIMIT 0 0 serialization.
 * WITHSCORES/SCORER, ADDSCORES, WITHCURSOR/FT.CURSOR, FT.HYBRID and LoadField aliases are not
 * covered here.
 *
 * <p>Carries its own 1.3 capability probe (real command + a 1.3-only argument, classified by error
 * text, never a version string), unlike {@link VectorSearchTests}, which has none: that file's
 * tests all target the 1.2 surface and don't need to tell a pre-1.3 module apart from a missing
 * one. Mirrors the Node and Python references' probe.
 */
public class VectorSearch13Tests {

    private static final List<Arguments> clients = new ArrayList<>();
    private static boolean capability;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        GlideClusterClient clusterClient =
                GlideClusterClient.createClient(commonClusterClientConfig().requestTimeout(5000).build())
                        .get();
        clusterClient.flushall(FlushMode.SYNC, SimpleMultiNodeRoute.ALL_PRIMARIES).get();
        clients.add(Arguments.of(named("cluster", clusterClient)));

        if (!TestConfiguration.STANDALONE_HOSTS[0].isEmpty()) {
            GlideClient standaloneClient =
                    GlideClient.createClient(commonClientConfig().requestTimeout(5000).build()).get();
            standaloneClient.flushall(FlushMode.SYNC).get();
            clients.add(Arguments.of(named("standalone", standaloneClient)));
        }

        capability = probeSearch13Capability(clusterClient);
    }

    @AfterAll
    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static void teardown() {
        for (Arguments args : clients) {
            BaseClient client = ((Named<BaseClient>) args.get()[0]).getPayload();
            if (client instanceof GlideClusterClient) {
                ((GlideClusterClient) client)
                        .flushall(FlushMode.SYNC, SimpleMultiNodeRoute.ALL_PRIMARIES)
                        .get();
            } else {
                ((GlideClient) client).flushall(FlushMode.SYNC).get();
            }
            client.close();
        }
    }

    @AfterEach
    @SneakyThrows
    @SuppressWarnings("unchecked")
    public void cleanup() {
        for (Arguments args : clients) {
            BaseClient client = ((Named<BaseClient>) args.get()[0]).getPayload();
            if (client instanceof GlideClusterClient) {
                ((GlideClusterClient) client)
                        .flushall(FlushMode.SYNC, SimpleMultiNodeRoute.ALL_PRIMARIES)
                        .get();
            } else {
                ((GlideClient) client).flushall(FlushMode.SYNC).get();
            }
        }
    }

    static Stream<Arguments> getClients() {
        return clients.stream();
    }

    /**
     * Classifies the INKEYS probe's error text (equivalent to Python's classify_inkeys_probe_error /
     * Node's classifyInkeysProbeError).
     */
    static boolean classifyInkeysProbeError(String msg) {
        return msg.contains("Unexpected") && msg.contains("INKEYS");
    }

    /**
     * Probes a connected client for valkey-search 1.3 capability using a real command plus a 1.3-only
     * argument (INKEYS, added in rc1 / PR #967), classified by error text rather than a version
     * string. Raises -- never returns a false "incapable" -- if the search module appears entirely
     * absent.
     */
    @SneakyThrows
    static boolean probeSearch13Capability(BaseClient client) {
        String probeIndex = "__glide_search_1_3_probe_" + UUID.randomUUID();
        try {
            customCommand(
                    client,
                    new GlideString[] {
                        gs("FT.CREATE"), gs(probeIndex), gs("SCHEMA"), gs("title"), gs("TEXT")
                    });
        } catch (ExecutionException e) {
            throw new RuntimeException(
                    "search 1.3 capability probe could not create a test index "
                            + "(is the search module loaded at all?): "
                            + e.getCause(),
                    e.getCause());
        }

        try {
            try {
                customCommand(
                        client,
                        new GlideString[] {gs("FT.SEARCH"), gs(probeIndex), gs("*"), gs("INKEYS"), gs("0")});
                return true;
            } catch (ExecutionException e) {
                String msg = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
                if (classifyInkeysProbeError(msg)) {
                    return false;
                }
                throw new RuntimeException(
                        "search 1.3 capability probe got an unexpected error: " + msg, e.getCause());
            }
        } finally {
            try {
                customCommand(client, new GlideString[] {gs("FT.DROPINDEX"), gs(probeIndex)});
            } catch (ExecutionException e) {
                // Retry once -- FT.CREATE and FT.DROPINDEX both use probeIndex as their own
                // first argument, so they always route to the same primary; a cluster-routing
                // mismatch is not a possible explanation for a retry also failing.
                customCommand(client, new GlideString[] {gs("FT.DROPINDEX"), gs(probeIndex)});
            }
        }
    }

    /**
     * Dispatches a raw custom command through whichever concrete client type was given, unwrapping a
     * cluster client's {@link ClusterValue}. A command routed to a single node (the default for most
     * commands, including FT.CREATE/FT.SEARCH/FT.INFO) comes back as a single value under {@link
     * ClusterValue#getSingleValue()}. A command with no explicit route that still only gets answered
     * by one node (such as CONFIG GET on this test harness's single-module-config cluster) also comes
     * back from that one node, but {@link ClusterValue} stores its map-shaped reply under {@link
     * ClusterValue#getMultiValue()} instead -- see the comment at the call site below for how that
     * was confirmed. There is no real per-node breakdown to pick a node out of here.
     */
    private static Object customCommand(BaseClient client, GlideString[] args)
            throws InterruptedException, ExecutionException {
        if (client instanceof GlideClusterClient) {
            ClusterValue<Object> value = ((GlideClusterClient) client).customCommand(args).get();
            // For an unrouted command like CONFIG GET, the single node that answers returns its own
            // map-shaped reply, and ClusterValue stores that single map under getMultiValue() rather
            // than getSingleValue() (confirmed empirically against a live cluster: hasMultiData() is
            // true with exactly one entry whose key is the config key itself, e.g.
            // "search.emulate-release", not a node address) -- so the multi-value map IS the reply's
            // own contents here, not a per-node breakdown.
            if (value.hasMultiData()) {
                return value.getMultiValue();
            }
            return value.getSingleValue();
        }
        return ((GlideClient) client).customCommand(args).get();
    }

    /**
     * Reads one field out of a reply that may be a flat {@code Object[]} (odd-index keys, even-index
     * values -- the shape FT.INFO's nested {@code index_definition} value comes back as on both RESP2
     * and RESP3, since glide-core's {@code FTInfoReturnType} converter does not recurse into that
     * specific key the way it does {@code fields}/{@code vector_params}) or an already-flattened
     * {@code Map}. Returns {@code null} if the field is absent or the reply shape isn't recognised.
     * Mirrors Node's {@code readMapField} / Go's equivalent helper.
     */
    @SuppressWarnings("unchecked")
    static Object readMapField(Object reply, String field) {
        if (reply instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) reply).entrySet()) {
                if (field.equals(String.valueOf(entry.getKey()))) {
                    return entry.getValue();
                }
            }
            return null;
        }

        if (reply instanceof Object[]) {
            Object[] arr = (Object[]) reply;
            for (int i = 0; i + 1 < arr.length; i += 2) {
                if (field.equals(String.valueOf(arr[i]))) {
                    return arr[i + 1];
                }
            }
        }

        return null;
    }

    /**
     * {@code true} if {@code level} is strictly less than {@code minLevel}, both dotted X.Y.Z (an
     * optional {@code -rcN} suffix is stripped). Mirrors Node's {@code checkVersionLt}.
     */
    static boolean checkVersionLt(String level, String minLevel) {
        int[] a = parseDottedVersion(level);
        int[] b = parseDottedVersion(minLevel);
        int maxLen = Math.max(a.length, b.length);
        for (int i = 0; i < maxLen; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x < y;
            }
        }
        return false;
    }

    private static int[] parseDottedVersion(String version) {
        String base = version.split("-", 2)[0];
        String[] parts = base.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i]);
        }
        return result;
    }

    /**
     * Reads the active {@code search.emulate-release} level from a connected, 1.3-capable client.
     * Fails loudly if the level can't be parsed -- a capable module with an unparseable level is a
     * harness bug, not something to silently skip past.
     */
    @SneakyThrows
    static String getEmulateReleaseLevel(BaseClient client) {
        Object reply =
                customCommand(
                        client, new GlideString[] {gs("CONFIG"), gs("GET"), gs("search.emulate-release")});
        Object level = readMapField(reply, "search.emulate-release");
        if (level == null) {
            throw new AssertionError(
                    "search.emulate-release could not be parsed from a 1.3-capable module's CONFIG GET"
                            + " reply");
        }
        return level.toString();
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_inkeys(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String tag = "{" + UUID.randomUUID() + "}";
        String prefix = tag + ":";
        String indexName = prefix + "inkeys_idx";
        FT.create(
                        client,
                        indexName,
                        new FieldInfo[] {new FieldInfo("title", new TextField())},
                        FTCreateOptions.builder()
                                .dataType(DataType.HASH)
                                .prefixes(new String[] {prefix})
                                .build())
                .get();

        try {
            String key1 = prefix + "1";
            String key2 = prefix + "2";
            String key3 = prefix + "3";
            for (String key : new String[] {key1, key2, key3}) {
                Map<String, String> fields = new HashMap<>();
                fields.put("title", "hello world");
                if (client instanceof GlideClusterClient) {
                    ((GlideClusterClient) client).hset(key, fields).get();
                } else {
                    ((GlideClient) client).hset(key, fields).get();
                }
            }
            Thread.sleep(1000);

            // absent -> no restriction, all 3 match
            Object[] all = FT.search(client, indexName, "*", FTSearchOptions.builder().build()).get();
            assertEquals(3L, all[0]);

            // empty array -> INKEYS 0, excludes every result
            Object[] none =
                    FT.search(
                                    client,
                                    indexName,
                                    "*",
                                    FTSearchOptions.builder().inKeys(new GlideString[0]).build())
                            .get();
            assertEquals(0L, none[0]);

            // a list -> restricts to just those keys
            Object[] some =
                    FT.search(
                                    client,
                                    indexName,
                                    "*",
                                    FTSearchOptions.builder().inKeys(new GlideString[] {gs(key1), gs(key2)}).build())
                            .get();
            assertEquals(2L, some[0]);
        } finally {
            FT.dropindex(client, indexName).get();
        }
    }

    /**
     * Not gated behind the 1.3 capability probe: a bare {@code COUNT} token is invalid FT.SEARCH
     * syntax on search 1.2.x too, so this must run against whatever module version CI's own image
     * actually has.
     */
    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_count_maps_to_limit_zero_zero(BaseClient client) {
        String tag = "{" + UUID.randomUUID() + "}";
        String prefix = tag + ":";
        String indexName = prefix + "count_idx";
        FT.create(
                        client,
                        indexName,
                        new FieldInfo[] {new FieldInfo("title", new TextField())},
                        FTCreateOptions.builder()
                                .dataType(DataType.HASH)
                                .prefixes(new String[] {prefix})
                                .build())
                .get();

        try {
            Map<String, String> fields = new HashMap<>();
            fields.put("title", "hello");
            if (client instanceof GlideClusterClient) {
                ((GlideClusterClient) client).hset(prefix + "1", fields).get();
                ((GlideClusterClient) client).hset(prefix + "2", fields).get();
            } else {
                ((GlideClient) client).hset(prefix + "1", fields).get();
                ((GlideClient) client).hset(prefix + "2", fields).get();
            }
            Thread.sleep(1000);

            // A real field-filter query, not a bare "*" wildcard: valkey-search rejected a bare
            // "*" for non-vector queries before 1.3 (unrelated to this test's actual target, the
            // COUNT -> LIMIT 0 0 serialization), so using one here would make this test fail on
            // CI's pre-1.3 image for the wrong reason.
            Object[] result =
                    FT.search(client, indexName, "@title:hello", FTSearchOptions.builder().count().build())
                            .get();
            assertEquals(2L, result[0]);
            assertEquals(1, result.length);
        } finally {
            FT.dropindex(client, indexName).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_create_float16_bfloat16_vector(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String float16IndexName = "float16_idx_" + UUID.randomUUID();
        FieldInfo float16VectorField =
                new FieldInfo(
                        "vec", VectorFieldFlat.builder(DistanceMetric.L2, 2).type(VectorType.FLOAT16).build());
        assertEquals(
                OK,
                FT.create(
                                client,
                                float16IndexName,
                                new FieldInfo[] {float16VectorField},
                                FTCreateOptions.builder().dataType(DataType.HASH).build())
                        .get());
        FT.dropindex(client, float16IndexName).get();

        String bfloat16IndexName = "bfloat16_idx_" + UUID.randomUUID();
        FieldInfo bfloat16VectorField =
                new FieldInfo(
                        "vec", VectorFieldFlat.builder(DistanceMetric.L2, 2).type(VectorType.BFLOAT16).build());
        assertEquals(
                OK,
                FT.create(
                                client,
                                bfloat16IndexName,
                                new FieldInfo[] {bfloat16VectorField},
                                FTCreateOptions.builder().dataType(DataType.HASH).build())
                        .get());
        FT.dropindex(client, bfloat16IndexName).get();
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_create_filter_rejects_empty(BaseClient client) {
        try {
            FTCreateOptions.builder().dataType(DataType.HASH).filter("").build().toArgs();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("filter cannot be empty"));
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_create_filter_score_field_no_highlight(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String indexName = "parity_idx_" + UUID.randomUUID();
        FieldInfo titleField =
                new FieldInfo("title", new TextField(false, null, false, false, true, true));
        FieldInfo boostField = new FieldInfo("boost", new NumericField());

        assertEquals(
                OK,
                FT.create(
                                client,
                                indexName,
                                new FieldInfo[] {titleField, boostField},
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .filter("@title != ''")
                                        .scoreField("boost")
                                        .noHighlight(true)
                                        .build())
                        .get());

        try {
            Map<String, String> fields = new HashMap<>();
            fields.put("title", "hello");
            fields.put("boost", "5");
            if (client instanceof GlideClusterClient) {
                ((GlideClusterClient) client).hset("parity:1", fields).get();
            } else {
                ((GlideClient) client).hset("parity:1", fields).get();
            }
            Thread.sleep(1000);

            Map<String, Object> info = FT.info(client, indexName).get();
            assertEquals(indexName, info.get("index_name").toString());

            Object indexDefinition = info.get("index_definition");
            Object scoreField = readMapField(indexDefinition, "score_field");

            String level = getEmulateReleaseLevel(client);
            if (!checkVersionLt(level, "1.3.0")) {
                assertEquals("boost", String.valueOf(scoreField));
            } else {
                assertEquals(null, scoreField);
            }
        } finally {
            FT.dropindex(client, indexName).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_aggregate_reducers(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String tag = "{" + UUID.randomUUID() + "}";
        String prefix = tag + ":";
        String indexName = prefix + "reducers_idx";
        FieldInfo categoryField = new FieldInfo("category", new TextField());
        FieldInfo priceField = new FieldInfo("price", new NumericField());

        assertEquals(
                OK,
                FT.create(
                                client,
                                indexName,
                                new FieldInfo[] {categoryField, priceField},
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        try {
            Map<String, String> row1 = new HashMap<>();
            row1.put("category", "a");
            row1.put("price", "10");
            Map<String, String> row2 = new HashMap<>();
            row2.put("category", "a");
            row2.put("price", "30");
            Map<String, String> row3 = new HashMap<>();
            row3.put("category", "a");
            row3.put("price", "10");

            if (client instanceof GlideClusterClient) {
                ((GlideClusterClient) client).hset(prefix + "1", row1).get();
                ((GlideClusterClient) client).hset(prefix + "2", row2).get();
                ((GlideClusterClient) client).hset(prefix + "3", row3).get();
            } else {
                ((GlideClient) client).hset(prefix + "1", row1).get();
                ((GlideClient) client).hset(prefix + "2", row2).get();
                ((GlideClient) client).hset(prefix + "3", row3).get();
            }
            Thread.sleep(1000);

            FTAggregateOptions.GroupBy.Reducer toList =
                    FTAggregateOptions.GroupBy.Reducer.toList("@price", "prices");
            FTAggregateOptions.GroupBy.Reducer firstValue =
                    FTAggregateOptions.GroupBy.Reducer.firstValue(
                            "@price", "@price", FTAggregateOptions.SortBy.SortOrder.DESC, "max_price");
            FTAggregateOptions.GroupBy.Reducer randomSample =
                    FTAggregateOptions.GroupBy.Reducer.randomSample("@price", 2, "sample");

            FTAggregateOptions options =
                    FTAggregateOptions.builder()
                            .addClause(
                                    new FTAggregateOptions.GroupBy(
                                            new String[] {"@category"},
                                            new FTAggregateOptions.GroupBy.Reducer[] {toList, firstValue, randomSample}))
                            .build();

            Map<GlideString, Object>[] result = FT.aggregate(client, indexName, "*", options).get();
            assertEquals(1, result.length);
            Map<GlideString, Object> row = result[0];

            Object[] prices = (Object[]) row.get(gs("prices"));
            List<String> priceStrings = new ArrayList<>();
            for (Object price : prices) {
                priceStrings.add(price.toString());
            }
            priceStrings.sort(null);
            assertEquals(Arrays.asList("10", "30"), priceStrings);

            assertEquals("30", row.get(gs("max_price")).toString());
        } finally {
            FT.dropindex(client, indexName).get();
        }
    }

    // ---- Helpers shared by the scoring/cursor/FT.HYBRID tests below ----

    private static final String HYBRID_DOC_TEXT_WINNER = "hybrid-text-winner";
    private static final String HYBRID_DOC_VECTOR_WINNER = "hybrid-vector-winner";
    private static final float[] HYBRID_QUERY_VECTOR = {1.0f, 1.0f};
    private static final String HYBRID_SEARCH_QUERY = "hello";
    private static final String HYBRID_SEARCH_ALIAS = "ss";
    private static final String HYBRID_VSIM_ALIAS = "vs";

    /** Packs 2 floats little-endian, mirroring Python's {@code struct.pack("2f", x, y)}. */
    private static byte[] packVector(float x, float y) {
        ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        buf.putFloat(x);
        buf.putFloat(y);
        return buf.array();
    }

    /**
     * Creates an index with a TEXT field ("title") and a 2-dimensional FLAT FLOAT32 VECTOR field
     * ("vec"), seeds the HYBRID_DOC_TEXT_WINNER/HYBRID_DOC_VECTOR_WINNER fixture (mirroring
     * Python's HYBRID_DOCS), and waits for both documents to be indexed. Returns the index name.
     */
    @SneakyThrows
    private static String createHybridIndex(BaseClient client, String prefix) {
        String index = prefix + "index";
        FieldInfo vecField =
                new FieldInfo(
                        "vec", VectorFieldFlat.builder(DistanceMetric.L2, 2).type(VectorType.FLOAT32).build());
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField()), vecField},
                                FTCreateOptions.builder().dataType(DataType.HASH).prefixes(new String[] {prefix}).build())
                        .get());
        Map<String, Object> textWinnerFields = new HashMap<>();
        textWinnerFields.put("title", "hello hello hello world");
        Map<String, Object> vectorWinnerFields = new HashMap<>();
        vectorWinnerFields.put("title", "goodbye");
        Map<String, byte[]> vectors = new HashMap<>();
        vectors.put(HYBRID_DOC_TEXT_WINNER, packVector(100.0f, 100.0f));
        vectors.put(HYBRID_DOC_VECTOR_WINNER, packVector(1.0f, 1.0f));
        for (String docName : new String[] {HYBRID_DOC_TEXT_WINNER, HYBRID_DOC_VECTOR_WINNER}) {
            Map<String, Object> rawFields =
                    docName.equals(HYBRID_DOC_TEXT_WINNER) ? textWinnerFields : vectorWinnerFields;
            Map<GlideString, GlideString> hashFields = new HashMap<>();
            hashFields.put(gs("title"), gs((String) rawFields.get("title")));
            hashFields.put(gs("vec"), gs(vectors.get(docName)));
            if (client instanceof GlideClusterClient) {
                ((GlideClusterClient) client).hset(gs(prefix + docName), hashFields).get();
            } else {
                ((GlideClient) client).hset(gs(prefix + docName), hashFields).get();
            }
        }
        Thread.sleep(1000);
        return index;
    }

    // ---- Scoring: WITHSCORES/SCORER on FT.SEARCH ----

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_with_scores_and_scorer(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-with-scores-" + UUID.randomUUID() + "}:";
        String index = prefix + "index";
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder().dataType(DataType.HASH).prefixes(new String[] {prefix}).build())
                        .get());
        try {
            Map<String, String> doc1 = new HashMap<>();
            doc1.put("title", "hello hello hello world");
            Map<String, String> doc2 = new HashMap<>();
            doc2.put("title", "hello there");
            if (client instanceof GlideClusterClient) {
                ((GlideClusterClient) client).hset(prefix + "1", doc1).get();
                ((GlideClusterClient) client).hset(prefix + "2", doc2).get();
            } else {
                ((GlideClient) client).hset(prefix + "1", doc1).get();
                ((GlideClient) client).hset(prefix + "2", doc2).get();
            }
            Thread.sleep(1000);

            Object[] result =
                    FT.search(
                                    client,
                                    index,
                                    "hello",
                                    FTSearchOptions.builder().withScores().scorer("BM25STD").build())
                            .get();
            assertEquals(2L, result[0]);
            @SuppressWarnings("unchecked")
            Map<GlideString, Object> resultMap = (Map<GlideString, Object>) result[1];
            assertEquals(2, resultMap.size());
            Map<String, Double> scoresByKey = new HashMap<>();
            for (Map.Entry<GlideString, Object> entry : resultMap.entrySet()) {
                Object[] scoreAndFields = (Object[]) entry.getValue();
                assertEquals(2, scoreAndFields.length);
                double score = Double.parseDouble(scoreAndFields[0].toString());
                assertTrue(score > 0);
                scoresByKey.put(entry.getKey().toString(), score);
                @SuppressWarnings("unchecked")
                Map<GlideString, GlideString> fields = (Map<GlideString, GlideString>) scoreAndFields[1];
                assertTrue(fields.containsKey(gs("title")));
            }
            assertTrue(
                    scoresByKey.get(prefix + "1") > scoresByKey.get(prefix + "2"),
                    "the document repeating the search term should score strictly higher under BM25STD: "
                            + scoresByKey);
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_with_scores_and_withsortkeys_combined_shape(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-scores-sortkeys-" + UUID.randomUUID() + "}:";
        String index = prefix + "index";
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("title", new TextField(true, null, false, false, false, false)),
                                    new FieldInfo("price", new NumericField(true))
                                },
                                FTCreateOptions.builder().dataType(DataType.HASH).prefixes(new String[] {prefix}).build())
                        .get());
        try {
            Map<String, String> doc1 = new HashMap<>();
            doc1.put("title", "hello");
            doc1.put("price", "10");
            Map<String, String> doc2 = new HashMap<>();
            doc2.put("title", "world");
            doc2.put("price", "20");
            if (client instanceof GlideClusterClient) {
                ((GlideClusterClient) client).hset(prefix + "1", doc1).get();
                ((GlideClusterClient) client).hset(prefix + "2", doc2).get();
            } else {
                ((GlideClient) client).hset(prefix + "1", doc1).get();
                ((GlideClient) client).hset(prefix + "2", doc2).get();
            }
            Thread.sleep(1000);

            Object[] result =
                    FT.search(
                                    client,
                                    index,
                                    "*",
                                    FTSearchOptions.builder()
                                            .withScores()
                                            .sortBy("price", FTSearchOptions.SortOrder.ASC)
                                            .withSortKeys()
                                            .build())
                            .get();
            assertEquals(2L, result[0]);
            @SuppressWarnings("unchecked")
            Map<GlideString, Object> resultMap = (Map<GlideString, Object>) result[1];
            assertEquals(2, resultMap.size());
            for (Object docValue : resultMap.values()) {
                Object[] triple = (Object[]) docValue;
                assertEquals(3, triple.length);
                double score = Double.parseDouble(triple[0].toString());
                assertTrue(score >= 0);
                assertNotEquals(null, triple[1]);
                @SuppressWarnings("unchecked")
                Map<GlideString, GlideString> fields = (Map<GlideString, GlideString>) triple[2];
                assertTrue(fields.containsKey(gs("title")));
            }
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_scorer_tfidf_rejected(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-scorer-tfidf-" + UUID.randomUUID() + "}:";
        String index = prefix + "index";
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder().dataType(DataType.HASH).prefixes(new String[] {prefix}).build())
                        .get());
        try {
            Map<String, String> doc = new HashMap<>();
            doc.put("title", "hello world");
            if (client instanceof GlideClusterClient) {
                ((GlideClusterClient) client).hset(prefix + "1", doc).get();
            } else {
                ((GlideClient) client).hset(prefix + "1", doc).get();
            }
            Thread.sleep(1000);

            ExecutionException e =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    FT.search(
                                                    client,
                                                    index,
                                                    "hello",
                                                    FTSearchOptions.builder().withScores().scorer("TFIDF").build())
                                            .get());
            assertTrue(e.getCause().getMessage().contains("SCORER"));
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    // ---- Scoring: ADDSCORES/SCORER on FT.AGGREGATE ----

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_aggregate_add_scores_sortable_by_score(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-add-scores-" + UUID.randomUUID() + "}:";
        String index = prefix + "index";
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder().dataType(DataType.HASH).prefixes(new String[] {prefix}).build())
                        .get());
        try {
            Map<String, String> doc1 = new HashMap<>();
            doc1.put("title", "hello world");
            Map<String, String> doc2 = new HashMap<>();
            doc2.put("title", "hello there friend");
            if (client instanceof GlideClusterClient) {
                ((GlideClusterClient) client).hset(prefix + "1", doc1).get();
                ((GlideClusterClient) client).hset(prefix + "2", doc2).get();
            } else {
                ((GlideClient) client).hset(prefix + "1", doc1).get();
                ((GlideClient) client).hset(prefix + "2", doc2).get();
            }
            Thread.sleep(1000);

            Map<GlideString, Object>[] result =
                    FT.aggregate(
                                    client,
                                    index,
                                    "hello",
                                    FTAggregateOptions.builder()
                                            .addScores()
                                            .loadFields(new String[] {"title"})
                                            .addClause(
                                                    new FTAggregateOptions.SortBy(
                                                            new FTAggregateOptions.SortBy.SortProperty[] {
                                                                new FTAggregateOptions.SortBy.SortProperty(
                                                                        "@__score", FTAggregateOptions.SortBy.SortOrder.DESC)
                                                            }))
                                            .build())
                            .get();
            assertEquals(2, result.length);
            List<Double> scores = new ArrayList<>();
            for (Map<GlideString, Object> row : result) {
                assertTrue(row.containsKey(gs("__score")));
                double score = Double.parseDouble(row.get(gs("__score")).toString());
                assertTrue(score > 0);
                scores.add(score);
            }
            List<Double> sorted = new ArrayList<>(scores);
            sorted.sort((a, b) -> Double.compare(b, a));
            assertEquals(sorted, scores);
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    // ---- Cursors: FT.AGGREGATE WITHCURSOR, FT.CURSOR READ/DEL, typed API ----

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_aggregate_typed_cursor_paged_to_exhaustion(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-typed-cursor-" + UUID.randomUUID() + "}:";
        String index = prefix + "index";
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("n", new NumericField(true))},
                                FTCreateOptions.builder().dataType(DataType.HASH).prefixes(new String[] {prefix}).build())
                        .get());
        try {
            for (int i = 1; i <= 20; i++) {
                Map<String, String> fields = new HashMap<>();
                fields.put("n", Integer.toString(i));
                if (client instanceof GlideClusterClient) {
                    ((GlideClusterClient) client).hset(prefix + "d" + i, fields).get();
                } else {
                    ((GlideClient) client).hset(prefix + "d" + i, fields).get();
                }
            }
            Thread.sleep(1000);

            int totalRows = 0;
            Set<Integer> seenNValues = new HashSet<>();
            Object[] page =
                    FT.aggregate(
                                    client,
                                    index,
                                    "*",
                                    FTAggregateOptions.builder()
                                            .loadFields(new String[] {"n"})
                                            .withCursor(new FTWithCursor(5))
                                            .build())
                            .get();
            Map<GlideString, Object>[] rows = castArray((Object[]) page[0], Map.class);
            long cursorId = (long) page[1];
            totalRows += rows.length;
            for (Map<GlideString, Object> row : rows) {
                seenNValues.add(Integer.parseInt(row.get(gs("n")).toString()));
            }
            int pagesRead = 1;
            while (cursorId != 0) {
                Object[] nextPage = FT.cursorRead(client, index, cursorId).get();
                Map<GlideString, Object>[] nextRows = castArray((Object[]) nextPage[0], Map.class);
                totalRows += nextRows.length;
                for (Map<GlideString, Object> row : nextRows) {
                    seenNValues.add(Integer.parseInt(row.get(gs("n")).toString()));
                }
                cursorId = (long) nextPage[1];
                pagesRead++;
                assertTrue(pagesRead <= 10, "paging did not terminate as expected");
            }
            assertEquals(20, totalRows);
            Set<Integer> expected = new HashSet<>();
            for (int i = 1; i <= 20; i++) expected.add(i);
            assertEquals(expected, seenNValues);
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_aggregate_typed_cursor_del_and_errors(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-typed-cursor-del-" + UUID.randomUUID() + "}:";
        String index = prefix + "index";
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("n", new NumericField(true))},
                                FTCreateOptions.builder().dataType(DataType.HASH).prefixes(new String[] {prefix}).build())
                        .get());
        try {
            for (int i = 1; i <= 5; i++) {
                Map<String, String> fields = new HashMap<>();
                fields.put("n", Integer.toString(i));
                if (client instanceof GlideClusterClient) {
                    ((GlideClusterClient) client).hset(prefix + "d" + i, fields).get();
                } else {
                    ((GlideClient) client).hset(prefix + "d" + i, fields).get();
                }
            }
            Thread.sleep(1000);

            Object[] page =
                    FT.aggregate(
                                    client,
                                    index,
                                    "*",
                                    FTAggregateOptions.builder()
                                            .loadFields(new String[] {"n"})
                                            .withCursor(new FTWithCursor(1))
                                            .build())
                            .get();
            long openCursorId = (long) page[1];
            assertNotEquals(0L, openCursorId);

            assertEquals(OK, FT.cursorDel(client, index, openCursorId).get());

            ExecutionException e1 =
                    assertThrows(
                            ExecutionException.class, () -> FT.cursorRead(client, index, openCursorId).get());
            assertTrue(e1.getCause().getMessage().contains("Cursor"));

            ExecutionException e2 =
                    assertThrows(
                            ExecutionException.class, () -> FT.cursorRead(client, index, 999999999L).get());
            assertTrue(e2.getCause().getMessage().contains("Cursor"));
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClustersOnly")
    public void ft_cursor_affinity_prefer_replica_client(BaseClient clusterClient) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");
        GlideClusterClient client = (GlideClusterClient) clusterClient;

        String prefix = "{ft-cursor-affinity-" + UUID.randomUUID() + "}:";
        String index = prefix + "index";
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("n", new NumericField(true))},
                                FTCreateOptions.builder().dataType(DataType.HASH).prefixes(new String[] {prefix}).build())
                        .get());
        for (int i = 1; i <= 20; i++) {
            Map<String, String> fields = new HashMap<>();
            fields.put("n", Integer.toString(i));
            client.hset(prefix + "d" + i, fields).get();
        }
        Thread.sleep(1000);

        GlideClusterClient preferReplicaClient =
                GlideClusterClient.createClient(
                                commonClusterClientConfig()
                                        .requestTimeout(5000)
                                        .readFrom(ReadFrom.PREFER_REPLICA)
                                        .build())
                        .get();
        try {
            int totalRows = 0;
            Object[] page =
                    (Object[])
                            preferReplicaClient
                                    .customCommand(
                                            new GlideString[] {
                                                gs("FT.AGGREGATE"),
                                                gs(index),
                                                gs("*"),
                                                gs("LOAD"),
                                                gs("1"),
                                                gs("n"),
                                                gs("WITHCURSOR"),
                                                gs("COUNT"),
                                                gs("5")
                                            })
                                    .get()
                                    .getSingleValue();
            Map<GlideString, Object>[] rows = castArray((Object[]) page[0], Map.class);
            long cursorId = (long) page[1];
            totalRows += rows.length;
            Set<Integer> seenNValues = new HashSet<>();
            for (Map<GlideString, Object> row : rows) {
                seenNValues.add(Integer.parseInt(row.get(gs("n")).toString()));
            }
            int pagesRead = 1;
            while (cursorId != 0) {
                Object[] nextPage =
                        (Object[])
                                preferReplicaClient
                                        .customCommand(
                                                new GlideString[] {
                                                    gs("FT.CURSOR"), gs("READ"), gs(index), gs(Long.toString(cursorId))
                                                })
                                        .get()
                                        .getSingleValue();
                Map<GlideString, Object>[] nextRows = castArray((Object[]) nextPage[0], Map.class);
                totalRows += nextRows.length;
                for (Map<GlideString, Object> row : nextRows) {
                    seenNValues.add(Integer.parseInt(row.get(gs("n")).toString()));
                }
                cursorId = (long) nextPage[1];
                pagesRead++;
                assertTrue(pagesRead <= 10, "paging did not terminate as expected");
            }
            assertEquals(20, totalRows);
            Set<Integer> expected = new HashSet<>();
            for (int i = 1; i <= 20; i++) expected.add(i);
            assertEquals(expected, seenNValues);

            // Negative control: a read explicitly routed to a REPLICA of the index's OWN slot
            // fails, confirming the cursor really is node-local.
            List<Object> shardsReply =
                    Arrays.asList(
                            (Object[])
                                    preferReplicaClient
                                            .customCommand(new GlideString[] {gs("CLUSTER"), gs("SHARDS")})
                                            .get()
                                            .getSingleValue());
            long indexSlot =
                    (long)
                            preferReplicaClient
                                    .customCommand(new GlideString[] {gs("CLUSTER"), gs("KEYSLOT"), gs(index)})
                                    .get()
                                    .getSingleValue();
            List<String[]> replicaAddrs = new ArrayList<>();
            for (Object shardObj : shardsReply) {
                @SuppressWarnings("unchecked")
                Map<GlideString, Object> shardMap = (Map<GlideString, Object>) shardObj;
                Object[] slots = (Object[]) shardMap.get(gs("slots"));
                boolean ownsSlot = false;
                for (int i = 0; i + 1 < slots.length; i += 2) {
                    long lo = (long) slots[i];
                    long hi = (long) slots[i + 1];
                    if (lo <= indexSlot && indexSlot <= hi) ownsSlot = true;
                }
                if (!ownsSlot) continue;
                Object[] nodes = (Object[]) shardMap.get(gs("nodes"));
                for (Object nodeObj : nodes) {
                    @SuppressWarnings("unchecked")
                    Map<GlideString, Object> nodeMap = (Map<GlideString, Object>) nodeObj;
                    if (gs("replica").equals(nodeMap.get(gs("role")))) {
                        replicaAddrs.add(
                                new String[] {
                                    nodeMap.get(gs("ip")).toString(), nodeMap.get(gs("port")).toString()
                                });
                    }
                }
            }
            assertTrue(replicaAddrs.size() >= 1, "test fixture bug: no replica found for index's own shard");

            Object[] anotherPage =
                    (Object[])
                            preferReplicaClient
                                    .customCommand(
                                            new GlideString[] {
                                                gs("FT.AGGREGATE"),
                                                gs(index),
                                                gs("*"),
                                                gs("LOAD"),
                                                gs("1"),
                                                gs("n"),
                                                gs("WITHCURSOR"),
                                                gs("COUNT"),
                                                gs("5")
                                            })
                                    .get()
                                    .getSingleValue();
            long anotherCursorId = (long) anotherPage[1];
            for (String[] addr : replicaAddrs) {
                ExecutionException e =
                        assertThrows(
                                ExecutionException.class,
                                () ->
                                        preferReplicaClient
                                                .customCommand(
                                                        new GlideString[] {
                                                            gs("FT.CURSOR"),
                                                            gs("READ"),
                                                            gs(index),
                                                            gs(Long.toString(anotherCursorId))
                                                        },
                                                        new ByAddressRoute(addr[0], Integer.parseInt(addr[1])))
                                                .get());
                assertTrue(e.getCause().getMessage().contains("Cursor"));
            }
        } finally {
            preferReplicaClient.close();
            FT.dropindex(client, index).get();
        }
    }

    static Stream<Arguments> getClustersOnly() {
        return clients.stream()
                .filter(
                        args -> {
                            @SuppressWarnings("unchecked")
                            BaseClient c = ((Named<BaseClient>) args.get()[0]).getPayload();
                            return c instanceof GlideClusterClient;
                        });
    }

    // ---- FT.HYBRID ----

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_basic_query(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-hybrid-basic-" + UUID.randomUUID() + "}:";
        String index = createHybridIndex(client, prefix);
        try {
            Object[] result =
                    FT.hybrid(
                                    client,
                                    index,
                                    new FTHybridSearchArm("hello"),
                                    new FTHybridVsimArm("vec", packVector(1.0f, 1.0f), 5, null, null))
                            .get();
            assertEquals(2, result.length);
            Set<GlideString> keys = new HashSet<>();
            for (Object rowObj : result) {
                @SuppressWarnings("unchecked")
                Map<GlideString, Object> row = (Map<GlideString, Object>) rowObj;
                keys.add((GlideString) row.get(gs("__key")));
                assertTrue(row.containsKey(gs("__score")));
            }
            Set<GlideString> expected = new HashSet<>();
            expected.add(gs(prefix + HYBRID_DOC_TEXT_WINNER));
            expected.add(gs(prefix + HYBRID_DOC_VECTOR_WINNER));
            assertEquals(expected, keys);
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_aggregate_clause_filters_post_fusion(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-hybrid-postfusion-filter-" + UUID.randomUUID() + "}:";
        String index = createHybridIndex(client, prefix);
        try {
            Object[] result =
                    FT.hybrid(
                                    client,
                                    index,
                                    FTHybridSearchArm.withYieldScoreAs("goodbye", HYBRID_SEARCH_ALIAS),
                                    new FTHybridVsimArm("vec", packVector(1.0f, 1.0f), 5, null, null),
                                    FTHybridOptions.builder()
                                            .addClause(new FTAggregateOptions.Filter("@" + HYBRID_SEARCH_ALIAS + " > 0"))
                                            .build())
                            .get();
            assertEquals(1, result.length);
            @SuppressWarnings("unchecked")
            Map<GlideString, Object> row = (Map<GlideString, Object>) result[0];
            assertEquals(gs(prefix + HYBRID_DOC_VECTOR_WINNER), row.get(gs("__key")));
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_combine_methods_fuse_results_linear_text_only(BaseClient client) {
        assertCombineWinner(client, new FTHybridCombine.Linear(1.0, 0.0, null, null), HYBRID_DOC_TEXT_WINNER);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_combine_methods_fuse_results_linear_vector_only(BaseClient client) {
        assertCombineWinner(
                client, new FTHybridCombine.Linear(0.0, 1.0, null, null), HYBRID_DOC_VECTOR_WINNER);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_combine_methods_fuse_results_function_text(BaseClient client) {
        assertCombineWinner(
                client,
                new FTHybridCombine.Function("@" + HYBRID_SEARCH_ALIAS),
                HYBRID_DOC_TEXT_WINNER);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_combine_methods_fuse_results_function_vec(BaseClient client) {
        assertCombineWinner(
                client, new FTHybridCombine.Function("@" + HYBRID_VSIM_ALIAS), HYBRID_DOC_VECTOR_WINNER);
    }

    @SneakyThrows
    private void assertCombineWinner(
            BaseClient client, FTHybridCombine combine, String expectedFirst) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-hybrid-combine-" + UUID.randomUUID() + "}:";
        String index = createHybridIndex(client, prefix);
        try {
            Object[] result =
                    FT.hybrid(
                                    client,
                                    index,
                                    FTHybridSearchArm.withYieldScoreAs(HYBRID_SEARCH_QUERY, HYBRID_SEARCH_ALIAS),
                                    new FTHybridVsimArm(
                                            "vec",
                                            packVector(HYBRID_QUERY_VECTOR[0], HYBRID_QUERY_VECTOR[1]),
                                            5,
                                            null,
                                            null,
                                            gs(HYBRID_VSIM_ALIAS)),
                                    FTHybridOptions.builder().combine(combine).build())
                            .get();
            assertEquals(2, result.length);
            Map<GlideString, Map<GlideString, Object>> rowsByKey = new HashMap<>();
            for (Object rowObj : result) {
                @SuppressWarnings("unchecked")
                Map<GlideString, Object> row = (Map<GlideString, Object>) rowObj;
                rowsByKey.put((GlideString) row.get(gs("__key")), row);
                assertTrue(row.containsKey(gs("__score")));
                assertTrue(row.containsKey(gs(HYBRID_VSIM_ALIAS)));
            }
            assertTrue(rowsByKey.containsKey(gs(prefix + HYBRID_DOC_TEXT_WINNER)));
            assertTrue(rowsByKey.containsKey(gs(prefix + HYBRID_DOC_VECTOR_WINNER)));
            @SuppressWarnings("unchecked")
            Map<GlideString, Object> firstRow = (Map<GlideString, Object>) result[0];
            assertEquals(gs(prefix + expectedFirst), firstRow.get(gs("__key")));
            assertTrue(
                    rowsByKey.get(gs(prefix + HYBRID_DOC_TEXT_WINNER)).containsKey(gs(HYBRID_SEARCH_ALIAS)));
            assertTrue(
                    !rowsByKey
                            .get(gs(prefix + HYBRID_DOC_VECTOR_WINNER))
                            .containsKey(gs(HYBRID_SEARCH_ALIAS)));
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_vector_inline_bytes_and_param_reference(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-hybrid-vecparam-" + UUID.randomUUID() + "}:";
        String index = createHybridIndex(client, prefix);
        try {
            byte[] vec = packVector(HYBRID_QUERY_VECTOR[0], HYBRID_QUERY_VECTOR[1]);

            Object[] inlineResult =
                    FT.hybrid(
                                    client,
                                    index,
                                    new FTHybridSearchArm(HYBRID_SEARCH_QUERY),
                                    new FTHybridVsimArm("vec", vec, 1, null, null, gs(HYBRID_VSIM_ALIAS)))
                            .get();

            Map<String, GlideString> params = new HashMap<>();
            params.put("myvec", gs(vec));
            Object[] paramResult =
                    FT.hybrid(
                                    client,
                                    index,
                                    new FTHybridSearchArm(HYBRID_SEARCH_QUERY),
                                    new FTHybridVsimArm("vec", "$myvec", 1, null, null, gs(HYBRID_VSIM_ALIAS)),
                                    FTHybridOptions.builder().params(params).build())
                            .get();

            assertEquals(2, inlineResult.length);
            assertEquals(2, paramResult.length);

            for (Object[] result : new Object[][] {inlineResult, paramResult}) {
                int rowsWithVs = 0;
                GlideString vsKey = null;
                for (Object rowObj : result) {
                    @SuppressWarnings("unchecked")
                    Map<GlideString, Object> row = (Map<GlideString, Object>) rowObj;
                    if (row.containsKey(gs(HYBRID_VSIM_ALIAS))) {
                        rowsWithVs++;
                        vsKey = (GlideString) row.get(gs("__key"));
                    }
                }
                assertEquals(1, rowsWithVs, "k=1 should select exactly one document");
                assertEquals(gs(prefix + HYBRID_DOC_VECTOR_WINNER), vsKey);
            }
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_vsim_filter_with_policy_adhoc(BaseClient client) {
        assertVsimFilterWithPolicy(client, HybridFilterPolicy.ADHOC);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_vsim_filter_with_policy_batches(BaseClient client) {
        assertVsimFilterWithPolicy(client, HybridFilterPolicy.BATCHES);
    }

    @SneakyThrows
    private void assertVsimFilterWithPolicy(BaseClient client, HybridFilterPolicy policy) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-hybrid-filter-" + UUID.randomUUID() + "}:";
        String index = createHybridIndex(client, prefix);
        try {
            Object[] result =
                    FT.hybrid(
                                    client,
                                    index,
                                    new FTHybridSearchArm("nonexistentterm12345"),
                                    new FTHybridVsimArm(
                                            "vec",
                                            packVector(1.0f, 1.0f),
                                            5,
                                            null,
                                            null,
                                            null,
                                            new FTHybridVsimFilter("@title:hello", policy)))
                            .get();
            assertEquals(1, result.length);
            @SuppressWarnings("unchecked")
            Map<GlideString, Object> row = (Map<GlideString, Object>) result[0];
            assertEquals(gs(prefix + HYBRID_DOC_TEXT_WINNER), row.get(gs("__key")));
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_localonly_accepted(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-hybrid-localonly-" + UUID.randomUUID() + "}:";
        String index = createHybridIndex(client, prefix);
        try {
            Object[] result =
                    FT.hybrid(
                                    client,
                                    index,
                                    new FTHybridSearchArm("hello"),
                                    new FTHybridVsimArm("vec", packVector(1.0f, 1.0f), 5, null, null),
                                    FTHybridOptions.builder().localOnly().build())
                            .get();
            assertEquals(2, result.length);
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_without_k_uses_engine_default(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-hybrid-default-k-" + UUID.randomUUID() + "}:";
        String index = prefix + "index";
        int docCount = 15;
        FieldInfo vecField =
                new FieldInfo(
                        "vec", VectorFieldFlat.builder(DistanceMetric.L2, 2).type(VectorType.FLOAT32).build());
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField()), vecField},
                                FTCreateOptions.builder().dataType(DataType.HASH).prefixes(new String[] {prefix}).build())
                        .get());
        try {
            for (int i = 1; i <= docCount; i++) {
                Map<GlideString, GlideString> fields = new HashMap<>();
                fields.put(gs("title"), gs("goodbye"));
                fields.put(gs("vec"), gs(packVector((float) i, (float) i)));
                if (client instanceof GlideClusterClient) {
                    ((GlideClusterClient) client).hset(gs(prefix + "d" + i), fields).get();
                } else {
                    ((GlideClient) client).hset(gs(prefix + "d" + i), fields).get();
                }
            }
            Thread.sleep(1000);

            Object[] result =
                    FT.hybrid(
                                    client,
                                    index,
                                    new FTHybridSearchArm("nonexistentterm12345"),
                                    new FTHybridVsimArm("vec", packVector(1.0f, 1.0f)))
                            .get();
            assertEquals(10, result.length);
        } finally {
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClustersOnly")
    public void ft_hybrid_with_cursor_prefer_replica_client_paged_to_exhaustion(BaseClient clusterClient) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");
        GlideClusterClient client = (GlideClusterClient) clusterClient;

        String prefix = "{ft-hybrid-cursor-" + UUID.randomUUID() + "}:";
        String index = createHybridIndex(client, prefix);

        GlideClusterClient preferReplicaClient =
                GlideClusterClient.createClient(
                                commonClusterClientConfig()
                                        .requestTimeout(5000)
                                        .readFrom(ReadFrom.PREFER_REPLICA)
                                        .build())
                        .get();
        try {
            int totalRows = 0;
            Object[] page =
                    FT.hybrid(
                                    preferReplicaClient,
                                    index,
                                    new FTHybridSearchArm("*"),
                                    new FTHybridVsimArm("vec", packVector(1.0f, 1.0f), 5, null, null),
                                    FTHybridOptions.builder().withCursor(new FTWithCursor(1)).build())
                            .get();
            Map<GlideString, Object>[] rows = castArray((Object[]) page[0], Map.class);
            long cursorId = (long) page[1];
            totalRows += rows.length;
            Set<GlideString> seenKeys = new HashSet<>();
            for (Map<GlideString, Object> row : rows) {
                seenKeys.add((GlideString) row.get(gs("__key")));
            }
            int pagesRead = 1;
            while (cursorId != 0) {
                Object[] nextPage = FT.cursorRead(preferReplicaClient, index, cursorId).get();
                Map<GlideString, Object>[] nextRows = castArray((Object[]) nextPage[0], Map.class);
                totalRows += nextRows.length;
                for (Map<GlideString, Object> row : nextRows) {
                    seenKeys.add((GlideString) row.get(gs("__key")));
                }
                cursorId = (long) nextPage[1];
                pagesRead++;
                assertTrue(pagesRead <= 10, "paging did not terminate as expected");
            }
            assertEquals(2, totalRows);
            Set<GlideString> expectedKeys = new HashSet<>();
            expectedKeys.add(gs(prefix + HYBRID_DOC_TEXT_WINNER));
            expectedKeys.add(gs(prefix + HYBRID_DOC_VECTOR_WINNER));
            assertEquals(expectedKeys, seenKeys);
        } finally {
            preferReplicaClient.close();
            FT.dropindex(client, index).get();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_hybrid_inside_multi_exec_rejected(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

        String prefix = "{ft-hybrid-multiexec-" + UUID.randomUUID() + "}:";
        String index = createHybridIndex(client, prefix);
        try {
            GlideString[] cmd = {
                gs("FT.HYBRID"),
                gs(index),
                gs("SEARCH"),
                gs(HYBRID_SEARCH_QUERY),
                gs("VSIM"),
                gs("@vec"),
                gs("$q"),
                gs("PARAMS"),
                gs("2"),
                gs("q"),
                gs(packVector(HYBRID_QUERY_VECTOR[0], HYBRID_QUERY_VECTOR[1]))
            };
            if (client instanceof GlideClusterClient) {
                glide.api.models.ClusterBatch batch = new glide.api.models.ClusterBatch(true);
                batch.customCommand(cmd);
                ExecutionException e =
                        assertThrows(
                                ExecutionException.class,
                                () -> ((GlideClusterClient) client).exec(batch, true).get());
                assertTrue(e.getCause().getMessage().contains("FT.HYBRID"));
            } else {
                glide.api.models.Batch batch = new glide.api.models.Batch(true);
                batch.customCommand(cmd);
                ExecutionException e =
                        assertThrows(
                                ExecutionException.class, () -> ((GlideClient) client).exec(batch, true).get());
                assertTrue(e.getCause().getMessage().contains("FT.HYBRID"));
            }
        } finally {
            FT.dropindex(client, index).get();
        }
    }
}

