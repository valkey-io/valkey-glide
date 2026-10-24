/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;

import glide.api.commands.servermodules.FT;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;

/** Mandatory parameters for {@link FT#search}. */
@Builder
public class FTSearchOptions {

    @Builder.Default private final boolean nocontent = false;

    @Builder.Default private final Map<GlideString, GlideString> identifiers = new HashMap<>();

    /**
     * Restricts the search to only this set of keys, filtering out any matching result whose key is
     * not in the list. The result count is adjusted to reflect the filtered set. An explicit empty
     * array is distinct from {@code null}: {@code null} omits {@code INKEYS} entirely (no
     * restriction), while an empty array sends {@code INKEYS 0} and excludes every result.
     */
    private final GlideString[] inKeys;

    /** Query timeout in milliseconds. */
    private final Integer timeout;

    private final Pair<Integer, Integer> limit;

    @Builder.Default private final boolean count = false;

    /**
     * Query parameters, which could be referenced in the query by <code>$</code> sign, followed by
     * the parameter name.
     */
    @Builder.Default private final Map<GlideString, GlideString> params = new HashMap<>();

    /** Query dialect version. Only dialect 2 is currently supported. */
    private final Integer dialect;

    @Builder.Default private final boolean verbatim = false;

    @Builder.Default private final boolean inorder = false;

    private final Integer slop;

    private final GlideString sortBy;

    private final SortOrder sortByOrder;

    @Builder.Default private final boolean withSortKeys = false;

    private final ShardScope shardScope;

    private final ConsistencyMode consistency;

    /**
     * If set, augments the output with each document's relevance score (valkey-search 1.3.0).
     * This changes the result shape: each document value in the result map becomes a two-element
     * list {@code [score, fieldMap]} instead of just {@code fieldMap} (a {@code GlideString}-
     * encoded decimal string, not a float). With {@code nocontent} set, the shape becomes {@code
     * [score, {}]}. Combining {@code withSortKeys} with {@code nocontent} gives the same {@code
     * [score, {}]}, because the server drops the sort key in that combination. With both {@code
     * withScores} and {@code withSortKeys} and content present, each row becomes a three-element
     * list {@code [score, sortKey, fieldMap]}.
     */
    @Builder.Default private final boolean withScores = false;

    /**
     * The scoring function used to rank results (valkey-search 1.3.0, {@code
     * src/indexes/scoring/scorer.h}). {@code "BM25STD"} is the only value currently registered and
     * selectable; any other value is rejected by the server. Has no visible effect unless combined
     * with {@link #withScores} (or inspected indirectly via {@code sortBy}).
     */
    private final GlideString scorer;

    /** Sort order for SORTBY clause. */
    public enum SortOrder {
        ASC,
        DESC
    }

    /** Controls shard participation in cluster mode. */
    public enum ShardScope {
        /** Terminate with timeout error if not all shards respond. This is the default. */
        ALLSHARDS,
        /** Generate a best-effort reply if not all shards respond within the timeout. */
        SOMESHARDS
    }

    /** Controls consistency requirements in cluster mode. */
    public enum ConsistencyMode {
        /** Terminate with an error if the cluster is in an inconsistent state. This is the default. */
        CONSISTENT,
        /** Generate a best-effort reply if the cluster remains inconsistent within the timeout. */
        INCONSISTENT
    }

