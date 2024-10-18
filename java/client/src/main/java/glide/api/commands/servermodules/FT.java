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
import glide.api.models.commands.FT.FTSearchOptions;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.NonNull;

/** Module for vector search commands. */
public class FT {
    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param fields Fields to populate into the index.
     * @return <code>OK</code>.
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
            @NonNull BaseClient client, @NonNull String indexName, @NonNull FieldInfo[] fields) {
        // Node: bug in meme DB - command fails if cmd is too short even though all mandatory args are
        // present
        // TODO confirm is it fixed or not and update docs if needed
        return create(client, indexName, fields, FTCreateOptions.builder().build());
    }

    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param fields Fields to populate into the index.
     * @param options Additional parameters for the command - see {@link FTCreateOptions}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * // Create a 6-dimensional JSON index using the HNSW algorithm:
     * FT.create(client, "json_idx1",
     *     new FieldInfo[] { new FieldInfo("$.vec", "VEC",
     *         VectorFieldHnsw.builder(DistanceMetric.L2, 6).numberOfEdges(32).build())
     *     },
     *     FTCreateOptions.builder().indexType(JSON).prefixes(new String[] {"json:"}).build(),
     * ).get();
     * }</pre>
     */
    public static CompletableFuture<String> create(
            @NonNull BaseClient client,
            @NonNull String indexName,
            @NonNull FieldInfo[] fields,
            @NonNull FTCreateOptions options) {
        return create(client, gs(indexName), fields, options);
    }

    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param fields Fields to populate into the index.
     * @return <code>OK</code>.
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
            @NonNull BaseClient client, @NonNull GlideString indexName, @NonNull FieldInfo[] fields) {
        // Node: bug in meme DB - command fails if cmd is too short even though all mandatory args are
        // present
        // TODO confirm is it fixed or not and update docs if needed
        return create(client, indexName, fields, FTCreateOptions.builder().build());
    }

    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @param client The client to execute the command.
     * @param indexName The index name.
     * @param fields Fields to populate into the index.
     * @param options Additional parameters for the command - see {@link FTCreateOptions}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * // Create a 6-dimensional JSON index using the HNSW algorithm:
     * FT.create(client, gs("json_idx1"),
     *     new FieldInfo[] { new FieldInfo(gs("$.vec"), gs("VEC"),
     *         VectorFieldHnsw.builder(DistanceMetric.L2, 6).numberOfEdges(32).build())
     *     },
     *     FTCreateOptions.builder().indexType(JSON).prefixes(new String[] {"json:"}).build(),
     * ).get();
     * }</pre>
     */
    public static CompletableFuture<String> create(
            @NonNull BaseClient client,
            @NonNull GlideString indexName,
            @NonNull FieldInfo[] fields,
            @NonNull FTCreateOptions options) {
        var args =
                Stream.of(
                                new GlideString[] {gs("FT.CREATE"), indexName},
                                options.toArgs(),
                                new GlideString[] {gs("SCHEMA")},
                                Arrays.stream(fields)
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
     *     .addExpression(
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
     *     .addExpression(
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
     * Returns information about a given index.
     *
     * @param indexName The index name.
     * @return Nested maps with info about the index. See example for more details.
     * @example
     *     <pre>{@code
     * // example of using the API:
     * Map<String, Object> response = client.ftinfo("myIndex").get();
     * // the response contains data in the following format:
     * Map<String, Object> data = Map.of(
     *     "index_name", gs("bcd97d68-4180-4bc5-98fe-5125d0abbcb8"),
     *     "index_status", gs("AVAILABLE"),
     *     "key_type", gs("JSON"),
     *     "creation_timestamp", 1728348101728771L,
     *     "key_prefixes", new String[] { gs("json:") },
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
     *           )
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
        // TODO inconsistency: the outer map is `Map<String, T>`,
        //   while inner maps are `Map<GlideString, T>`
        //   The outer map converted from `Map<GlideString, T>` in ClusterValue::ofMultiValueBinary
        // TODO server returns all strings as `SimpleString`, we're safe to convert all to
        //   `GlideString`s to `String`
        return executeCommand(client, new GlideString[] {gs("FT.INFO"), gs(indexName)}, true);
    }

    /**
     * Returns information about a given index.
     *
     * @param indexName The index name.
     * @return Nested maps with info about the index. See example for more details.
     * @example
     *     <pre>{@code
     * // example of using the API:
     * Map<String, Object> response = client.ftinfo(gs("myIndex")).get();
     * // the response contains data in the following format:
     * Map<String, Object> data = Map.of(
     *     "index_name", gs("bcd97d68-4180-4bc5-98fe-5125d0abbcb8"),
     *     "index_status", gs("AVAILABLE"),
     *     "key_type", gs("JSON"),
     *     "creation_timestamp", 1728348101728771L,
     *     "key_prefixes", new String[] { gs("json:") },
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
     *           )
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
        return executeCommand(client, new GlideString[] {gs("FT.INFO"), indexName}, true);
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
     * @param indexName The index name for which an existing alias has to updated.
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
     * @param indexName The index name for which an existing alias has to updated.
     * @return <code>"OK"</code>.
     * @example
     *     <pre>{@code
     * FT.aliasupdate(client,gs("myalias"), gs("myindex")).get(); // "OK"
     * }</pre>
     */
    public static CompletableFuture<String> aliasupdate(
            @NonNull BaseClient client, @NonNull GlideString aliasName, @NonNull GlideString indexName) {
        var args = new GlideString[] {gs("FT.ALIASUPDATE"), aliasName, indexName};

        return executeCommand(client, args, false);
    }

    /**
     * Returns the execution plan for a complex query
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @return A <code>string</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * FT.explain(client, "myIndex", "*=>[KNN 2 @VEC $query_vec]").get();   // "Vector"
     * }</pre>
     */
    public static CompletableFuture<String> explain(
            @NonNull BaseClient client, @NonNull String indexName, @NonNull String query) {
        var args = concatenateArrays(new GlideString[] {gs("FT.EXPLAIN"), gs(indexName), gs(query)});
        return executeCommand(client, args, false)
                .thenApply(result -> ((GlideString) result).toString());
    }

    /**
     * Returns the execution plan for a complex query
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @return A <code>string</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * FT.explain(client, gs("myIndex"), gs("*=>[KNN 2 @VEC $query_vec]")).get();   // "Vector"
     * }</pre>
     */
    public static CompletableFuture<GlideString> explain(
            @NonNull BaseClient client, @NonNull GlideString indexName, @NonNull GlideString query) {
        var args = concatenateArrays(new GlideString[] {gs("FT.EXPLAIN"), indexName, query});
        return executeCommand(client, args, false);
    }

    /**
     * Returns the execution plan for a complex query
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @param dialect The dialect version under which to execute the query. If not specified, the
     *     query executes under the default dialect version set during module initial loading.
     * @return A <code>string</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * FT.explain(client, "myIndex", "*=>[KNN 2 @VEC $query_vec]", 12.2).get();   // "Vector"
     * }</pre>
     */
    public static CompletableFuture<String> explain(
            @NonNull BaseClient client,
            @NonNull String indexName,
            @NonNull String query,
            @NonNull Double dialect) {
        var args =
                concatenateArrays(
                        new GlideString[] {gs("FT.EXPLAIN"), gs(indexName), gs(query), gs(dialect.toString())});
        return executeCommand(client, args, false)
                .thenApply(result -> ((GlideString) result).toString());
    }

    /**
     * Returns the execution plan for a complex query
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @param dialect The dialect version under which to execute the query. If not specified, the
     *     query executes under the default dialect version set during module initial loading.
     * @return A <code>string</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * FT.explain(client, gs("myIndex"), gs("*=>[KNN 2 @VEC $query_vec]"), 12.2).get();   // "Vector"
     * }</pre>
     */
    public static CompletableFuture<GlideString> explain(
            @NonNull BaseClient client,
            @NonNull GlideString indexName,
            @NonNull GlideString query,
            @NonNull Double dialect) {
        var args =
                concatenateArrays(
                        new GlideString[] {gs("FT.EXPLAIN"), indexName, query, gs(dialect.toString())});
        return executeCommand(client, args, false);
    }

    /**
     * Return the execution plan for a complex query but formatted for easier reading without using
     * redis-cli --raw
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @return A <code>string</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * FT.explaincli(client, "myIndex", "*=>[KNN 2 @VEC $query_vec]").get();   // "Vector"
     * }</pre>
     */
    public static CompletableFuture<String[]> explaincli(
            @NonNull BaseClient client, @NonNull String indexName, @NonNull String query) {
        var args = concatenateArrays(new GlideString[] {gs("FT.EXPLAINCLI"), gs(indexName), gs(query)});
        return executeCommand(client, args, false)
                .thenApply(result -> castArray(result, GlideString.class));
    }

    /**
     * Return the execution plan for a complex query but formatted for easier reading without using
     * redis-cli --raw
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @return A <code>string</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * FT.explaincli(client, gs("myIndex"), gs("*=>[KNN 2 @VEC $query_vec]")).get();   // "Vector"
     * }</pre>
     */
    public static CompletableFuture<Object[]> explaincli(
            @NonNull BaseClient client, @NonNull GlideString indexName, @NonNull GlideString query) {
        var args = concatenateArrays(new GlideString[] {gs("FT.EXPLAINCLI"), indexName, query});
        return executeCommand(client, args, false);
    }

    /**
     * Return the execution plan for a complex query but formatted for easier reading without using
     * redis-cli --raw
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @param dialect The dialect version under which to execute the query. If not specified, the
     *     query executes under the default dialect version set during module initial loading.
     * @return A <code>string</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * FT.explaincli(client, "myIndex", "*=>[KNN 2 @VEC $query_vec]", 12.2).get();   // "Vector"
     * }</pre>
     */
    public static CompletableFuture<Object[]> explaincli(
            @NonNull BaseClient client,
            @NonNull String indexName,
            @NonNull String query,
            @NonNull Double dialect) {
        var args =
                concatenateArrays(
                        new GlideString[] {
                            gs("FT.EXPLAINCLI"), gs(indexName), gs(query), gs(dialect.toString())
                        });
        return executeCommand(client, args, false)
                .thenApply(result -> (GlideString[]) ((Object[]) result)[0]);
    }

    /**
     * Return the execution plan for a complex query but formatted for easier reading without using
     * redis-cli --raw.
     *
     * @param client The client to execute the command.
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @param dialect The dialect version under which to execute the query. If not specified, the
     *     query executes under the default dialect version set during module initial loading.
     * @param options The additional parameter for the command - see {@link FTCreateOptions}.
     * @return A <code>string</code> representing the execution plan.
     * @example
     *     <pre>{@code
     * FT.explaincli(client, gs("myIndex"), gs("*=>[KNN 2 @VEC $query_vec]"), 12.2).get();   // "Vector"
     * }</pre>
     */
    public static CompletableFuture<Object[]> explaincli(
            @NonNull BaseClient client,
            @NonNull GlideString indexName,
            @NonNull GlideString query,
            @NonNull Double dialect) {
        var args =
                concatenateArrays(
                        new GlideString[] {gs("FT.EXPLAINCLI"), indexName, query, gs(dialect.toString())});
        return executeCommand(client, args, false);
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
