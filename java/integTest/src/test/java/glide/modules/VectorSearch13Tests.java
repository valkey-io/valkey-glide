/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import glide.api.models.commands.FT.FTSearchOptions;
import glide.api.models.commands.FlushMode;
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * commands, including FT.CREATE/FT.SEARCH/FT.INFO) comes back as a single value; a command that
     * fans out to every node by default (such as CONFIG GET with no explicit route) comes back as a
     * per-node map, in which case the first node's reply is returned -- every node in this test
     * harness's single-module-config cluster reports the same value, so any one node answers the
     * question this helper is used for.
     */
    private static Object customCommand(BaseClient client, GlideString[] args)
            throws InterruptedException, ExecutionException {
        if (client instanceof GlideClusterClient) {
            ClusterValue<Object> value = ((GlideClusterClient) client).customCommand(args).get();
            // For an unrouted command like CONFIG GET or FT.INFO, the single node that answers
            // returns its own map-shaped reply, and ClusterValue stores that single map under
            // getMultiValue() rather than getSingleValue() (confirmed empirically against a live
            // cluster: hasMultiData() is true with exactly one entry whose key is the config key
            // itself, e.g. "search.emulate-release", not a node address) -- so the multi-value map
            // IS the reply's own contents here, not a per-node breakdown.
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
}