    /** Convert to module API. */
    public GlideString[] toArgs() {
        if (sortBy == null && sortByOrder != null) {
            throw new IllegalArgumentException("sortByOrder requires sortBy to be set.");
        }
        if (sortBy == null && withSortKeys) {
            throw new IllegalArgumentException("withSortKeys requires sortBy to be set.");
        }
        if (count && limit != null) {
            throw new IllegalArgumentException(
                    "count and limit are mutually exclusive -- count maps to LIMIT 0 0 "
                            + "internally, so an explicit limit would conflict with it.");
        }
        ArrayList<GlideString> args = new ArrayList<GlideString>();
        if (shardScope != null) {
            args.add(gs(shardScope.toString()));
        }
        if (consistency != null) {
            args.add(gs(consistency.toString()));
        }
        if (nocontent) {
            args.add(gs("NOCONTENT"));
        }
        if (verbatim) {
            args.add(gs("VERBATIM"));
        }
        if (inorder) {
            args.add(gs("INORDER"));
        }
        if (slop != null) {
            args.add(gs("SLOP"));
            args.add(gs(slop.toString()));
        }
        if (inKeys != null) {
            args.add(gs("INKEYS"));
            args.add(gs(Integer.toString(inKeys.length)));
            for (GlideString key : inKeys) {
                args.add(key);
            }
        }
        if (!identifiers.isEmpty()) {
            int returnIndex = args.size();
            args.add(gs("RETURN"));
            int tokenCount = 0;
            for (Map.Entry<GlideString, GlideString> pair : identifiers.entrySet()) {
                tokenCount++;
                args.add(pair.getKey());
                if (pair.getValue() != null) {
                    tokenCount += 2;
                    args.add(gs("AS"));
                    args.add(pair.getValue());
                }
            }
            args.add(returnIndex + 1, gs(Integer.toString(tokenCount)));
        }
        if (sortBy != null) {
            args.add(gs("SORTBY"));
            args.add(sortBy);
            if (sortByOrder != null) {
                args.add(gs(sortByOrder.toString()));
            }
        }
        if (withSortKeys) {
            args.add(gs("WITHSORTKEYS"));
        }
        if (timeout != null) {
            args.add(gs("TIMEOUT"));
            args.add(gs(timeout.toString()));
        }
        if (!params.isEmpty()) {
            args.add(gs("PARAMS"));
            args.add(gs(Integer.toString(params.size() * 2)));
            params.forEach(
                    (name, value) -> {
                        args.add(name);
                        args.add(value);
                    });
        }
        if (limit != null) {
            args.add(gs("LIMIT"));
            args.add(gs(Integer.toString(limit.getLeft())));
            args.add(gs(Integer.toString(limit.getRight())));
        }
        if (count) {
            // COUNT is not a real FT.SEARCH keyword -- valkey-search's parser
            // rejects a bare COUNT token outright. The count-only form the
            // server actually recognises is LIMIT 0 0; the guard at the top
            // of this method already confirmed limit is unset.
            args.add(gs("LIMIT"));
            args.add(gs("0"));
            args.add(gs("0"));
        }
        if (withScores) {
            args.add(gs("WITHSCORES"));
        }
        if (scorer != null) {
            args.add(gs("SCORER"));
            args.add(scorer);
        }
        if (dialect != null) {
            args.add(gs("DIALECT"));
            args.add(gs(dialect.toString()));
        }
        return args.toArray(new GlideString[0]);
    }

    public static class FTSearchOptionsBuilder {

        // private - hiding this API from user
        void limit(Pair<Integer, Integer> limit) {}

        void count(boolean count) {}

        void nocontent(boolean nocontent) {}

        void dialect(Integer dialect) {}

        void identifiers(Map<GlideString, GlideString> identifiers) {}

        void verbatim(boolean verbatim) {}

        void inorder(boolean inorder) {}

        void withSortKeys(boolean withSortKeys) {}

        void withScores(boolean withScores) {}

        public FTSearchOptionsBuilder() {
            this.identifiers$value = new HashMap<>();
        }

        /** Add a field to be returned. */
        public FTSearchOptionsBuilder addReturnField(@NonNull String field) {
            this.identifiers$value.put(gs(field), null);
            this.identifiers$set = true;
            return this;
        }

        /** Add a field with an alias to be returned. */
        public FTSearchOptionsBuilder addReturnField(@NonNull String field, @NonNull String alias) {
            this.identifiers$value.put(gs(field), gs(alias));
            this.identifiers$set = true;
            return this;
        }

        /** Add a field to be returned. */
        public FTSearchOptionsBuilder addReturnField(@NonNull GlideString field) {
            this.identifiers$value.put(field, null);
            this.identifiers$set = true;
            return this;
        }

        /** Add a field with an alias to be returned. */
        public FTSearchOptionsBuilder addReturnField(
                @NonNull GlideString field, @NonNull GlideString alias) {
            this.identifiers$value.put(field, alias);
            this.identifiers$set = true;
            return this;
        }

        /**
         * Configure query pagination. By default only first 10 documents are returned.
         *
         * @param offset Zero-based offset.
         * @param count Number of elements to return.
         */
        public FTSearchOptionsBuilder limit(int offset, int count) {
            this.limit = Pair.of(offset, count);
            return this;
        }

