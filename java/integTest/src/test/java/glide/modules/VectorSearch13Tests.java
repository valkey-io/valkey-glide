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
import glide.api.models.GlideString;
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
 * Live tests for the valkey-search 1.3 stable surface (wave 1 of the ports --
 * QA/BRIEF-WAVE1-PORTS.md): INKEYS, FLOAT16/BFLOAT16 vector types, FT.CREATE FILTER/SCORE_FIELD/
 * NOHL, per-field UNF, and count's LIMIT 0 0 serialization. WITHSCORES/SCORER, ADDSCORES,
 * WITHCURSOR/FT.CURSOR, FT.HYBRID and LoadField aliases are wave 2, out of scope here.
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

    /** Dispatches a raw custom command through whichever concrete client type was given. */
    private static Object customCommand(BaseClient client, GlideString[] args)
            throws InterruptedException, ExecutionException {
        if (client instanceof GlideClusterClient) {
            return ((GlideClusterClient) client).customCommand(args).get();
        }
        return ((GlideClient) client).customCommand(args).get();
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

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_count_maps_to_limit_zero_zero(BaseClient client) {
        Assumptions.assumeTrue(
                capability, "search module present but pre-1.3 (INKEYS probe rejected it)");

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

            Object[] result =
                    FT.search(client, indexName, "*", FTSearchOptions.builder().count().build()).get();
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

        String indexName = "float16_idx_" + UUID.randomUUID();
        FieldInfo vectorField =
                new FieldInfo(
                        "vec", VectorFieldFlat.builder(DistanceMetric.L2, 2).type(VectorType.FLOAT16).build());
        assertEquals(
                OK,
                FT.create(
                                client,
                                indexName,
                                new FieldInfo[] {vectorField},
                                FTCreateOptions.builder().dataType(DataType.HASH).build())
                        .get());
        FT.dropindex(client, indexName).get();
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
        } finally {
            FT.dropindex(client, indexName).get();
        }
    }
}
