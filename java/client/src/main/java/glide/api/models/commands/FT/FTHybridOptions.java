/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;
import static glide.utils.ArrayTransformUtils.toGlideStringArray;

import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;

/**
 * This class represents the optional arguments for the FT.HYBRID command, applied to the
 * aggregate-suffix tail that follows the mandatory SEARCH, VSIM and optional COMBINE clauses (which
 * are passed directly to {@code FT#hybrid}, not through this class).
 */
@Builder
public class FTHybridOptions {
    private final FTHybridCombine combine;

    @Builder.Default private final boolean loadAll = false;

    private final GlideString[] loadFields;

    /**
     * Entries for the LOAD clause that also rename their field via {@code AS}. Can be freely mixed
     * with {@link #loadFields} in the same {@link #toArgs} call by setting both; the two lists are
     * concatenated, plain fields first.
     */
    private final FTAggregateOptions.LoadField[] loadFieldsWithAlias;

    /**
     * FILTER, LIMIT, GROUPBY, SORTBY and APPLY clauses, reusing {@link
     * FTAggregateOptions.FTAggregateClause} and its subclasses verbatim from the FT.AGGREGATE options
     * module. Every arm alias is freely reachable from these clauses (APPLY, SORTBY, GROUPBY/REDUCE)
     * -- only the LOAD clause has the alias restriction described on {@link FTHybridSearchArm}.
     * FT.HYBRID also applies its own default LIMIT of 10 rows when no {@link
     * FTAggregateOptions.Limit} clause is given here -- FT.AGGREGATE has no such default and returns
     * everything. {@link #toArgs} always emits {@code clauses} after {@code params}'s own serialized
     * arguments (PARAMS is always present for FT.HYBRID, so this tail is never empty) -- emitting a
     * leading FILTER clause right after the VSIM arm would otherwise let the server's VSIM parser
     * claim it as a VSIM pre-filter instead.
     */
    private final List<FTAggregateOptions.FTAggregateClause> clauses;

    /** Requests a paged reply. See {@link FTWithCursor}. */
    private final FTWithCursor withCursor;

    /**
     * Restricts the query to the local shard rather than fanning out across the cluster. This is a
     * valkey-search extension not present in RediSearch's FT.HYBRID. LOCALONLY is accepted anywhere
     * in this tail BEFORE PARAMS (including after DIALECT or a LOAD clause) but is rejected
     * ("Unexpected argument `LOCALONLY`") once PARAMS has been parsed, regardless of what follows
     * PARAMS. {@link #toArgs} always emits it before {@code params} and {@code clauses} (PARAMS
     * always follows it immediately), so this restriction is handled transparently regardless of the
     * order its own arguments were given in.
     */
    @Builder.Default private final boolean localOnly = false;

    /** Overrides the timeout parameter of the module, in milliseconds. */
    private final Integer timeout;

    /**
     * Param key/value pairs referenced from within the SEARCH query, a VSIM arm's {@code $name}
     * vector reference, or any clause's expression text. If a {@link FTHybridVsimArm} in the same
     * call is given raw vector bytes rather than an explicit {@code $name} string, an internally
     * generated entry is merged into this mapping automatically -- a name collision with a
     * caller-supplied entry here raises {@link IllegalArgumentException} rather than silently
     * overwriting either one.
     */
    @Builder.Default private final Map<String, GlideString> params = new HashMap<>();

    /**
     * The query dialect version to use. FT.HYBRID is fixed at dialect 2 server-side; passing any
     * other value is rejected by the server.
     */
    private final Integer dialect;

    /**
     * The COMBINE clause, if set. Package-visible to {@code FT#hybrid}, which emits it directly after
     * the VSIM arm's own argv rather than through {@link #toArgs}.
     */
    public FTHybridCombine getCombine() {
        return combine;
    }

