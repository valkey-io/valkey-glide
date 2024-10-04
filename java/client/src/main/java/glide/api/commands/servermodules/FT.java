/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands.servermodules;

import static glide.api.models.GlideString.gs;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
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
     *     "index_name", "bcd97d68-4180-4bc5-98fe-5125d0abbcb8",
     *     "index_status", "AVAILABLE",
     *     "key_type", "JSON",
     *     "creation_timestamp", 1728348101728771L,
     *     "key_prefixes", new String[] { "json:" },
     *     "num_indexed_vectors", 0L,
     *     "space_usage", 653471L,
     *     "num_docs", 0L,
     *     "vector_space_usage", 653471L,
     *     "index_degradation_percentage", 0L,
     *     "fulltext_space_usage", 0L,
     *     "current_lag", 0L,
     *     "fields", new Object [] {
     *         Map.of(
     *             "identifier", "$.vec",
     *             "type", "VECTOR",
     *             "field_name", "VEC",
     *             "option", ""
     *             "vector_params", Map.of(
     *                 "data_type", "FLOAT32",
     *                 "initial_capacity", 1000L,
     *                 "current_capacity", 1000L,
     *                 "distance_metric", "L2",
     *                 "dimension", 6L,
     *                 "block_size", 1024L,
     *                 "algorithm", "FLAT"
     *           )
     *         ),
     *         Map.of(
     *             "identifier", "name",
     *             "type", "TEXT",
     *             "field_name", "name",
     *             "option", ""
     *         ),
     *     }
     * );
     * }</pre>
     */
    public static CompletableFuture<Map<String, Object>> info(
            @NonNull BaseClient client, @NonNull String indexName) {
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
     * Map<String, Object> response = client.ftinfo("myIndex").get();
     * // the response contains data in the following format:
     * Map<String, Object> data = Map.of(
     *     "index_name", "bcd97d68-4180-4bc5-98fe-5125d0abbcb8",
     *     "index_status", "AVAILABLE",
     *     "key_type", "JSON",
     *     "creation_timestamp", 1728348101728771L,
     *     "key_prefixes", new String[] { "json:" },
     *     "num_indexed_vectors", 0L,
     *     "space_usage", 653471L,
     *     "num_docs", 0L,
     *     "vector_space_usage", 653471L,
     *     "index_degradation_percentage", 0L,
     *     "fulltext_space_usage", 0L,
     *     "current_lag", 0L,
     *     "fields", new Object [] {
     *         Map.of(
     *             "identifier", "$.vec",
     *             "type", "VECTOR",
     *             "field_name", "VEC",
     *             "option", ""
     *             "vector_params", Map.of(
     *                 "data_type", "FLOAT32",
     *                 "initial_capacity", 1000L,
     *                 "current_capacity", 1000L,
     *                 "distance_metric", "L2",
     *                 "dimension", 6L,
     *                 "block_size", 1024L,
     *                 "algorithm", "FLAT"
     *           )
     *         ),
     *         Map.of(
     *             "identifier", "name",
     *             "type", "TEXT",
     *             "field_name", "name",
     *             "option", ""
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
