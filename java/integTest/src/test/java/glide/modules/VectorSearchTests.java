/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.assertDeepEquals;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import glide.api.models.commands.FT.FTProfileOptions;
import glide.api.models.commands.FT.FTSearchOptions;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.exceptions.RequestException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class VectorSearchTests {

    private static GlideClusterClient client;

    /** Waiting interval to let server process the data before querying */
    private static final int DATA_PROCESSING_TIMEOUT = 1000; // ms

    @BeforeAll
    @SneakyThrows
    public static void init() {
        client =
                GlideClusterClient.createClient(commonClusterClientConfig().requestTimeout(5000).build())
                        .get();
        client.flushall(FlushMode.SYNC, ALL_PRIMARIES).get();
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        client.flushall(FlushMode.SYNC, ALL_PRIMARIES).get();
        client.close();
    }

    @Test
    @SneakyThrows
    public void check_module_loaded() {
        var info = client.info(new Section[] {Section.MODULES}, RANDOM).get().getSingleValue();
        assertTrue(info.contains("# search_index_stats"));
    }

    @SneakyThrows
    @Test
    public void ft_create() {
        // create few simple indices
        assertEquals(
                OK,
                FT.create(
                                client,
                                UUID.randomUUID().toString(),
                                new FieldInfo[] {
                                    new FieldInfo("vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                })
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
        var index = UUID.randomUUID().toString();
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
        var exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                FT.create(
                                                client,
                                                index,
                                                new FieldInfo[] {
                                                    new FieldInfo("title", new TextField()),
                                                    new FieldInfo("name", new TextField())
                                                })
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("already exists"));

        // create an index without fields
        exception =
                assertThrows(
                        ExecutionException.class,
                        () -> FT.create(client, UUID.randomUUID().toString(), new FieldInfo[0]).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("wrong number of arguments"));

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
                                                })
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("already exists"));
    }

    @SneakyThrows
    @Test
    public void ft_search() {
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
                                Map.of(
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
                                Map.of(
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
        var options =
                FTSearchOptions.builder()
                        .params(
                                Map.of(
                                        gs("query_vec"),
                                        gs(
                                                new byte[] {
                                                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                                                    (byte) 0
                                                })))
                        .build();
        var query = "*=>[KNN 2 @VEC $query_vec]";
        var ftsearch = FT.search(client, index, query, options).get();

        assertArrayEquals(
                new Object[] {
                    2L,
                    Map.of(
                            gs(prefix + 0),
                            Map.of(gs("__VEC_score"), gs("0"), gs("vec"), gs("\0\0\0\0\0\0\0\0")),
                            gs(prefix + 1),
                            Map.of(
                                    gs("__VEC_score"),
                                    gs("1"),
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

        var ftprofile = FT.profile(client, index, new FTProfileOptions(query, options)).get();
        assertArrayEquals(ftsearch, (Object[]) ftprofile[0]);

        // querying non-existing index
        var exception =
                assertThrows(
                        ExecutionException.class,
                        () -> FT.search(client, UUID.randomUUID().toString(), "*").get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index not found"));
    }

    @SneakyThrows
    @Test
    public void ft_drop_and_ft_list() {
        var index = gs(UUID.randomUUID().toString());
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("vec", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                })
                        .get());

        var before = Set.of(FT.list(client).get());

        assertEquals(OK, FT.dropindex(client, index).get());

        var after = new HashSet<>(Set.of(FT.list(client).get()));

        assertFalse(after.contains(index));
        after.add(index);
        assertEquals(after, before);

        var exception = assertThrows(ExecutionException.class, () -> FT.dropindex(client, index).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index does not exist"));
    }

    @SneakyThrows
    @Test
    public void ft_aggregate() {
        var prefixBicycles = "{bicycles}:";
        var indexBicycles = prefixBicycles + UUID.randomUUID();
        var prefixMovies = "{movies}:";
        var indexMovies = prefixMovies + UUID.randomUUID();

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
        var options =
                FTAggregateOptions.builder()
                        .loadFields(new String[] {"__key"})
                        .addClause(
                                new GroupBy(
                                        new String[] {"@condition"},
                                        new Reducer[] {new Reducer("COUNT", new String[0], "bicycles")}))
                        .build();
        var aggreg = FT.aggregate(client, indexBicycles, "*", options).get();
        // elements (maps in array) could be reordered, comparing as sets
        assertDeepEquals(
                Set.of(
                        Map.of(gs("condition"), gs("new"), gs("bicycles"), 5.),
                        Map.of(gs("condition"), gs("used"), gs("bicycles"), 4.),
                        Map.of(gs("condition"), gs("refurbished"), gs("bicycles"), 1.)),
                Set.of(aggreg));

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
                        Map.of(
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
                        Map.of(
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
                        Map.of(
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
                        Map.of(
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
                Set.of(
                        Map.of(
                                gs("genre"),
                                gs("Drama"),
                                gs("nb_of_movies"),
                                1.,
                                gs("nb_of_votes"),
                                1563839.,
                                gs("avg_rating"),
                                10.),
                        Map.of(
                                gs("genre"),
                                gs("Action"),
                                gs("nb_of_movies"),
                                2.,
                                gs("nb_of_votes"),
                                2033895.,
                                gs("avg_rating"),
                                9.),
                        Map.of(
                                gs("genre"),
                                gs("Thriller"),
                                gs("nb_of_movies"),
                                1.,
                                gs("nb_of_votes"),
                                559490.,
                                gs("avg_rating"),
                                9.)),
                Set.of(aggreg));

        var ftprofile = FT.profile(client, indexMovies, new FTProfileOptions("*", options)).get();
        assertDeepEquals(aggreg, ftprofile[0]);
    }

    @SuppressWarnings("unchecked")
    @Test
    @SneakyThrows
    public void ft_info() {
        var index = UUID.randomUUID().toString();
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "$.vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.COSINE, 42).build()),
                                    new FieldInfo("$.name", new TextField()),
                                },
                                FTCreateOptions.builder()
                                        .dataType(DataType.JSON)
                                        .prefixes(new String[] {"123"})
                                        .build())
                        .get());

        var response = FT.info(client, index).get();
        assertEquals(gs(index), response.get("index_name"));
        assertEquals(gs("JSON"), response.get("key_type"));
        assertArrayEquals(new GlideString[] {gs("123")}, (Object[]) response.get("key_prefixes"));
        var fields = (Object[]) response.get("fields");
        assertEquals(2, fields.length);
        var f1 = (Map<GlideString, Object>) fields[1];
        assertEquals(gs("$.vec"), f1.get(gs("identifier")));
        assertEquals(gs("VECTOR"), f1.get(gs("type")));
        assertEquals(gs("VEC"), f1.get(gs("field_name")));
        var f1params = (Map<GlideString, Object>) f1.get(gs("vector_params"));
        assertEquals(gs("COSINE"), f1params.get(gs("distance_metric")));
        assertEquals(42L, f1params.get(gs("dimension")));

        assertEquals(
                Map.of(
                        gs("identifier"),
                        gs("$.name"),
                        gs("type"),
                        gs("TEXT"),
                        gs("field_name"),
                        gs("$.name"),
                        gs("option"),
                        gs("")),
                fields[0]);

        // querying a missing index
        assertEquals(OK, FT.dropindex(client, index).get());
        var exception = assertThrows(ExecutionException.class, () -> FT.info(client, index).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index not found"));
    }

    @SneakyThrows
    @Test
    public void ft_aliasadd_aliasdel_aliasupdate_aliaslist() {

        var alias1 = "alias1";
        var alias2 = "a2";
        var indexName = "{" + UUID.randomUUID() + "-index}";

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
        assertEquals(Map.of(gs(alias1), gs(indexName)), FT.aliaslist(client).get());

        // error with adding the same alias to the same index
        var exception =
                assertThrows(ExecutionException.class, () -> FT.aliasadd(client, alias1, indexName).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Alias already exists"));

        assertEquals(OK, FT.aliasupdate(client, alias2, indexName).get());
        assertEquals(
                Map.of(gs(alias1), gs(indexName), gs(alias2), gs(indexName)), FT.aliaslist(client).get());
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
    @Test
    public void ft_explain() {

        String indexName = UUID.randomUUID().toString();
        createIndexHelper(indexName);

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
        var exception =
                assertThrows(
                        ExecutionException.class,
                        () -> FT.explain(client, UUID.randomUUID().toString(), "*").get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index not found"));
    }

    @SneakyThrows
    @Test
    public void ft_explaincli() {

        String indexName = UUID.randomUUID().toString();
        createIndexHelper(indexName);

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
        var exception =
                assertThrows(
                        ExecutionException.class,
                        () -> FT.explaincli(client, UUID.randomUUID().toString(), "*").get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index not found"));
    }

    private void createIndexHelper(String indexName) throws ExecutionException, InterruptedException {
        FieldInfo numericField = new FieldInfo("price", new NumericField());
        FieldInfo textField = new FieldInfo("title", new TextField());

        FieldInfo[] fields = new FieldInfo[] {numericField, textField};

        String prefix = "{hash-search-" + UUID.randomUUID().toString() + "}:";

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