        /**
         * Once set, the query will return only number of documents in the result set without actually
         * returning them.
         */
        public FTSearchOptionsBuilder count() {
            this.count$value = true;
            this.count$set = true;
            return this;
        }

        /** Once set, the query will return only document IDs without any field content. */
        public FTSearchOptionsBuilder nocontent() {
            this.nocontent$value = true;
            this.nocontent$set = true;
            return this;
        }

        /**
         * Set the query dialect version.
         *
         * @param dialect The dialect version (currently, only dialect 2 is supported in valkey-search).
         */
        public FTSearchOptionsBuilder dialect(int dialect) {
            this.dialect = dialect;
            return this;
        }

        /** If set, stemming is not applied to text terms in the query. */
        public FTSearchOptionsBuilder verbatim() {
            this.verbatim$value = true;
            this.verbatim$set = true;
            return this;
        }

        /** If set, proximity matching of text terms must be in order. */
        public FTSearchOptionsBuilder inorder() {
            this.inorder$value = true;
            this.inorder$set = true;
            return this;
        }

        /**
         * Set the slop value for proximity matching of text terms.
         *
         * @param slop The maximum number of intervening terms allowed between query terms.
         */
        public FTSearchOptionsBuilder slop(int slop) {
            this.slop = slop;
            return this;
        }

        /**
         * Sort results by a field.
         *
         * @param field The field name to sort by.
         */
        public FTSearchOptionsBuilder sortBy(@NonNull String field) {
            this.sortBy = gs(field);
            return this;
        }

        /**
         * Sort results by a field.
         *
         * @param field The field name to sort by.
         */
        public FTSearchOptionsBuilder sortBy(@NonNull GlideString field) {
            this.sortBy = field;
            return this;
        }

        /**
         * Sort results by a field with a specified order.
         *
         * @param field The field name to sort by.
         * @param order The sort order (ASC or DESC).
         */
        public FTSearchOptionsBuilder sortBy(@NonNull String field, @NonNull SortOrder order) {
            this.sortBy = gs(field);
            this.sortByOrder = order;
            return this;
        }

        /**
         * Sort results by a field with a specified order.
         *
         * @param field The field name to sort by.
         * @param order The sort order (ASC or DESC).
         */
        public FTSearchOptionsBuilder sortBy(@NonNull GlideString field, @NonNull SortOrder order) {
            this.sortBy = field;
            this.sortByOrder = order;
            return this;
        }

        /**
         * If set and sortBy is specified, augments the output with the sort key value.
         *
         * <p>When WITHSORTKEYS is enabled, the response format changes: each document entry becomes a
         * two-element array {@code [sortKey, fieldMap]} instead of just {@code fieldMap}. The sort key
         * is the value of the field used for sorting (or {@code null} if the field is missing from the
         * document).
         */
        public FTSearchOptionsBuilder withSortKeys() {
            this.withSortKeys$value = true;
            this.withSortKeys$set = true;
            return this;
        }

        /**
         * Set the shard scope for cluster mode queries.
         *
         * @param shardScope The shard scope (ALLSHARDS or SOMESHARDS).
         */
        public FTSearchOptionsBuilder shardScope(@NonNull ShardScope shardScope) {
            this.shardScope = shardScope;
            return this;
        }

        /**
         * Set the consistency mode for cluster mode queries.
         *
         * @param consistency The consistency mode (CONSISTENT or INCONSISTENT).
         */
        public FTSearchOptionsBuilder consistency(@NonNull ConsistencyMode consistency) {
            this.consistency = consistency;
            return this;
        }

        /**
         * If set, augments the output with each document's relevance score. See {@link
         * #withScores} for the resulting shape change.
         */
        public FTSearchOptionsBuilder withScores() {
            this.withScores$value = true;
            this.withScores$set = true;
            return this;
        }

        /**
         * Set the scoring function used to rank results. {@code "BM25STD"} is the only value
         * currently registered and selectable.
         *
         * @param scorer The scoring function name.
         */
        public FTSearchOptionsBuilder scorer(@NonNull String scorer) {
            this.scorer = gs(scorer);
            return this;
        }

        /**
         * Set the scoring function used to rank results. {@code "BM25STD"} is the only value
         * currently registered and selectable.
         *
         * @param scorer The scoring function name.
         */
        public FTSearchOptionsBuilder scorer(@NonNull GlideString scorer) {
            this.scorer = scorer;
            return this;
        }
    }
}
