/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands.servermodules;

import static glide.api.models.GlideString.gs;
import static glide.utils.ArrayTransformUtils.concatenateArrays;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
import glide.api.models.commands.FT.FTSearchOptions;
import java.util.Arrays;
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
