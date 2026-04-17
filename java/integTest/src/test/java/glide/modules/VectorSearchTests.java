/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.assertDeepEquals;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.utils.Java8Utils.createMap;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;

import glide.TestConfiguration;
import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.commands.servermodules.FT;
import glide.api.commands.servermodules.Json;
import glide.api.models.GlideString;
import glide.api.models.commands.FT.FTAggregateOptions;
import glide.api.models.commands.FT.FTAggregateOptions.Apply;
import glide.api.models.commands.FT.FTAggregateOptions.GroupBy;
import glide.api.models.commands.FT.FTAggregateOptions.GroupBy.Reducer;
import glide.api.models.commands.FT.FTAggregateOptions.SortBy;
import glide.api.models.commands.FT.FTAggregateOptions.SortBy.SortOrder;
import glide.api.models.commands.FT.FTAggregateOptions.SortBy.SortProperty;
import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTCreateOptions.DataType;
import glide.api.models.commands.FT.FTCreateOptions.DistanceMetric;
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
import glide.api.models.commands.FT.FTCreateOptions.NumericField;
import glide.api.models.commands.FT.FTCreateOptions.TagField;
import glide.api.models.commands.FT.FTCreateOptions.TextField;
import glide.api.models.commands.FT.FTCreateOptions.VectorFieldFlat;
import glide.api.models.commands.FT.FTCreateOptions.VectorFieldHnsw;
import glide.api.models.commands.FT.FTInfoOptions;
import glide.api.models.commands.FT.FTProfileOptions;
import glide.api.models.commands.FT.FTSearchOptions;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute;
import glide.api.models.exceptions.RequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class VectorSearchTests {

    private static final List<Arguments> clients = new ArrayList<>();

    /** Waiting interval to let server process the data before querying */
    private static final int DATA_PROCESSING_TIMEOUT = 1000; // ms

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

    /** Returns only cluster clients for tests that require cluster-specific features. */
    static Stream<Arguments> getClusterClients() {
        return clients.stream()
                .filter(
                        args -> {
                            @SuppressWarnings("unchecked")
                            Named<BaseClient> named = (Named<BaseClient>) args.get()[0];
                            return named.getPayload() instanceof GlideClusterClient;
                        });
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void check_module_loaded(BaseClient client) {
        String info;
        if (client instanceof GlideClusterClient) {
            info =
                    ((GlideClusterClient) client)
                            .info(new Section[] {Section.MODULES}, SimpleSingleNodeRoute.RANDOM)
                            .get()
                            .getSingleValue();
        } else {
            info = ((GlideClient) client).info(new Section[] {Section.MODULES}).get();
        }
        assertTrue(info.contains("# search_index_stats"));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_create(BaseClient client) {
        // create few simple indices
        assertEquals(
                OK,
                FT.create(
                                client,
                                UUID.randomUUID().toString(),
                                new FieldInfo[] {
                                    new FieldInfo("vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {"vec:"})
                                        .build())
                        .get());
        assertEquals(
                OK,
                FT.create(
                                client,
                                UUID.randomUUID().toString(),
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "$.vec", "VEC", VectorFieldFlat.builder(DistanceMetric.L2, 6).build())
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.JSON)
                                        .prefixes(new String[] {"json:"})
                                        .build())
                        .get());

        // create an index with HNSW vector with additional parameters
        assertEquals(
                OK,
                FT.create(
                                client,
                                UUID.randomUUID().toString(),
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "doc_embedding",
                                            VectorFieldHnsw.builder(DistanceMetric.COSINE, 1536)
                                                    .numberOfEdges(40)
                                                    .vectorsExaminedOnConstruction(250)
                                                    .vectorsExaminedOnRuntime(40)
                                                    .build())
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {"docs:"})
                                        .build())
                        .get());

        // create an index with multiple fields
        assertEquals(
                OK,
                FT.create(
                                client,
                                UUID.randomUUID().toString(),
                                new FieldInfo[] {
                                    new FieldInfo("title", new TextField()),
                                    new FieldInfo("published_at", new NumericField()),
                                    new FieldInfo("category", new TagField())
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {"blog:post:"})
                                        .build())
                        .get());

        // create an index with multiple prefixes
        String index = UUID.randomUUID().toString();
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("author_id", new TagField()),
                                    new FieldInfo("author_ids", new TagField()),
                                    new FieldInfo("title", new TextField()),
                                    new FieldInfo("name", new TextField())
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {"author:details:", "book:details:"})
                                        .build())
                        .get());

        // create a duplicating index
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                FT.create(
                                                client,
                                                index,
                                                new FieldInfo[] {
                                                    new FieldInfo("title", new TextField()),
                                                    new FieldInfo("name", new TextField())
                                                },
                                                FTCreateOptions.builder()
                                                        .dataType(DataType.HASH)
                                                        .prefixes(new String[] {"author:details:"})
                                                        .build())
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("already exists"));

        // create an index without fields
        exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                FT.create(
                                                client,
                                                UUID.randomUUID().toString(),
                                                new FieldInfo[0],
                                                FTCreateOptions.builder()
                                                        .dataType(DataType.HASH)
                                                        .prefixes(new String[] {"empty:"})
                                                        .build())
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("schema must have at least one attribute"));

        // duplicated field name
        exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                FT.create(
                                                client,
                                                UUID.randomUUID().toString(),
                                                new FieldInfo[] {
                                                    new FieldInfo("name", new TextField()),
                                                    new FieldInfo("name", new TextField())
                                                },
                                                FTCreateOptions.builder()
                                                        .dataType(DataType.HASH)
                                                        .prefixes(new String[] {"dup:"})
                                                        .build())
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Duplicate: field in schema"));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        assertEquals(
                1L,
                client
                        .hset(
                                gs(prefix + 0),
                                createMap(
                                        gs("vec"),
                                        gs(
                                                new byte[] {
                                                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                                                    (byte) 0
                                                })))
                        .get());
        assertEquals(
                1L,
                client
                        .hset(
                                gs(prefix + 1),
                                createMap(
                                        gs("vec"),
                                        gs(
                                                new byte[] {
                                                    (byte) 0,
                                                    (byte) 0,
                                                    (byte) 0,
                                                    (byte) 0,
                                                    (byte) 0,
                                                    (byte) 0,
                                                    (byte) 0x80,
                                                    (byte) 0xBF
                                                })))
                        .get());
        Thread.sleep(DATA_PROCESSING_TIMEOUT); // let server digest the data and update index

        // FT.SEARCH hash_idx1 "*=>[KNN 2 @VEC $query_vec]" PARAMS 2 query_vec
        // "\x00\x00\x00\x00\x00\x00\x00\x00" DIALECT 2
        FTSearchOptions options =
                FTSearchOptions.builder()
                        .params(
                                createMap(
                                        gs("query_vec"),
                                        gs(
                                                new byte[] {
                                                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                                                    (byte) 0
                                                })))
                        .addReturnField("vec")
                        .build();
        String query = "*=>[KNN 2 @VEC $query_vec]";
        Object[] ftsearch = FT.search(client, index, query, options).get();

        assertArrayEquals(
                new Object[] {
                    2L,
                    createMap(
                            gs(prefix + 0),
                            createMap(gs("vec"), gs("\0\0\0\0\0\0\0\0")),
                            gs(prefix + 1),
                            createMap(
                                    gs("vec"),
                                    gs(
                                            new byte[] {
                                                (byte) 0,
                                                (byte) 0,
                                                (byte) 0,
                                                (byte) 0,
                                                (byte) 0,
                                                (byte) 0,
                                                (byte) 0x80,
                                                (byte) 0xBF
                                            })))
                },
                ftsearch);

        // TODO more tests with json index

        // FT.PROFILE may not be available on all server versions (e.g. valkey-search).
        try {
            Object[] ftprofile = FT.profile(client, index, new FTProfileOptions(query, options)).get();
            assertArrayEquals(ftsearch, (Object[]) ftprofile[0]);
        } catch (ExecutionException e) {
            assertInstanceOf(RequestException.class, e.getCause());
        }

        // querying non-existing index
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> FT.search(client, UUID.randomUUID().toString(), "*").get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("not found in database"));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_nocontent(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";
        String key = prefix + "doc";

        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        assertEquals(1L, client.hset(gs(key), createMap(gs("title"), gs("hello world"))).get());
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        // NOCONTENT: only keys are returned, no field content
        Object[] result =
                FT.search(client, index, "hello", FTSearchOptions.builder().nocontent().build()).get();
        assertArrayEquals(new Object[] {1L, createMap(gs(key), createMap())}, result);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_dialect(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";
        String key = prefix + "doc";

        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        assertEquals(1L, client.hset(gs(key), createMap(gs("title"), gs("hello world"))).get());
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        // dialect 2 is accepted; assert the document is returned
        Object[] result =
                FT.search(client, index, "hello", FTSearchOptions.builder().dialect(2).build()).get();
        assertEquals(2, result.length);
        assertEquals(1L, result[0]);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_dialect_invalid(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        // dialect < 2 is not supported; expect an error
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                FT.search(client, index, "hello", FTSearchOptions.builder().dialect(1).build())
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("DIALECT"));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_drop_and_ft_list(BaseClient client) {
        GlideString index = gs(UUID.randomUUID().toString());
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("vec", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {"vec:"})
                                        .build())
                        .get());

        GlideString[] beforeArray = FT.list(client).get();
        Set<GlideString> before = new HashSet<>(Arrays.asList(beforeArray));

        assertEquals(OK, FT.dropindex(client, index).get());

        GlideString[] afterArray = FT.list(client).get();
        Set<GlideString> after = new HashSet<>(Arrays.asList(afterArray));

        assertFalse(after.contains(index));
        after.add(index);
        assertEquals(after, before);

        ExecutionException exception =
                assertThrows(ExecutionException.class, () -> FT.dropindex(client, index).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("not found in database"));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    @Disabled("AGGREGATE is currently not fully supported")
    public void ft_aggregate(BaseClient client) {
        String prefixBicycles = "{bicycles}:";
        String indexBicycles = prefixBicycles + UUID.randomUUID();
        String prefixMovies = "{movies}:";
        String indexMovies = prefixMovies + UUID.randomUUID();

        // FT.CREATE idx:bicycle ON JSON PREFIX 1 bicycle: SCHEMA $.model AS model TEXT $.description AS
        // description TEXT $.price AS price NUMERIC $.condition AS condition TAG SEPARATOR ,
        assertEquals(
                OK,
                FT.create(
                                client,
                                indexBicycles,
                                new FieldInfo[] {
                                    new FieldInfo("$.model", "model", new TextField()),
                                    new FieldInfo("$.price", "price", new NumericField()),
                                    new FieldInfo("$.condition", "condition", new TagField(',')),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.JSON)
                                        .prefixes(new String[] {prefixBicycles})
                                        .build())
                        .get());

        // TODO check JSON module loaded

        Json.set(
                        client,
                        prefixBicycles + 0,
                        ".",
                        "{\"brand\": \"Velorim\", \"model\": \"Jigger\", \"price\": 270, \"condition\":"
                                + " \"new\"}")
                .get();
        Json.set(
                        client,
                        prefixBicycles + 1,
                        ".",
                        "{\"brand\": \"Bicyk\", \"model\": \"Hillcraft\", \"price\": 1200, \"condition\":"
                                + " \"used\"}")
                .get();
        Json.set(
                        client,
                        prefixBicycles + 2,
                        ".",
                        "{\"brand\": \"Nord\", \"model\": \"Chook air 5\", \"price\": 815, \"condition\":"
                                + " \"used\"}")
                .get();
        Json.set(
                        client,
                        prefixBicycles + 3,
                        ".",
                        "{\"brand\": \"Eva\", \"model\": \"Eva 291\", \"price\": 3400, \"condition\":"
                                + " \"used\"}")
                .get();
        Json.set(
                        client,
                        prefixBicycles + 4,
                        ".",
                        "{\"brand\": \"Noka Bikes\", \"model\": \"Kahuna\", \"price\": 3200, \"condition\":"
                                + " \"used\"}")
                .get();
        Json.set(
                        client,
                        prefixBicycles + 5,
                        ".",
                        "{\"brand\": \"Breakout\", \"model\": \"XBN 2.1 Alloy\", \"price\": 810, \"condition\":"
                                + " \"new\"}")
                .get();
        Json.set(
                        client,
                        prefixBicycles + 6,
                        ".",
                        "{\"brand\": \"ScramBikes\", \"model\": \"WattBike\", \"price\": 2300, \"condition\":"
                                + " \"new\"}")
                .get();
        Json.set(
                        client,
                        prefixBicycles + 7,
                        ".",
                        "{\"brand\": \"Peaknetic\", \"model\": \"Secto\", \"price\": 430, \"condition\":"
                                + " \"new\"}")
                .get();
        Json.set(
                        client,
                        prefixBicycles + 8,
                        ".",
                        "{\"brand\": \"nHill\", \"model\": \"Summit\", \"price\": 1200, \"condition\":"
                                + " \"new\"}")
                .get();
        Json.set(
                        client,
                        prefixBicycles + 9,
                        ".",
                        "{\"model\": \"ThrillCycle\", \"brand\": \"BikeShind\", \"price\": 815, \"condition\":"
                                + " \"refurbished\"}")
                .get();

        Thread.sleep(DATA_PROCESSING_TIMEOUT); // let server digest the data and update index

        // FT.AGGREGATE idx:bicycle "*" LOAD 1 "__key" GROUPBY 1 "@condition" REDUCE COUNT 0 AS bicylces
        FTAggregateOptions options =
                FTAggregateOptions.builder()
                        .loadFields(new String[] {"__key"})
                        .addClause(
                                new GroupBy(
                                        new String[] {"@condition"},
                                        new Reducer[] {new Reducer("COUNT", new String[0], "bicycles")}))
                        .build();
        Object[] aggreg = FT.aggregate(client, indexBicycles, "*", options).get();
        // elements (maps in array) could be reordered, comparing as sets
        assertDeepEquals(
                new HashSet<>(
                        Arrays.asList(
                                createMap(gs("condition"), gs("new"), gs("bicycles"), 5.),
                                createMap(gs("condition"), gs("used"), gs("bicycles"), 4.),
                                createMap(gs("condition"), gs("refurbished"), gs("bicycles"), 1.))),
                new HashSet<>(Arrays.asList(aggreg)));

        // FT.CREATE idx:movie ON hash PREFIX 1 "movie:" SCHEMA title TEXT release_year NUMERIC rating
        // NUMERIC genre TAG votes NUMERIC
        assertEquals(
                OK,
                FT.create(
                                client,
                                indexMovies,
                                new FieldInfo[] {
                                    new FieldInfo("title", new TextField()),
                                    new FieldInfo("release_year", new NumericField()),
                                    new FieldInfo("rating", new NumericField()),
                                    new FieldInfo("genre", new TagField()),
                                    new FieldInfo("votes", new NumericField()),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefixMovies})
                                        .build())
                        .get());

        client
                .hset(
                        prefixMovies + 11002,
                        createMap(
                                "title",
                                "Star Wars: Episode V - The Empire Strikes Back",
                                "release_year",
                                "1980",
                                "genre",
                                "Action",
                                "rating",
                                "8.7",
                                "votes",
                                "1127635",
                                "imdb_id",
                                "tt0080684"))
                .get();
        client
                .hset(
                        prefixMovies + 11003,
                        createMap(
                                "title",
                                "The Godfather",
                                "release_year",
                                "1972",
                                "genre",
                                "Drama",
                                "rating",
                                "9.2",
                                "votes",
                                "1563839",
                                "imdb_id",
                                "tt0068646"))
                .get();
        client
                .hset(
                        prefixMovies + 11004,
                        createMap(
                                "title",
                                "Heat",
                                "release_year",
                                "1995",
                                "genre",
                                "Thriller",
                                "rating",
                                "8.2",
                                "votes",
                                "559490",
                                "imdb_id",
                                "tt0113277"))
                .get();
        client
                .hset(
                        prefixMovies + 11005,
                        createMap(
                                "title",
                                "Star Wars: Episode VI - Return of the Jedi",
                                "genre",
                                "Action",
                                "votes",
                                "906260",
                                "rating",
                                "8.3",
                                "release_year",
                                "1983",
                                "ibmdb_id",
                                "tt0086190"))
                .get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT); // let server digest the data and update index

        // FT.AGGREGATE idx:movie * LOAD * APPLY ceil(@rating) as r_rating GROUPBY 1 @genre REDUCE
        // COUNT 0 AS nb_of_movies REDUCE SUM 1 votes AS nb_of_votes REDUCE AVG 1 r_rating AS avg_rating
        // SORTBY 4 @avg_rating DESC @nb_of_votes DESC
        options =
                FTAggregateOptions.builder()
                        .loadAll()
                        .addClause(new Apply("ceil(@rating)", "r_rating"))
                        .addClause(
                                new GroupBy(
                                        new String[] {"@genre"},
                                        new Reducer[] {
                                            new Reducer("COUNT", new String[0], "nb_of_movies"),
                                            new Reducer("SUM", new String[] {"votes"}, "nb_of_votes"),
                                            new Reducer("AVG", new String[] {"r_rating"}, "avg_rating")
                                        }))
                        .addClause(
                                new SortBy(
                                        new SortProperty[] {
                                            new SortProperty("@avg_rating", SortOrder.DESC),
                                            new SortProperty("@nb_of_votes", SortOrder.DESC)
                                        }))
                        .build();
        aggreg = FT.aggregate(client, indexMovies, "*", options).get();
        // elements (maps in array) could be reordered, comparing as sets
        assertDeepEquals(
                new HashSet<>(
                        Arrays.asList(
                                createMap(
                                        gs("genre"),
                                        gs("Drama"),
                                        gs("nb_of_movies"),
                                        1.,
                                        gs("nb_of_votes"),
                                        1563839.,
                                        gs("avg_rating"),
                                        10.),
                                createMap(
                                        gs("genre"),
                                        gs("Action"),
                                        gs("nb_of_movies"),
                                        2.,
                                        gs("nb_of_votes"),
                                        2033895.,
                                        gs("avg_rating"),
                                        9.),
                                createMap(
                                        gs("genre"),
                                        gs("Thriller"),
                                        gs("nb_of_movies"),
                                        1.,
                                        gs("nb_of_votes"),
                                        559490.,
                                        gs("avg_rating"),
                                        9.))),
                new HashSet<>(Arrays.asList(aggreg)));

        Object[] ftprofile = FT.profile(client, indexMovies, new FTProfileOptions("*", options)).get();
        assertDeepEquals(aggreg, ftprofile[0]);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    @SneakyThrows
    public void ft_info(BaseClient client) {
        String index = UUID.randomUUID().toString();
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "$.vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.COSINE, 42).build()),
                                    new FieldInfo("$.name", "name", new TextField()),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.JSON)
                                        .prefixes(new String[] {"123"})
                                        .build())
                        .get());

        Map<String, Object> response = FT.info(client, index).get();
        assertEquals(gs(index), response.get("index_name"));
        assertTrue(Arrays.deepToString((Object[]) response.get("index_definition")).contains("JSON"));
        assertTrue(Arrays.deepToString((Object[]) response.get("index_definition")).contains("123"));

        // Verify field details via the "attributes" key
        String attributesStr = Arrays.deepToString((Object[]) response.get("attributes"));
        assertTrue(attributesStr.contains("$.vec"));
        assertTrue(attributesStr.contains("VEC"));
        assertTrue(attributesStr.contains("VECTOR"));
        assertTrue(attributesStr.contains("COSINE"));
        assertTrue(attributesStr.contains("42"));
        assertTrue(attributesStr.contains("$.name"));
        assertTrue(attributesStr.contains("TEXT"));

        // querying a missing index
        assertEquals(OK, FT.dropindex(client, index).get());
        ExecutionException exception =
                assertThrows(ExecutionException.class, () -> FT.info(client, index).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("not found in database"));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    @Disabled("ALIAS is currently unsupported")
    public void ft_aliasadd_aliasdel_aliasupdate_aliaslist(BaseClient client) {

        String alias1 = "alias1";
        String alias2 = "a2";
        String indexName = "{" + UUID.randomUUID() + "-index}";

        // create some indices
        assertEquals(
                OK,
                FT.create(
                                client,
                                indexName,
                                new FieldInfo[] {
                                    new FieldInfo("vec", VectorFieldFlat.builder(DistanceMetric.L2, 2).build())
                                })
                        .get());

        assertEquals(0, FT.aliaslist(client).get().size());
        assertEquals(OK, FT.aliasadd(client, alias1, indexName).get());
        assertEquals(createMap(gs(alias1), gs(indexName)), FT.aliaslist(client).get());

        // error with adding the same alias to the same index
        ExecutionException exception =
                assertThrows(ExecutionException.class, () -> FT.aliasadd(client, alias1, indexName).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Alias already exists"));

        assertEquals(OK, FT.aliasupdate(client, alias2, indexName).get());
        assertEquals(
                createMap(gs(alias1), gs(indexName), gs(alias2), gs(indexName)),
                FT.aliaslist(client).get());
        assertEquals(OK, FT.aliasdel(client, alias2).get());

        // with GlideString:
        assertEquals(OK, FT.aliasupdate(client, gs(alias1), gs(indexName)).get());
        assertEquals(OK, FT.aliasdel(client, gs(alias1)).get());
        assertEquals(OK, FT.aliasadd(client, gs(alias2), gs(indexName)).get());
        assertEquals(OK, FT.aliasdel(client, gs(alias2)).get());

        // exception with calling `aliasdel` on an alias that doesn't exist
        exception = assertThrows(ExecutionException.class, () -> FT.aliasdel(client, alias2).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Alias does not exist"));

        // exception with calling `aliasadd` with a nonexisting index
        exception =
                assertThrows(
                        ExecutionException.class, () -> FT.aliasadd(client, alias1, "nonexistent_index").get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index does not exist"));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    @Disabled("EXPLAIN is not supported yet")
    public void ft_explain(BaseClient client) {

        String indexName = UUID.randomUUID().toString();
        createIndexHelper(client, indexName);

        // search query containing numeric field.
        String query = "@price:[0 10]";
        String result = FT.explain(client, indexName, query).get();
        assertTrue(result.contains("price"));
        assertTrue(result.contains("0"));
        assertTrue(result.contains("10"));

        GlideString resultGS = FT.explain(client, gs(indexName), gs(query)).get();
        assertTrue((resultGS).toString().contains("price"));
        assertTrue((resultGS).toString().contains("0"));
        assertTrue((resultGS).toString().contains("10"));

        // search query that returns all data.
        GlideString resultGSAllData = FT.explain(client, gs(indexName), gs("*")).get();
        assertTrue(resultGSAllData.toString().contains("*"));

        assertEquals(OK, FT.dropindex(client, indexName).get());

        // missing index throws an error.
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> FT.explain(client, UUID.randomUUID().toString(), "*").get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index not found"));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    @Disabled("EXPLAIN is currently unsupported")
    public void ft_explaincli(BaseClient client) {

        String indexName = UUID.randomUUID().toString();
        createIndexHelper(client, indexName);

        // search query containing numeric field.
        String query = "@price:[0 10]";
        String[] result = FT.explaincli(client, indexName, query).get();
        List<String> resultList = Arrays.stream(result).map(String::trim).collect(Collectors.toList());

        assertTrue(resultList.contains("price"));
        assertTrue(resultList.contains("0"));
        assertTrue(resultList.contains("10"));

        GlideString[] resultGS = FT.explaincli(client, gs(indexName), gs(query)).get();
        List<String> resultListGS =
                Arrays.stream(resultGS)
                        .map(GlideString::toString)
                        .map(String::trim)
                        .collect(Collectors.toList());

        assertTrue((resultListGS).contains("price"));
        assertTrue((resultListGS).contains("0"));
        assertTrue((resultListGS).contains("10"));

        // search query that returns all data.
        GlideString[] resultGSAllData = FT.explaincli(client, gs(indexName), gs("*")).get();
        List<String> resultListGSAllData =
                Arrays.stream(resultGSAllData)
                        .map(GlideString::toString)
                        .map(String::trim)
                        .collect(Collectors.toList());
        assertTrue((resultListGSAllData).contains("*"));

        assertEquals(OK, FT.dropindex(client, indexName).get());

        // missing index throws an error.
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> FT.explaincli(client, UUID.randomUUID().toString(), "*").get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index not found"));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_1_2_sortby(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("price", new NumericField(true)),
                                    new FieldInfo("name", new TextField()),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        client.hset(prefix + "1", createMap("price", "10", "name", "Aardvark")).get();
        client.hset(prefix + "2", createMap("price", "20", "name", "Mango")).get();
        client.hset(prefix + "3", createMap("price", "30", "name", "Zebra")).get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        // SORTBY price ASC
        FTSearchOptions options =
                FTSearchOptions.builder().sortBy("price", FTSearchOptions.SortOrder.ASC).build();
        Object[] result = FT.search(client, index, "@price:[1 +inf]", options).get();
        assertEquals(3L, result[0]);
        @SuppressWarnings("unchecked")
        Map<GlideString, Map<GlideString, GlideString>> docs =
                (Map<GlideString, Map<GlideString, GlideString>>) result[1];
        GlideString firstKey = docs.keySet().iterator().next();
        assertEquals(gs("10"), docs.get(firstKey).get(gs("price")));

        // SORTBY price DESC
        options = FTSearchOptions.builder().sortBy("price", FTSearchOptions.SortOrder.DESC).build();
        result = FT.search(client, index, "@price:[1 +inf]", options).get();
        assertEquals(3L, result[0]);
        docs = (Map<GlideString, Map<GlideString, GlideString>>) result[1];
        // First result should have highest price (30)
        firstKey = docs.keySet().iterator().next();
        assertEquals(gs("30"), docs.get(firstKey).get(gs("price")));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_1_2_withsortkeys(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("price", new NumericField(true)),
                                    new FieldInfo("name", new TextField()),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        client.hset(prefix + "1", createMap("price", "10", "name", "Aardvark")).get();
        client.hset(prefix + "2", createMap("price", "20", "name", "Mango")).get();
        client.hset(prefix + "3", createMap("price", "30", "name", "Zebra")).get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        // WITHSORTKEYS — each doc value becomes [sortKey, fieldMap]
        FTSearchOptions options =
                FTSearchOptions.builder()
                        .sortBy("price", FTSearchOptions.SortOrder.ASC)
                        .withSortKeys()
                        .build();
        Object[] result = FT.search(client, index, "@price:[1 +inf]", options).get();
        assertEquals(3L, result[0]);

        // With WITHSORTKEYS the doc map values are Object[] { sortKey, fieldMap }
        @SuppressWarnings("unchecked")
        Map<GlideString, Object[]> docs = (Map<GlideString, Object[]>) result[1];
        assertEquals(3, docs.size());

        // Verify sort keys are in ascending order and field maps are accessible
        int i = 0;
        String[] expectedPrices = {"10", "20", "30"};
        for (Map.Entry<GlideString, Object[]> entry : docs.entrySet()) {
            Object[] pair = entry.getValue();
            // pair[0] is the sort key (numeric sort keys are prefixed with #)
            GlideString sortKey = (GlideString) pair[0];
            assertTrue(sortKey.toString().contains(expectedPrices[i]));
            // pair[1] is the field map
            @SuppressWarnings("unchecked")
            Map<GlideString, GlideString> fields = (Map<GlideString, GlideString>) pair[1];
            assertEquals(gs(expectedPrices[i]), fields.get(gs("price")));
            i++;
        }

        assertEquals(OK, FT.dropindex(client, index).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search_1_2_text_query_flags(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("title", new TextField()),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        client.hset(prefix + "1", createMap("title", "hello world")).get();
        client.hset(prefix + "2", createMap("title", "hello there")).get();
        client.hset(prefix + "3", createMap("title", "goodbye world")).get();
        client.hset(prefix + "4", createMap("title", "world hello")).get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        // VERBATIM - no stemming
        FTSearchOptions verbatimOptions = FTSearchOptions.builder().verbatim().build();
        Object[] verbatimResult = FT.search(client, index, "hello", verbatimOptions).get();
        assertEquals(3L, (Long) verbatimResult[0]); // hello world, hello there, world hello

        // SLOP without INORDER
        FTSearchOptions noInorderOptions = FTSearchOptions.builder().slop(1).build();
        Object[] noInorderResult = FT.search(client, index, "hello world", noInorderOptions).get();
        assertEquals(2L, (Long) noInorderResult[0]); // world hello, hello world

        // SLOP with INORDER
        FTSearchOptions inorderOptions = FTSearchOptions.builder().inorder().slop(1).build();
        Object[] inorderResult = FT.search(client, index, "hello world", inorderOptions).get();
        assertEquals(1L, (Long) inorderResult[0]); // hello world
    }

    /** Shard/consistency options are cluster-only concepts. */
    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClusterClients")
    public void ft_search_1_2_shard_consistency(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("tag", new TagField()), new FieldInfo("score", new NumericField()),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        client.hset(prefix + "1", createMap("tag", "test", "score", "1")).get();
        client.hset(prefix + "2", createMap("tag", "test", "score", "2")).get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        // SOMESHARDS + INCONSISTENT
        FTSearchOptions options =
                FTSearchOptions.builder()
                        .shardScope(FTSearchOptions.ShardScope.SOMESHARDS)
                        .consistency(FTSearchOptions.ConsistencyMode.INCONSISTENT)
                        .build();
        Object[] result = FT.search(client, index, "@tag:{test}", options).get();

        // In a healthy cluster, SOMESHARDS still returns all results.
        // This test verifies the option is accepted; partial results only occur with unavailable
        // shards.
        // If it ends up being flakey, relax the equality to a GEQ check
        assertEquals(2L, (Long) result[0]);

        // ALLSHARDS + CONSISTENT (defaults)
        options =
                FTSearchOptions.builder()
                        .shardScope(FTSearchOptions.ShardScope.ALLSHARDS)
                        .consistency(FTSearchOptions.ConsistencyMode.CONSISTENT)
                        .build();
        result = FT.search(client, index, "@tag:{test}", options).get();
        assertEquals(2L, (Long) result[0]);
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_aggregate_1_2_query_flags(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("score", new NumericField()),
                                    new FieldInfo("title", new TextField()),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        client.hset(prefix + "1", createMap("score", "10", "title", "hello world")).get();
        client.hset(prefix + "2", createMap("score", "20", "title", "hello there")).get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        // VERBATIM - disables stemming on the query
        FTAggregateOptions verbatimOptions = FTAggregateOptions.builder().verbatim().build();
        Map<GlideString, Object>[] result =
                FT.aggregate(client, index, "@score:[1 +inf]", verbatimOptions).get();
        // Both docs match; no LOAD so each record is an empty map
        assertEquals(2, result.length);
        assertTrue(result[0].isEmpty());
        assertTrue(result[1].isEmpty());

        // INORDER + SLOP - proximity matching flags
        FTAggregateOptions inorderOptions = FTAggregateOptions.builder().inorder().slop(1).build();
        result = FT.aggregate(client, index, "@score:[1 +inf]", inorderOptions).get();
        assertEquals(2, result.length);
        assertTrue(result[0].isEmpty());
        assertTrue(result[1].isEmpty());

        // DIALECT
        FTAggregateOptions dialectOptions = FTAggregateOptions.builder().dialect(2).build();
        result = FT.aggregate(client, index, "@score:[1 +inf]", dialectOptions).get();
        assertEquals(2, result.length);
        assertTrue(result[0].isEmpty());
        assertTrue(result[1].isEmpty());

        // LOAD
        FTAggregateOptions loadOptions = FTAggregateOptions.builder().loadAll().build();
        result = FT.aggregate(client, index, "@score:[20 +inf]", loadOptions).get();
        assertEquals(1, result.length);
        assertFalse(result[0].isEmpty());
        assertEquals(gs("hello there"), result[0].get(gs("title")));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_create_1_2_index_options(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        // Test SCORE, LANGUAGE, SKIPINITIALSCAN are accepted by the server
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .score(1.0)
                                        .language("english")
                                        .skipInitialScan(true)
                                        .build())
                        .get());
        assertEquals(OK, FT.dropindex(client, index).get());

        // Test MINSTEMSIZE
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .minStemSize(6)
                                        .build())
                        .get());
        client.hset(prefix + "1", createMap("title", "running")).get();
        client.hset(prefix + "2", createMap("title", "plays")).get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        Object[] result = FT.search(client, index, "run").get();
        assertEquals(1L, (Long) result[0]);

        result = FT.search(client, index, "play").get();
        assertEquals(0L, (Long) result[0]);
        assertEquals(OK, FT.dropindex(client, index).get());

        // Test NOSTOPWORDS
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .noStopWords(true)
                                        .build())
                        .get());
        client.hset(prefix + "1", createMap("title", "the quick fox")).get();
        assertEquals(1L, (Long) FT.search(client, index, "the").get()[0]);
        assertEquals(OK, FT.dropindex(client, index).get());

        // Test STOPWORDS
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .stopWords(new String[] {"fox", "an"})
                                        .build())
                        .get());
        client.hset(prefix + "1", createMap("title", "the quick fox")).get();
        assertEquals(1L, (Long) FT.search(client, index, "the").get()[0]);
        assertEquals(1L, (Long) FT.search(client, index, "quick").get()[0]);

        // RequestException: Filter epression 'fox' invalid query.
        assertThrows(ExecutionException.class, () -> FT.search(client, index, "fox").get());
        assertEquals(OK, FT.dropindex(client, index).get());

        // Test NOOFFSETS
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .noOffsets(true)
                                        .build())
                        .get());
        client.hset(prefix + "1", createMap("title", "hello")).get();
        assertEquals(
                1L, (Long) FT.search(client, index, "hello", FTSearchOptions.builder().build()).get()[0]);
        assertThrows(
                ExecutionException.class,
                () -> FT.search(client, index, "hello", FTSearchOptions.builder().slop(1).build()).get());
        assertEquals(OK, FT.dropindex(client, index).get());
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_create_1_2_field_options(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        // TextField with nostem, weight, sortable
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "title",
                                            new TextField(
                                                    /* noStem= */ true,
                                                    /* weight= */ 1.0,
                                                    /* withSuffixTrie= */ false,
                                                    /* noSuffixTrie= */ false,
                                                    /* sortable= */ true)),
                                    new FieldInfo("price", new NumericField(/* sortable= */ true)),
                                    new FieldInfo("tag", new TagField(',', false, /* sortable= */ true)),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        client.hset(prefix + "1", createMap("title", "hello", "price", "10", "tag", "a,b")).get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        // Verify index works with sortable fields
        FTSearchOptions options =
                FTSearchOptions.builder().sortBy("price", FTSearchOptions.SortOrder.ASC).build();
        Object[] result = FT.search(client, index, "@price:[1 +inf]", options).get();
        assertEquals(1L, (Long) result[0]);

        // noStemming
        result = FT.search(client, index, "hello").get();
        assertEquals(1L, (Long) result[0]);
        result = FT.search(client, index, "hellos").get();
        assertEquals(0L, (Long) result[0]);

        assertEquals(OK, FT.dropindex(client, index).get());

        // TextField with withSuffixTrie
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "title",
                                            new TextField(
                                                    /* noStem= */ false,
                                                    /* weight= */ null,
                                                    /* withSuffixTrie= */ true,
                                                    /* noSuffixTrie= */ false,
                                                    /* sortable= */ false)),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());
        client.hset(prefix + "1", createMap("title", "hello world")).get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        // Suffix query - should work with suffix trie
        result = FT.search(client, index, "*orld").get();
        assertEquals(1L, (Long) result[0]);
        assertEquals(OK, FT.dropindex(client, index).get());

        // TextField with NOSUFFIXTRIE
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "title",
                                            new TextField(
                                                    /* noStem= */ false,
                                                    /* weight= */ null,
                                                    /* withSuffixTrie= */ false,
                                                    /* noSuffixTrie= */ true,
                                                    /* sortable= */ false)),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());
        client.hset(prefix + "1", createMap("title", "hello world")).get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        // Suffix query - should NOT work with NOSUFFIXTRIE
        assertThrows(ExecutionException.class, () -> FT.search(client, index, "*orld").get());

        assertEquals(OK, FT.dropindex(client, index).get());
    }

    /** FT.INFO with scope options is a cluster-only feature. */
    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClusterClients")
    public void ft_info_1_2_options(BaseClient client) {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {new FieldInfo("title", new TextField())},
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        client.hset(prefix + "1", createMap("title", "hello world")).get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT);

        // LOCAL scope — returns detailed per-node info with fields, num_docs, etc.
        Map<String, Object> localInfo =
                FT.info(client, index, new FTInfoOptions(FTInfoOptions.InfoScope.LOCAL)).get();
        assertEquals(gs(index), localInfo.get("index_name"));
        assertTrue(localInfo.containsKey("num_docs"));

        // LOCAL with ALLSHARDS + CONSISTENT — smoke test that these flags are accepted
        Map<String, Object> localWithFlags =
                FT.info(
                                client,
                                index,
                                new FTInfoOptions(
                                        FTInfoOptions.InfoScope.LOCAL,
                                        FTInfoOptions.ShardScope.ALLSHARDS,
                                        FTInfoOptions.ConsistencyMode.CONSISTENT))
                        .get();
        assertEquals(gs(index), localWithFlags.get("index_name"));

        // LOCAL with SOMESHARDS + INCONSISTENT — smoke test
        Map<String, Object> localWithAltFlags =
                FT.info(
                                client,
                                index,
                                new FTInfoOptions(
                                        FTInfoOptions.InfoScope.LOCAL,
                                        FTInfoOptions.ShardScope.SOMESHARDS,
                                        FTInfoOptions.ConsistencyMode.INCONSISTENT))
                        .get();
        assertEquals(gs(index), localWithAltFlags.get("index_name"));

        // PRIMARY and CLUSTER scopes require the coordinator to be enabled
        // (use-coordinator module arg). If available, verify the response shape;
        // otherwise, verify the server rejects with the expected error.
        try {
            Map<String, Object> primaryInfo =
                    FT.info(client, index, new FTInfoOptions(FTInfoOptions.InfoScope.PRIMARY)).get();
            assertEquals(gs(index), primaryInfo.get("index_name"));
            assertEquals(gs("PRIMARY"), primaryInfo.get("mode"));
        } catch (ExecutionException e) {
            assertInstanceOf(RequestException.class, e.getCause());
            assertTrue(e.getMessage().contains("PRIMARY option is not valid"));
        }

        try {
            Map<String, Object> clusterInfo =
                    FT.info(client, index, new FTInfoOptions(FTInfoOptions.InfoScope.CLUSTER)).get();
            assertEquals(gs(index), clusterInfo.get("index_name"));
            assertEquals(gs("CLUSTER"), clusterInfo.get("mode"));
        } catch (ExecutionException e) {
            assertInstanceOf(RequestException.class, e.getCause());
            assertTrue(e.getMessage().contains("CLUSTER option is not valid"));
        }

        assertEquals(OK, FT.dropindex(client, index).get());
    }

    private void createIndexHelper(BaseClient client, String indexName)
            throws ExecutionException, InterruptedException {
        FieldInfo numericField = new FieldInfo("price", new NumericField());
        FieldInfo textField = new FieldInfo("title", new TextField());

        FieldInfo[] fields = new FieldInfo[] {numericField, textField};

        // Prefix must not contain a hash tag when the index name is not hash-tagged.
        String prefix = "hash-search-" + UUID.randomUUID().toString() + ":";

        assertEquals(
                OK,
                FT.create(
                                client,
                                indexName,
                                fields,
                                FTCreateOptions.builder()
                                        .dataType(DataType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());
    }
}
