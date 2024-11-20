/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands.servermodules;

import static glide.api.models.GlideString.gs;
import static glide.utils.ArrayTransformUtils.castArray;
import static glide.utils.ArrayTransformUtils.concatenateArrays;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.FT.FTAggregateOptions;
import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
import glide.api.models.commands.FT.FTProfileOptions;
import glide.api.models.commands.FT.FTSearchOptions;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;

/** Module for vector search commands. */
public class FT {
    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param schema Fields to populate into the index. Equivalent to `SCHEMA` block in the module
     *     API.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * // Create an index for vectors of size 2:
     * FT.create(client, "my_idx1", new FieldInfo[] {
     *     new FieldInfo("vec", VectorFieldFlat.builder(DistanceMetric.L2, 2).build())
     * }).get();
     *
     * // Create a 6-dimensional JSON index using the HNSW algorithm:
     * FT.create(client, "my_idx2",
     *     new FieldInfo[] { new FieldInfo("$.vec", "VEC",
     *         VectorFieldHnsw.builder(DistanceMetric.L2, 6).numberOfEdges(32).build())
     * }).get();
     * }</pre>
     */
    public static CompletableFuture<String> create(
            @NonNull BaseClient client, @NonNull String indexName, @NonNull FieldInfo[] schema) {
        // Node: bug in meme DB - command fails if cmd is too short even though all mandatory args are
        // present
        // TODO confirm is it fixed or not and update docs if needed
        return create(client, indexName, schema, FTCreateOptions.builder().build());
    }

    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param schema Fields to populate into the index. Equivalent to `SCHEMA` block in the module
     *     API.
     * @param options Additional parameters for the command - see {@link FTCreateOptions}.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * // Create a 6-dimensional JSON index using the HNSW algorithm:
     * FT.create(client, "json_idx1",
     *     new FieldInfo[] { new FieldInfo("$.vec", "VEC",
     *         VectorFieldHnsw.builder(DistanceMetric.L2, 6).numberOfEdges(32).build())
     *     },
     *     FTCreateOptions.builder().dataType(JSON).prefixes(new String[] {"json:"}).build(),
     * ).get();
     * }</pre>
     */
    public static CompletableFuture<String> create(
            @NonNull BaseClient client,
            @NonNull String indexName,
            @NonNull FieldInfo[] schema,
            @NonNull FTCreateOptions options) {
        return create(client, gs(indexName), schema, options);
    }

    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param schema Fields to populate into the index. Equivalent to `SCHEMA` block in the module
     *     API.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * // Create an index for vectors of size 2:
     * FT.create(client, gs("my_idx1"), new FieldInfo[] {
     *     new FieldInfo("vec", VectorFieldFlat.builder(DistanceMetric.L2, 2).build())
     * }).get();
     *
     * // Create a 6-dimensional JSON index using the HNSW algorithm:
     * FT.create(client, gs("my_idx2"),
     *     new FieldInfo[] { new FieldInfo(gs("$.vec"), gs("VEC"),
     *         VectorFieldHnsw.builder(DistanceMetric.L2, 6).numberOfEdges(32).build())
     * }).get();
     * }</pre>
     */
    public static CompletableFuture<String> create(
            @NonNull BaseClient client, @NonNull GlideString indexName, @NonNull FieldInfo[] schema) {
        // Node: bug in meme DB - command fails if cmd is too short even though all mandatory args are
        // present
        // TODO confirm is it fixed or not and update docs if needed
        return create(client, indexName, schema, FTCreateOptions.builder().build());
    }

    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param schema Fields to populate into the index. Equivalent to `SCHEMA` block in the module
     *     API.
     * @param options Additional parameters for the command - see {@link FTCreateOptions}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * // Create a 6-dimensional JSON index using the HNSW algorithm:
     * FT.create(client, gs("json_idx1"),
     *     new FieldInfo[] { new FieldInfo(gs("$.vec"), gs("VEC"),
     *         VectorFieldHnsw.builder(DistanceMetric.L2, 6).numberOfEdges(32).build())
     *     },
     *     FTCreateOptions.builder().dataType(JSON).prefixes(new String[] {"json:"}).build(),
     * ).get();
     * }</pre>
     */
    public static CompletableFuture<String> create(
            @NonNull BaseClient client,
            @NonNull GlideString indexName,
            @NonNull FieldInfo[] schema,
            @NonNull FTCreateOptions options) {
        var args =
                Stream.of(
                                new GlideString[] {gs("FT.CREATE"), indexName},
                                options.toArgs(),
                                new GlideString[] {gs("SCHEMA")},
                                Arrays.stream(schema)
                                        .map(FieldInfo::toArgs)
                                        .flatMap(Arrays::stream)
                                        .toArray(GlideString[]::new))
                        .flatMap(Arrays::stream)
                        .toArray(GlideString[]::new);
        return executeCommand(client, args, false);
    }

    /**
     * Uses the provided query expression to locate keys within an index. Once located, the count
     * and/or content of indexed fields within those keys can be returned.
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @param options The search options - see {@link FTSearchOptions}.
     * @return A two element array, where first element is count of documents in result set, and the
     *     second element, which has format <code>
     *     {@literal Map<GlideString, Map<GlideString, GlideString>>}</code> - a mapping between
     *     document names and map of their attributes.<br>
     *     If {@link FTSearchOptions.FTSearchOptionsBuilder#count()} or {@link
     *     FTSearchOptions.FTSearchOptionsBuilder#limit(int, int)} with values <code>0, 0</code> is
     *     set, the command returns array with only one element - the count of the documents.
     * @example
     *     <pre>{@code
     * byte[] vector = new byte[24];
     * Arrays.fill(vector, (byte) 0);
     * var result = FT.search(client, "json_idx1", "*=>[KNN 2 @VEC $query_vec]",
     *         FTSearchOptions.builder().params(Map.of(gs("query_vec"), gs(vector))).build())
     *     .get();
     * assertArrayEquals(result, new Object[] { 2L, Map.of(
     *     gs("json:2"), Map.of(gs("__VEC_score"), gs("11.1100006104"), gs("$"), gs("{\"vec\":[1.1,1.2,1.3,1.4,1.5,1.6]}")),
     *     gs("json:0"), Map.of(gs("__VEC_score"), gs("91"), gs("$"), gs("{\"vec\":[1,2,3,4,5,6]}")))
     * });
     * }</pre>
     */
    public static CompletableFuture<Object[]> search(
            @NonNull BaseClient client,
            @NonNull String indexName,
            @NonNull String query,
            @NonNull FTSearchOptions options) {
        var args =
                concatenateArrays(
                        new GlideString[] {gs("FT.SEARCH"), gs(indexName), gs(query)}, options.toArgs());
        return executeCommand(client, args, false);
    }

    /**
     * Uses the provided query expression to locate keys within an index. Once located, the count
     * and/or content of indexed fields within those keys can be returned.
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @param options The search options - see {@link FTSearchOptions}.
     * @return A two element array, where first element is count of documents in result set, and the
     *     second element, which has format <code>
     *     {@literal Map<GlideString, Map<GlideString, GlideString>>}</code> - a mapping between
     *     document names and map of their attributes.<br>
     *     If {@link FTSearchOptions.FTSearchOptionsBuilder#count()} or {@link
     *     FTSearchOptions.FTSearchOptionsBuilder#limit(int, int)} with values <code>0, 0</code> is
     *     set, the command returns array with only one element - the count of the documents.
     * @example
     *     <pre>{@code
     * byte[] vector = new byte[24];
     * Arrays.fill(vector, (byte) 0);
     * var result = FT.search(client, gs("json_idx1"), gs("*=>[KNN 2 @VEC $query_vec]"),
     *         FTSearchOptions.builder().params(Map.of(gs("query_vec"), gs(vector))).build())
     *     .get();
     * assertArrayEquals(result, new Object[] { 2L, Map.of(
     *     gs("json:2"), Map.of(gs("__VEC_score"), gs("11.1100006104"), gs("$"), gs("{\"vec\":[1.1,1.2,1.3,1.4,1.5,1.6]}")),
     *     gs("json:0"), Map.of(gs("__VEC_score"), gs("91"), gs("$"), gs("{\"vec\":[1,2,3,4,5,6]}")))
     * });
     * }</pre>
     */
    public static CompletableFuture<Object[]> search(
            @NonNull BaseClient client,
            @NonNull GlideString indexName,
            @NonNull GlideString query,
            @NonNull FTSearchOptions options) {
        var args =
                concatenateArrays(new GlideString[] {gs("FT.SEARCH"), indexName, query}, options.toArgs());
        return executeCommand(client, args, false);
    }

    /**
     * Uses the provided query expression to locate keys within an index. Once located, the count
     * and/or content of indexed fields within those keys can be returned.
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @return A two element array, where first element is count of documents in result set, and the
     *     second element, which has format <code>
     *     {@literal Map<GlideString, Map<GlideString, GlideString>>}</code> - a mapping between
     *     document names and map of their attributes.
     * @example
     *     <pre>{@code
     * byte[] vector = new byte[24];
     * Arrays.fill(vector, (byte) 0);
     * var result = FT.search(client, "json_idx1", "*").get();
     * assertArrayEquals(result, new Object[] { 2L, Map.of(
     *     gs("json:2"), Map.of(gs("$"), gs("{\"vec\":[1.1,1.2,1.3,1.4,1.5,1.6]}")),
     *     gs("json:0"), Map.of(gs("$"), gs("{\"vec\":[1,2,3,4,5,6]}")))
     * });
     * }</pre>
     */
    public static CompletableFuture<Object[]> search(
            @NonNull BaseClient client, @NonNull String indexName, @NonNull String query) {
        var args = new GlideString[] {gs("FT.SEARCH"), gs(indexName), gs(query)};
        return executeCommand(client, args, false);
    }

    /**
     * Uses the provided query expression to locate keys within an index. Once located, the count
     * and/or content of indexed fields within those keys can be returned.
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @return A two element array, where first element is count of documents in result set, and the
     *     second element, which has format <code>
     *     {@literal Map<GlideString, Map<GlideString, GlideString>>}</code> - a mapping between
     *     document names and map of their attributes.
     * @example
     *     <pre>{@code
     * byte[] vector = new byte[24];
     * Arrays.fill(vector, (byte) 0);
     * var result = FT.search(client, gs("json_idx1"), gs("*")).get();
     * assertArrayEquals(result, new Object[] { 2L, Map.of(
     *     gs("json:2"), Map.of(gs("$"), gs("{\"vec\":[1.1,1.2,1.3,1.4,1.5,1.6]}")),
     *     gs("json:0"), Map.of(gs("$"), gs("{\"vec\":[1,2,3,4,5,6]}")))
     * });
     * }</pre>
     */
    public static CompletableFuture<Object[]> search(
            @NonNull BaseClient client, @NonNull GlideString indexName, @NonNull GlideString query) {
        var args = new GlideString[] {gs("FT.SEARCH"), indexName, query};
        return executeCommand(client, args, false);
    }

    /**
     * Deletes an index and associated content. Indexed document keys are unaffected.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * FT.dropindex(client, "hash_idx1").get();
     * }</pre>
     */
    public static CompletableFuture<String> dropindex(
            @NonNull BaseClient client, @NonNull String indexName) {
        return executeCommand(client, new GlideString[] {gs("FT.DROPINDEX"), gs(indexName)}, false);
    }

    /**
     * Deletes an index and associated content. Indexed document keys are unaffected.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * FT.dropindex(client, gs("hash_idx1")).get();
     * }</pre>
     */
    public static CompletableFuture<String> dropindex(
            @NonNull BaseClient client, @NonNull GlideString indexName) {
        return executeCommand(client, new GlideString[] {gs("FT.DROPINDEX"), indexName}, false);
    }

    /**
     * Runs a search query on an index, and perform aggregate transformations on the results.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param query The text query to search.
     * @return Results of the last stage of the pipeline.
     * @example
     *     <pre>{@code
     * // example of using the API:
     * FT.aggregate(client, "myIndex", "*").get();
     * // the response contains data in the following format:
     * Map<GlideString, Object>[] response = new Map[] {
     *     Map.of(
     *         gs("condition"), gs("refurbished"),
     *         gs("bicycles"), new Object[] { gs("bicycle:9") }
     *     ),
     *     Map.of(
     *         gs("condition"), gs("used"),
     *         gs("bicycles"), new Object[] { gs("bicycle:1"), gs("bicycle:2"), gs("bicycle:3") }
     *     ),
     *     Map.of(
     *         gs("condition"), gs("new"),
     *         gs("bicycles"), new Object[] { gs("bicycle:0"), gs("bicycle:5") }
     *     )
     * };
     * }</pre>
     */
    public static CompletableFuture<Map<GlideString, Object>[]> aggregate(
            @NonNull BaseClient client, @NonNull String indexName, @NonNull String query) {
        return aggregate(client, gs(indexName), gs(query));
    }

    /**
     * Runs a search query on an index, and perform aggregate transformations on the results.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param query The text query to search.
     * @param options Additional parameters for the command - see {@link FTAggregateOptions}.
     * @return Results of the last stage of the pipeline.
     * @example
     *     <pre>{@code
     * // example of using the API:
     * FTAggregateOptions options = FTAggregateOptions.builder()
     *     .loadFields(new String[] {"__key"})
     *     .addClause(
     *             new FTAggregateOptions.GroupBy(
     *                     new String[] {"@condition"},
     *                     new Reducer[] {
     *                         new Reducer("TOLIST", new String[] {"__key"}, "bicycles")
     *                     }))
     *     .build();
     * FT.aggregate(client, "myIndex", "*", options).get();
     * // the response contains data in the following format:
     * Map<GlideString, Object>[] response = new Map[] {
     *     Map.of(
     *         gs("condition"), gs("refurbished"),
     *         gs("bicycles"), new Object[] { gs("bicycle:9") }
     *     ),
     *     Map.of(
     *         gs("condition"), gs("used"),
     *         gs("bicycles"), new Object[] { gs("bicycle:1"), gs("bicycle:2"), gs("bicycle:3") }
     *     ),
     *     Map.of(
     *         gs("condition"), gs("new"),
     *         gs("bicycles"), new Object[] { gs("bicycle:0"), gs("bicycle:5") }
     *     )
     * };
     * }</pre>
     */
    public static CompletableFuture<Map<GlideString, Object>[]> aggregate(
            @NonNull BaseClient client,
            @NonNull String indexName,
            @NonNull String query,
            @NonNull FTAggregateOptions options) {
        return aggregate(client, gs(indexName), gs(query), options);
    }

    /**
     * Runs a search query on an index, and perform aggregate transformations on the results.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param query The text query to search.
     * @return Results of the last stage of the pipeline.
     * @example
     *     <pre>{@code
     * // example of using the API:
     * FT.aggregate(client, gs("myIndex"), gs("*")).get();
     * // the response contains data in the following format:
     * Map<GlideString, Object>[] response = new Map[] {
     *     Map.of(
     *         gs("condition"), gs("refurbished"),
     *         gs("bicycles"), new Object[] { gs("bicycle:9") }
     *     ),
     *     Map.of(
     *         gs("condition"), gs("used"),
     *         gs("bicycles"), new Object[] { gs("bicycle:1"), gs("bicycle:2"), gs("bicycle:3") }
     *     ),
     *     Map.of(
     *         gs("condition"), gs("new"),
     *         gs("bicycles"), new Object[] { gs("bicycle:0"), gs("bicycle:5") }
     *     )
     * };
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    public static CompletableFuture<Map<GlideString, Object>[]> aggregate(
            @NonNull BaseClient client, @NonNull GlideString indexName, @NonNull GlideString query) {
        var args = new GlideString[] {gs("FT.AGGREGATE"), indexName, query};
        return FT.<Object[]>executeCommand(client, args, false)
                .thenApply(res -> castArray(res, Map.class));
    }

    /**
     * Runs a search query on an index, and perform aggregate transformations on the results.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param query The text query to search.
     * @param options Additional parameters for the command - see {@link FTAggregateOptions}.
     * @return Results of the last stage of the pipeline.
     * @example
     *     <pre>{@code
     * // example of using the API:
     * FTAggregateOptions options = FTAggregateOptions.builder()
     *     .loadFields(new String[] {"__key"})
     *     .addClause(
     *             new FTAggregateOptions.GroupBy(
     *                     new String[] {"@condition"},
     *                     new Reducer[] {
     *                         new Reducer("TOLIST", new String[] {"__key"}, "bicycles")
     *                     }))
     *     .build();
     * FT.aggregate(client, gs("myIndex"), gs("*"), options).get();
     * // the response contains data in the following format:
     * Map<GlideString, Object>[] response = new Map[] {
     *     Map.of(
     *         gs("condition"), gs("refurbished"),
     *         gs("bicycles"), new Object[] { gs("bicycle:9") }
     *     ),
     *     Map.of(
     *         gs("condition"), gs("used"),
     *         gs("bicycles"), new Object[] { gs("bicycle:1"), gs("bicycle:2"), gs("bicycle:3") }
     *     ),
     *     Map.of(
     *         gs("condition"), gs("new"),
     *         gs("bicycles"), new Object[] { gs("bicycle:0"), gs("bicycle:5") }
     *     )
     * };
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    public static CompletableFuture<Map<GlideString, Object>[]> aggregate(
            @NonNull BaseClient client,
            @NonNull GlideString indexName,
            @NonNull GlideString query,
            @NonNull FTAggregateOptions options) {
        var args =
                concatenateArrays(
                        new GlideString[] {gs("FT.AGGREGATE"), indexName, query}, options.toArgs());
        return FT.<Object[]>executeCommand(client, args, false)
                .thenApply(res -> castArray(res, Map.class));
    }

    /**
     * Runs a search or aggregation query and collects performance profiling information.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param options Querying and profiling parameters - see {@link FTProfileOptions}.
     * @return A two-element array. The first element contains results of query being profiled, the
     *     second element stores profiling information.
     * @example
     *     <pre>{@code
     * var options = FTSearchOptions.builder().params(Map.of(
     *         gs("query_vec"),
     *         gs(new byte[] { (byte) 0, (byte) 0, (byte) 0, (byte) 0 })))
     *     .build();
     * var result = FT.profile(client, "myIndex", new FTProfileOptions("*=>[KNN 2 @VEC $query_vec]", options)).get();
     * // result[0] contains `FT.SEARCH` response with the given options and query
     * // result[1] contains profiling data as a `Map<GlideString, Long>`
     * }</pre>
     */
    public static CompletableFuture<Object[]> profile(
            @NonNull BaseClient client, @NonNull String indexName, @NonNull FTProfileOptions options) {
        return profile(client, gs(indexName), options);
    }

    /**
     * Runs a search or aggregation query and collects performance profiling information.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param options Querying and profiling parameters - see {@link FTProfileOptions}.
     * @return A two-element array. The first element contains results of query being profiled, the
     *     second element stores profiling information.
     * @example
     *     <pre>{@code
     * var commandLine = new String[] { "*", "LOAD", "1", "__key", "GROUPBY", "1", "@condition", "REDUCE", "COUNT", "0", "AS", "bicylces" };
     * var result = FT.profile(client, gs("myIndex"), new FTProfileOptions(QueryType.AGGREGATE, commandLine)).get();
     * // result[0] contains `FT.AGGREGATE` response with the given command line
     * // result[1] contains profiling data as a `Map<GlideString, Long>`
     * }</pre>
     */
    public static CompletableFuture<Object[]> profile(
            @NonNull BaseClient client,
            @NonNull GlideString indexName,
            @NonNull FTProfileOptions options) {
        var args = concatenateArrays(new GlideString[] {gs("FT.PROFILE"), indexName}, options.toArgs());
        return executeCommand(client, args, false);
    }

    /**
     * Returns information about a given index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @return Nested maps with info about the index. See example for more details.
     * @example
     *     <pre>{@code
     * // example of using the API:
     * Map<String, Object> response = FT.info(client, "myIndex").get();
     * // the response contains data in the following format:
     * Map<String, Object> data = Map.of(
     *     "index_name", gs("myIndex"),
     *     "index_status", gs("AVAILABLE"),
     *     "key_type", gs("JSON"),
     *     "creation_timestamp", 1728348101728771L,
     *     "key_prefixes", new Object[] { gs("json:") },
     *     "num_indexed_vectors", 0L,
     *     "space_usage", 653471L,
     *     "num_docs", 0L,
     *     "vector_space_usage", 653471L,
     *     "index_degradation_percentage", 0L,
     *     "fulltext_space_usage", 0L,
     *     "current_lag", 0L,
     *     "fields", new Object [] {
     *         Map.of(
     *             gs("identifier"), gs("$.vec"),
     *             gs("type"), gs("VECTOR"),
     *             gs("field_name"), gs("VEC"),
     *             gs("option"), gs(""),
     *             gs("vector_params", Map.of(
     *                 gs("data_type", gs("FLOAT32"),
     *                 gs("initial_capacity", 1000L,
     *                 gs("current_capacity", 1000L,
     *                 gs("distance_metric", gs("L2"),
     *                 gs("dimension", 6L,
     *                 gs("block_size", 1024L,
     *                 gs("algorithm", gs("FLAT")
     *             )
     *         ),
     *         Map.of(
     *             gs("identifier"), gs("name"),
     *             gs("type"), gs("TEXT"),
     *             gs("field_name"), gs("name"),
     *             gs("option"), gs("")
     *         ),
     *     }
     * );
     * }</pre>
     */
    public static CompletableFuture<Map<String, Object>> info(
            @NonNull BaseClient client, @NonNull String indexName) {
        return info(client, gs(indexName));
    }

    /**
     * Returns information about a given index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @return Nested maps with info about the index. See example for more details.
     * @example
     *     <pre>{@code
     * // example of using the API:
     * Map<String, Object> response = FT.info(client, gs("myIndex")).get();
     * // the response contains data in the following format:
     * Map<String, Object> data = Map.of(
     *     "index_name", gs("myIndex"),
     *     "index_status", gs("AVAILABLE"),
     *     "key_type", gs("JSON"),
     *     "creation_timestamp", 1728348101728771L,
     *     "key_prefixes", new Object[] { gs("json:") },
     *     "num_indexed_vectors", 0L,
     *     "space_usage", 653471L,
     *     "num_docs", 0L,
     *     "vector_space_usage", 653471L,
     *     "index_degradation_percentage", 0L,
     *     "fulltext_space_usage", 0L,
     *     "current_lag", 0L,
     *     "fields", new Object [] {
     *         Map.of(
     *             gs("identifier"), gs("$.vec"),
     *             gs("type"), gs("VECTOR"),
     *             gs("field_name"), gs("VEC"),
     *             gs("option"), gs(""),
     *             gs("vector_params", Map.of(
     *                 gs("data_type", gs("FLOAT32"),
     *                 gs("initial_capacity", 1000L,
     *                 gs("current_capacity", 1000L,
     *                 gs("distance_metric", gs("L2"),
     *                 gs("dimension", 6L,
     *                 gs("block_size", 1024L,
     *                 gs("algorithm", gs("FLAT")
     *             )
     *         ),
     *         Map.of(
     *             gs("identifier"), gs("name"),
     *             gs("type"), gs("TEXT"),
     *             gs("field_name"), gs("name"),
     *             gs("option"), gs("")
     *         ),
     *     }
     * );
     * }</pre>
     */
    public static CompletableFuture<Map<String, Object>> info(
            @NonNull BaseClient client, @NonNull GlideString indexName) {
        // TODO inconsistency on cluster client: the outer map is `Map<String, T>`,
        //   while inner maps are `Map<GlideString, T>`
        //   The outer map converted from `Map<GlideString, T>` in ClusterValue::ofMultiValueBinary
        // TODO server returns all map keys as `SimpleString`, we're safe to convert all to
        //   `GlideString`s to `String`

        // standalone client returns `Map<GlideString, Object>`, but cluster `Map<String, Object>`
        if (client instanceof GlideClusterClient)
            return executeCommand(client, new GlideString[] {gs("FT.INFO"), indexName}, true);
        return FT.<Map<GlideString, Object>>executeCommand(
                        client, new GlideString[] {gs("FT.INFO"), indexName}, true)
                .thenApply(
                        map ->
                                map.entrySet().stream()
                                        .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue)));
    }

    /**
     * Lists all indexes.
     *
     * @param client The client to execute the command.
     * @return An array of index names.
     * @example
     *     <pre>{@code
     * GlideString[] indices = FT.list(client).get();
     * }</pre>
     */
    public static CompletableFuture<GlideString[]> list(@NonNull BaseClient client) {
        return FT.<Object[]>executeCommand(client, new GlideString[] {gs("FT._LIST")}, false)
                .thenApply(arr -> castArray(arr, GlideString.class));
    }

    /**
     * Adds an alias for an index. The new alias name can be used anywhere that an index name is
     * required.
     *
     * @param client The client to execute the command.
     * @param aliasName The alias to be added to an index.
     * @param indexName The index name for which the alias has to be added.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * FT.aliasadd(client, "myalias", "myindex").get(); // "OK"
     * }</pre>
     */
    public static CompletableFuture<String> aliasadd(
            @NonNull BaseClient client, @NonNull String aliasName, @NonNull String indexName) {
        return aliasadd(client, gs(aliasName), gs(indexName));
    }

    /**
     * Adds an alias for an index. The new alias name can be used anywhere that an index name is
     * required.
     *
     * @param client The client to execute the command.
     * @param aliasName The alias to be added to an index.
     * @param indexName The index name for which the alias has to be added.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * FT.aliasadd(client, gs("myalias"), gs("myindex")).get(); // "OK"
     * }</pre>
     */
    public static CompletableFuture<String> aliasadd(
            @NonNull BaseClient client, @NonNull GlideString aliasName, @NonNull GlideString indexName) {
        var args = new GlideString[] {gs("FT.ALIASADD"), aliasName, indexName};

        return executeCommand(client, args, false);
    }

    /**
     * Deletes an existing alias for an index.
     *
     * @param client The client to execute the command.
     * @param aliasName The existing alias to be deleted for an index.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * FT.aliasdel(client, "myalias").get(); // "OK"
     * }</pre>
     */
    public static CompletableFuture<String> aliasdel(
            @NonNull BaseClient client, @NonNull String aliasName) {
        return aliasdel(client, gs(aliasName));
    }

    /**
     * Deletes an existing alias for an index.
     *
     * @param client The client to execute the command.
     * @param aliasName The existing alias to be deleted for an index.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * FT.aliasdel(client, gs("myalias")).get(); // "OK"
     * }</pre>
     */
    public static CompletableFuture<String> aliasdel(
            @NonNull BaseClient client, @NonNull GlideString aliasName) {
        var args = new GlideString[] {gs("FT.ALIASDEL"), aliasName};

        return executeCommand(client, args, false);
    }

    /**
     * Updates an existing alias to point to a different physical index. This command only affects
     * future references to the alias.
     *
     * @param client The client to execute the command.
     * @param aliasName The alias name. This alias will now be pointed to a different index.
     * @param indexName The index name for which an existing alias has to be updated.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * FT.aliasupdate(client, "myalias", "myindex").get(); // "OK"
     * }</pre>
     */
    public static CompletableFuture<String> aliasupdate(
            @NonNull BaseClient client, @NonNull String aliasName, @NonNull String indexName) {
        return aliasupdate(client, gs(aliasName), gs(indexName));
    }

    /**
     * Update an existing alias to point to a different physical index. This command only affects
     * future references to the alias.
     *
     * @param client The client to execute the command.
     * @param aliasName The alias name. This alias will now be pointed to a different index.
     * @param indexName The index name for which an existing alias has to be updated.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * FT.aliasupdate(client, gs("myalias"), gs("myindex")).get(); // "OK"
     * }</pre>
     */
    public static CompletableFuture<String> aliasupdate(
            @NonNull BaseClient client, @NonNull GlideString aliasName, @NonNull GlideString indexName) {
        var args = new GlideString[] {gs("FT.ALIASUPDATE"), aliasName, indexName};
        return executeCommand(client, args, false);
    }

    /**
     * Lists all index aliases.
     *
     * @param client The client to execute the command.
     * @return A map of index aliases to indices being aliased.
     * @example
     *     <pre>{@code
     * var aliases = FT.aliaslist(client).get();
     * // the response contains data in the following format:
     * Map<GlideString, GlideString> aliases = Map.of(
     *     gs("alias"), gs("myIndex"),
     * );
     * }</pre>
     */
    public static CompletableFuture<Map<GlideString, GlideString>> aliaslist(
            @NonNull BaseClient client) {
        // standalone client returns `Map<GlideString, Object>`, but cluster `Map<String, Object>`
        //   The map converted from `Map<GlideString, T>` in ClusterValue::ofMultiValueBinary
        // TODO this will fail once an alias name will be non-utf8-compatible
        if (client instanceof GlideClient)
            return executeCommand(client, new GlideString[] {gs("FT._ALIASLIST")}, true);
        return FT.<Map<String, GlideString>>executeCommand(
                        client, new GlideString[] {gs("FT._ALIASLIST")}, true)
                .thenApply(
                        map ->
                                map.entrySet().stream()
                                        .collect(Collectors.toMap(e -> gs(e.getKey()), Map.Entry::getValue)));
    }

    /**
     * Parse a query and return information about how that query was parsed.
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search. It is the same as the query passed as an argument to
     *     {@link FT#search(BaseClient, String, String)} and {@link FT#aggregate(BaseClient, String,
     *     String)}.
     * @return A <code>String</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * String result = FT.explain(client, "myIndex", "@price:[0 10]").get();
     * assert result.equals("Field {\n\tprice\n\t0\n\t10\n}");
     * }</pre>
     */
    public static CompletableFuture<String> explain(
            @NonNull BaseClient client, @NonNull String indexName, @NonNull String query) {
        GlideString[] args = {gs("FT.EXPLAIN"), gs(indexName), gs(query)};
        return FT.<GlideString>executeCommand(client, args, false).thenApply(GlideString::toString);
    }

    /**
     * Parse a query and return information about how that query was parsed.
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search. It is the same as the query passed as an argument to
     *     {@link FT#search(BaseClient, GlideString, GlideString)} and {@link FT#aggregate(BaseClient,
     *     GlideString, GlideString)}.
     * @return A <code>GlideString</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * GlideString result = FT.explain(client, gs("myIndex"), gs("@price:[0 10]")).get();
     * assert result.equals("Field {\n\tprice\n\t0\n\t10\n}");
     * }</pre>
     */
    public static CompletableFuture<GlideString> explain(
            @NonNull BaseClient client, @NonNull GlideString indexName, @NonNull GlideString query) {
        GlideString[] args = {gs("FT.EXPLAIN"), indexName, query};
        return executeCommand(client, args, false);
    }

    /**
     * Same as the {@link FT#explain(BaseClient, String, String)} except that the results are
     * displayed in a different format.
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search. It is the same as the query passed as an argument to
     *     {@link FT#search(BaseClient, String, String)} and {@link FT#aggregate(BaseClient, String,
     *     String)}.
     * @return A <code>String[]</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * String[] result = FT.explaincli(client, "myIndex",  "@price:[0 10]").get();
     * assert Arrays.equals(result, new String[]{
     *   "Field {",
     *   "  price",
     *   "  0",
     *   "  10",
     *   "}"
     * });
     * }</pre>
     */
    public static CompletableFuture<String[]> explaincli(
            @NonNull BaseClient client, @NonNull String indexName, @NonNull String query) {
        CompletableFuture<GlideString[]> result = explaincli(client, gs(indexName), gs(query));
        return result.thenApply(
                ret -> Arrays.stream(ret).map(GlideString::toString).toArray(String[]::new));
    }

    /**
     * Same as the {@link FT#explain(BaseClient, String, String)} except that the results are
     * displayed in a different format.
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search. It is the same as the query passed as an argument to
     *     {@link FT#search(BaseClient, GlideString, GlideString)} and {@link FT#aggregate(BaseClient,
     *     GlideString, GlideString)}.
     * @return A <code>GlideString[]</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * GlideString[] result = FT.explaincli(client, gs("myIndex"),  gs("@price:[0 10]")).get();
     * assert Arrays.equals(result, new GlideString[]{
     *   gs("Field {"),
     *   gs("  price"),
     *   gs("  0"),
     *   gs("  10"),
     *   gs("}")
     * });
     * }</pre>
     */
    public static CompletableFuture<GlideString[]> explaincli(
            @NonNull BaseClient client, @NonNull GlideString indexName, @NonNull GlideString query) {
        GlideString[] args = new GlideString[] {gs("FT.EXPLAINCLI"), indexName, query};
        return FT.<Object[]>executeCommand(client, args, false)
                .thenApply(ret -> castArray(ret, GlideString.class));
    }

    /**
     * A wrapper for custom command API.
     *
     * @param client The client to execute the command.
     * @param args The command line.
     * @param returnsMap - true if command returns a map
     */
    @SuppressWarnings("unchecked")
    private static <T> CompletableFuture<T> executeCommand(
            BaseClient client, GlideString[] args, boolean returnsMap) {
        if (client instanceof GlideClient) {
            return ((GlideClient) client).customCommand(args).thenApply(r -> (T) r);
        } else if (client instanceof GlideClusterClient) {
            return ((GlideClusterClient) client)
                    .customCommand(args)
                    .thenApply(returnsMap ? ClusterValue::getMultiValue : ClusterValue::getSingleValue)
                    .thenApply(r -> (T) r);
        }
        throw new IllegalArgumentException(
                "Unknown type of client, should be either `GlideClient` or `GlideClusterClient`");
    }
}
