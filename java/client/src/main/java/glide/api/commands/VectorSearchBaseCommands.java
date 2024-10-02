/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.vss.FTCreateOptions.FieldInfo;
import glide.api.models.commands.vss.FTCreateOptions.IndexType;
import glide.api.models.commands.vss.FTSearchOptions;
import glide.api.models.commands.vss.FTSearchOptions.FTSearchOptionsBuilder;
import java.util.concurrent.CompletableFuture;

public interface VectorSearchBaseCommands {
    // TODO GlideString???
    /**
     * Creates an index and initiates a backfill of that index.
     *
     * @see TODO
     * @param indexName The index name.
     * @param indexType The index type.
     * @param prefixes (Optional) A list of prefixes of index definitions
     * @param fields Fields to populate into the index.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * // Create an index for vectors of size 2:
     * client.ftcreate("hash_idx1", IndexType.HASH, new String[] {"hash:"}, new FieldInfo[] {
     *     new FieldInfo("vec", "VEC", VectorFieldFlat.builder(DistanceMetric.L2, 2).build())
     * }).get();
     * // Create a 6-dimensional JSON index using the HNSW algorithm:
     * client.ftcreate("json_idx1", IndexType.JSON, new String[] {"json:"}, new FieldInfo[] {
     *     new FieldInfo("$.vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.L2, 6).numberOfEdges(32).build())
     * }).get();
     * }</pre>
     */
    CompletableFuture<String> ftcreate(
            String indexName, IndexType indexType, String[] prefixes, FieldInfo[] fields);

    /**
     * Uses the provided query expression to locate keys within an index. Once located, the count
     * and/or content of indexed fields within those keys can be returned.
     *
     * @see TODO
     * @param indexName The index name to search into.
     * @param query The text query to search.
     * @param options The search options - see {@link FTSearchOptions}.
     * @return A two element array, where first element is count of documents in result set, and the
     *     second element, which has format <code>
     *     {@literal Map<GlideString, Map<GlideString, GlideString>>}</code>, is a mapping between
     *     document names and map of their attributes.<br>
     *     If {@link FTSearchOptionsBuilder#count()} or {@link FTSearchOptionsBuilder#limit(int, int)}
     *     with values <code>0, 0</code> is set, the command returns array with only one element - the
     *     count of the documents.
     * @example
     *     <pre>{@code
     * byte[] vector = new byte[24];
     * Arrays.fill(vector, (byte) 0);
     * var result = client.ftsearch("json_idx1", "*=>[KNN 2 @VEC $query_vec]",
     *         FTSearchOptions.builder().params(Map.of("query_vec", gs(vector))).build())
     *     .get();
     * assertArrayEquals(result, new Object[] { 2L, Map.of(
     *     gs("json:2"), Map.of(gs("__VEC_score"), gs("11.1100006104"), gs("$"), gs("{\"vec\":[1.1,1.2,1.3,1.4,1.5,1.6]}")),
     *     gs("json:0"), Map.of(gs("__VEC_score"), gs("91"), gs("$"), gs("{\"vec\":[1,2,3,4,5,6]}")))
     * });
     * }</pre>
     */
    CompletableFuture<Object[]> ftsearch(String indexName, String query, FTSearchOptions options);

    /**
     * Deletes an index and associated content. Keys are unaffected.
     *
     * @see TODO
     * @param indexName The index name.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * client.ftdrop("hash_idx1").get();
     * }</pre>
     */
    CompletableFuture<String> ftdrop(String indexName);
}