    private Map<String, GlideString> mergeParams(Map<String, GlideString> generatedVsimParam) {
        Map<String, GlideString> merged = new HashMap<>(params);
        if (generatedVsimParam != null) {
            for (Map.Entry<String, GlideString> entry : generatedVsimParam.entrySet()) {
                if (merged.containsKey(entry.getKey())) {
                    throw new IllegalArgumentException(
                            "Generated VSIM parameter name '"
                                    + entry.getKey()
                                    + "' collides with an entry already present in params; rename the "
                                    + "caller-supplied params entry.");
                }
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    /**
     * Get the optional arguments for the FT.HYBRID command's aggregate-suffix tail, with no
     * internally generated VSIM parameter to merge.
     */
    public GlideString[] toArgs() {
        return toArgs(null);
    }

    /**
     * Get the optional arguments for the FT.HYBRID command's aggregate-suffix tail.
     *
     * @param generatedVsimParam An internally generated PARAMS entry for a {@link FTHybridVsimArm}
     *     given raw vector bytes, merged with {@link #params} here. Supplied by {@code FT#hybrid};
     *     not intended to be set directly by callers.
     * @throws IllegalArgumentException If {@code generatedVsimParam}'s key collides with an entry
     *     already present in {@link #params}.
     */
    public GlideString[] toArgs(Map<String, GlideString> generatedVsimParam) {
        ArrayList<GlideString> args = new ArrayList<>();
        if (localOnly) {
            // Must come before PARAMS -- the server rejects LOCALONLY once PARAMS has been
            // parsed. toArgs always emits it this early regardless of field-construction order.
            args.add(gs("LOCALONLY"));
        }
        if (loadAll) {
            args.add(gs("LOAD"));
            args.add(gs("*"));
        } else {
            args.addAll(
                    Arrays.asList(FTAggregateOptions.loadFieldsToArgs(loadFields, loadFieldsWithAlias)));
        }
        if (timeout != null) {
            args.add(gs("TIMEOUT"));
            args.add(gs(timeout.toString()));
        }
        args.addAll(
                Arrays.asList(FTAggregateOptions.paramsToArgs(mergeParams(generatedVsimParam), "PARAMS")));
        if (clauses != null) {
            // Must come after PARAMS -- a leading FILTER clause emitted right after the VSIM
            // arm would otherwise be claimed by the server's VSIM parser as a VSIM pre-filter
            // instead.
            for (FTAggregateOptions.FTAggregateClause clause : clauses) {
                args.addAll(Arrays.asList(clause.toArgs()));
            }
        }
        if (withCursor != null) {
            args.addAll(Arrays.asList(withCursor.toArgs()));
        }
        if (dialect != null) {
            args.add(gs("DIALECT"));
            args.add(gs(dialect.toString()));
        }
        return args.toArray(new GlideString[0]);
    }

    public static class FTHybridOptionsBuilder {
        // private - hiding this API from user
        void loadAll(boolean loadAll) {}

        void localOnly(boolean localOnly) {}

        void loadFieldsWithAlias(FTAggregateOptions.LoadField[] loadFieldsWithAlias) {}

        void clauses(List<FTAggregateOptions.FTAggregateClause> clauses) {}

        /** Load every field declared in the index, plus both arms' score aliases automatically. */
        public FTHybridOptionsBuilder loadAll() {
            this.loadAll$value = true;
            this.loadAll$set = true;
            return this;
        }

        /** Load specified fields from the index. */
        public FTHybridOptionsBuilder loadFields(@NonNull String[] fields) {
            this.loadFields = toGlideStringArray(fields);
            return this;
        }

        /** Load specified fields from the index. */
        public FTHybridOptionsBuilder loadFields(@NonNull GlideString[] fields) {
            this.loadFields = fields;
            return this;
        }

        /**
         * Load specified fields from the index, each optionally renamed via {@code AS}. Can be freely
         * mixed with {@link #loadFields(String[])}/{@link #loadFields(GlideString[])} in the same call.
         */
        public FTHybridOptionsBuilder loadFields(@NonNull FTAggregateOptions.LoadField[] fields) {
            this.loadFieldsWithAlias = fields;
            return this;
        }

        /**
         * Add a FILTER, LIMIT, GROUPBY, SORTBY or APPLY clause to the aggregate-suffix tail, that can
         * be repeated multiple times in any order and be freely intermixed.
         */
        public FTHybridOptionsBuilder addClause(@NonNull FTAggregateOptions.FTAggregateClause clause) {
            if (this.clauses == null) this.clauses = new ArrayList<>();
            this.clauses.add(clause);
            return this;
        }

        /** Restrict the query to the local shard rather than fanning out across the cluster. */
        public FTHybridOptionsBuilder localOnly() {
            this.localOnly$value = true;
            this.localOnly$set = true;
            return this;
        }
    }
}
