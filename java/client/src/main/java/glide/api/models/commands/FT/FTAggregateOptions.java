/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.toGlideStringArray;

import glide.api.BaseClient;
import glide.api.commands.servermodules.FT;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.NonNull;

/**
 * Additional arguments for {@link FT#aggregate(BaseClient, String, String, FTAggregateOptions)}
 * command.
 */
@Builder
public class FTAggregateOptions {
    /** Query timeout in milliseconds. */
    private final Integer timeout;

    private final boolean loadAll;

    private final GlideString[] loadFields;

    /**
     * Entries for the LOAD clause that also rename their field via {@code AS}, reusing {@link
     * LoadField} (valkey-search 1.3.0; see {@link LoadField}'s docstring for the compatibility
     * gate). Can be freely mixed with {@link #loadFields} in the same {@link #toArgs()} call by
     * calling both builder methods; the two lists are concatenated, plain fields first.
     */
    private final LoadField[] loadFieldsWithAlias;

    private final List<FTAggregateClause> clauses;

    // 1.2 options
    @Builder.Default private final boolean verbatim = false;

    @Builder.Default private final boolean inorder = false;

    private final Integer slop;

    private final Integer dialect;

    /**
     * If set, exposes the relevance score as a pipeline field named {@code __score} (valkey-search
     * 1.3.0, {@code src/commands/ft_aggregate_parser.cc} {@code kAddScoresParam}, {@code
     * src/commands/ft_aggregate_exec.cc}). {@code __score} can then be referenced like any other
     * pipeline field in a {@link LoadField}, {@link Apply} expression, or a {@link
     * SortBy.SortProperty}.
     */
    @Builder.Default private final boolean addScores = false;

    /**
     * The scoring function used to compute {@code __score} and rank results (valkey-search 1.3.0,
     * {@code src/indexes/scoring/scorer.h}). {@code "BM25STD"} is the only value currently
     * registered and selectable; any other value is rejected by the server. Has no visible effect
     * unless combined with {@link #addScores} (or inspected indirectly via a SORTBY on {@code
     * __score}).
     */
    private final GlideString scorer;

    /**
     * Requests a paged reply instead of the whole result set at once. See {@link FTWithCursor}. The
     * reply shape becomes a two-element array of {@code [rows, cursorId]}, where {@code rows} is
     * this same aggregate response shape and {@code cursorId} is passed to a subsequent {@link
     * FT#cursorRead} call to fetch the next page ({@code 0} once exhausted).
     *
     * <p>A paged query, and every subsequent FT.CURSOR READ/DEL against the cursor it opens, always
     * reads from the PRIMARY of the index's slot, regardless of the client's {@code ReadFrom}
     * configuration -- a cursor is a per-process, node-local resource on the server that opened it.
     * A plain aggregate without {@code withCursor} is unaffected and still prefers replicas as
     * normal.
     */
    private final FTWithCursor withCursor;

    /**
     * {@code true} if {@link #withCursor} is set. Package-visible to {@code FT#aggregate}, which
     * must return the raw {@code [rows, cursorId]} 2-tuple rather than casting to the flat
     * per-document array shape when a cursor is requested.
     */
    public boolean hasWithCursor() {
        return withCursor != null;
    }

    /** Convert to module API. */
    public GlideString[] toArgs() {
        ArrayList<GlideString> args = new ArrayList<GlideString>();
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
        if (loadAll) {
            args.add(gs("LOAD"));
            args.add(gs("*"));
        } else {
            args.addAll(Arrays.asList(loadFieldsToArgs(loadFields, loadFieldsWithAlias)));
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
                        args.add(gs(name));
                        args.add(value);
                    });
        }
        if (clauses != null) {
            for (FTAggregateClause expression : clauses) {
                args.addAll(Arrays.asList(expression.toArgs()));
            }
        }
        if (dialect != null) {
            args.add(gs("DIALECT"));
            args.add(gs(dialect.toString()));
        }
        if (addScores) {
            args.add(gs("ADDSCORES"));
        }
        if (scorer != null) {
            args.add(gs("SCORER"));
            args.add(scorer);
        }
        if (withCursor != null) {
            args.addAll(Arrays.asList(withCursor.toArgs()));
        }
        return args.toArray(new GlideString[0]);
    }

    /**
     * Serializes a LOAD clause's field list for FT.AGGREGATE and FT.HYBRID (shared, since the two
     * commands' LOAD clause grammar is byte-identical -- see {@link LoadField}'s docstring). The
     * count is a token budget, not an entry count -- each {@link LoadField} with an alias
     * contributes 3 tokens (field, AS, alias) rather than 1, matching the parser's own accounting.
     * Plain fields (no alias) are emitted first, followed by {@link LoadField} entries, in that
     * order; either array may be {@code null} or empty.
     */
    static GlideString[] loadFieldsToArgs(GlideString[] plainFields, LoadField[] aliasedFields) {
        boolean hasPlain = plainFields != null && plainFields.length > 0;
        boolean hasAliased = aliasedFields != null && aliasedFields.length > 0;
        if (!hasPlain && !hasAliased) {
            return new GlideString[0];
        }
        ArrayList<GlideString> loadFieldArgs = new ArrayList<>();
        if (hasPlain) {
            loadFieldArgs.addAll(Arrays.asList(plainFields));
        }
        if (hasAliased) {
            for (LoadField field : aliasedFields) {
                loadFieldArgs.addAll(Arrays.asList(field.toArgs()));
            }
        }
        ArrayList<GlideString> args = new ArrayList<>();
        args.add(gs("LOAD"));
        args.add(gs(Integer.toString(loadFieldArgs.size())));
        args.addAll(loadFieldArgs);
        return args.toArray(new GlideString[0]);
    }

    /**
     * Serializes a PARAMS option for FT.AGGREGATE and FT.HYBRID (shared, since the two commands'
     * PARAMS grammar is byte-identical).
     */
    static GlideString[] paramsToArgs(Map<String, GlideString> params, String paramsKeyword) {
        if (params == null || params.isEmpty()) {
            return new GlideString[0];
        }
        ArrayList<GlideString> args = new ArrayList<>();
        args.add(gs(paramsKeyword));
        args.add(gs(Integer.toString(params.size() * 2)));
        params.forEach(
                (name, value) -> {
                    args.add(gs(name));
                    args.add(value);
                });
        return args.toArray(new GlideString[0]);
    }

    /**
     * A single entry in the LOAD clause of FT.AGGREGATE or FT.HYBRID, optionally renaming the
     * loaded field via {@code AS}. Modelled on {@code FTSearchOptions}' return-field identifiers
     * (the equivalent concept for FT.SEARCH's RETURN option).
     *
     * <p>{@code AS <alias>} in the LOAD clause is a compatibility fix introduced in valkey-search
     * 1.3.0 ({@code src/commands/ft_aggregate_parser.cc}, gated by the {@code ft_aggregate_load_as}
     * compatibility flag). Below {@code search.emulate-release 1.3.0}, the legacy branch does not
     * recognise {@code AS} as a rename keyword at all: it is consumed as just another LOAD field
     * token rather than a rename directive. GLIDE does not validate this client-side; against a
     * pre-1.3.0 emulate-release the server rejects the command outright with a {@code RequestError}
     * reading "Index: field `AS` does not exist".
     */
    public static class LoadField {
        private final GlideString fieldIdentifier;
        private final GlideString alias;

        /**
         * Initialize a new instance with no alias.
         *
         * @param fieldIdentifier The identifier for the field to load.
         */
        public LoadField(@NonNull GlideString fieldIdentifier) {
            this.fieldIdentifier = fieldIdentifier;
            this.alias = null;
        }

        /**
         * Initialize a new instance with no alias.
         *
         * @param fieldIdentifier The identifier for the field to load.
         */
        public LoadField(@NonNull String fieldIdentifier) {
            this.fieldIdentifier = gs(fieldIdentifier);
            this.alias = null;
        }

        /**
         * Initialize a new instance.
         *
         * @param fieldIdentifier The identifier for the field to load.
         * @param alias The alias to emit the field's column under. Requires {@code
         *     search.emulate-release} &gt;= 1.3.0 to take effect.
         */
        public LoadField(@NonNull GlideString fieldIdentifier, @NonNull GlideString alias) {
            this.fieldIdentifier = fieldIdentifier;
            this.alias = alias;
        }

        /**
         * Initialize a new instance.
         *
         * @param fieldIdentifier The identifier for the field to load.
         * @param alias The alias to emit the field's column under. Requires {@code
         *     search.emulate-release} &gt;= 1.3.0 to take effect.
         */
        public LoadField(@NonNull String fieldIdentifier, @NonNull String alias) {
            this.fieldIdentifier = gs(fieldIdentifier);
            this.alias = gs(alias);
        }

        /** Get the arguments for this LOAD clause entry. */
        GlideString[] toArgs() {
            if (alias == null) {
                return new GlideString[] {fieldIdentifier};
            }
            return new GlideString[] {fieldIdentifier, gs("AS"), alias};
        }
    }

    /**
     * Query parameters, which could be referenced in the query by <code>$</code> sign, followed by
     * the parameter name.
     */
    @Builder.Default private final Map<String, GlideString> params = new HashMap<>();

    public static class FTAggregateOptionsBuilder {
        // private - hiding this API from user
        void loadAll(boolean loadAll) {}

        void expressions(List<FTAggregateClause> expressions) {}

        void verbatim(boolean verbatim) {}

        void inorder(boolean inorder) {}

        void addScores(boolean addScores) {}

        void loadFieldsWithAlias(LoadField[] loadFieldsWithAlias) {}

        /** Load all fields declared in the index. */
        public FTAggregateOptionsBuilder loadAll() {
            loadAll = true;
            return this;
        }

        /** Load specified fields from the index. */
        public FTAggregateOptionsBuilder loadFields(@NonNull String[] fields) {
            loadFields = toGlideStringArray(fields);
            loadAll = false;
            return this;
        }

        /** Load specified fields from the index. */
        public FTAggregateOptionsBuilder loadFields(@NonNull GlideString[] fields) {
            loadFields = fields;
            loadAll = false;
            return this;
        }

        /**
         * Load specified fields from the index, each optionally renamed via {@code AS}. Can be
         * freely mixed with {@link #loadFields(String[])}/{@link #loadFields(GlideString[])} in the
         * same call -- the two lists are concatenated, plain fields first. Requires {@code
         * search.emulate-release} &gt;= 1.3.0 for the alias to take effect. See {@link LoadField}.
         */
        public FTAggregateOptionsBuilder loadFields(@NonNull LoadField[] fields) {
            loadFieldsWithAlias = fields;
            loadAll = false;
            return this;
        }

        /** If set, exposes the relevance score as a pipeline field named {@code __score}. */
        public FTAggregateOptionsBuilder addScores() {
            this.addScores$value = true;
            this.addScores$set = true;
            return this;
        }

        /**
         * Set the scoring function used to compute {@code __score} and rank results. {@code
         * "BM25STD"} is the only value currently registered and selectable.
         *
         * @param scorer The scoring function name.
         */
        public FTAggregateOptionsBuilder scorer(@NonNull String scorer) {
            this.scorer = gs(scorer);
            return this;
        }

        /**
         * Set the scoring function used to compute {@code __score} and rank results. {@code
         * "BM25STD"} is the only value currently registered and selectable.
         *
         * @param scorer The scoring function name.
         */
        public FTAggregateOptionsBuilder scorer(@NonNull GlideString scorer) {
            this.scorer = scorer;
            return this;
        }

        /**
         * Request a paged reply instead of the whole result set at once. See {@link FTWithCursor}.
         *
         * @param withCursor The cursor paging options.
         */
        public FTAggregateOptionsBuilder withCursor(@NonNull FTWithCursor withCursor) {
            this.withCursor = withCursor;
            return this;
        }

        /**
         * Add {@link Filter}, {@link Limit}, {@link GroupBy}, {@link SortBy} or {@link Apply}
         * clause to the pipeline, that can be repeated multiple times in any order and be freely
         * intermixed. They are applied in the order specified, with the output of one clause
         * feeding the input of the next clause.
         */
        public FTAggregateOptionsBuilder addClause(@NonNull FTAggregateClause clause) {
            if (clauses == null) clauses = new ArrayList<>();
            clauses.add(clause);
            return this;
        }

        /** If set, stemming is not applied to term searches. */
        public FTAggregateOptionsBuilder verbatim() {
            this.verbatim$value = true;
            this.verbatim$set = true;
            return this;
        }

        /** If set, proximity matching of terms must be in order. */
        public FTAggregateOptionsBuilder inorder() {
            this.inorder$value = true;
            this.inorder$set = true;
            return this;
        }

        /**
         * Set the slop value for proximity matching of terms.
         *
         * @param slop The maximum number of intervening terms allowed between query terms.
         */
        public FTAggregateOptionsBuilder slop(int slop) {
            this.slop = slop;
            return this;
        }

        /**
         * Set the query dialect version.
         *
         * @param dialect The dialect version.
         */
        public FTAggregateOptionsBuilder dialect(int dialect) {
            this.dialect = dialect;
            return this;
        }
    }

    /**
     * A superclass for clauses which could be added to <code>FT.AGGREGATE</code> pipeline.<br>
     * A clause could be either:
     *
     * <ul>
     *   <li>{@link Filter}
     *   <li>{@link Limit}
     *   <li>{@link GroupBy}
     *   <li>{@link SortBy}
     *   <li>{@link Apply}
     * </ul>
     */
    public abstract static class FTAggregateClause {
        abstract GlideString[] toArgs();
    }

    enum ClauseType {
        LIMIT,
        FILTER,
        GROUPBY,
        SORTBY,
        REDUCE,
        APPLY
    }

    /** A clause for limiting the number of retained records. */
    public static class Limit extends FTAggregateClause {
        private final int offset;
        private final int count;

        /**
         * Initialize a new instance.
         *
         * @param offset Starting point from which the records have to be retained.
         * @param count The total number of records to be retained.
         */
        public Limit(int offset, int count) {
            this.offset = offset;
            this.count = count;
        }

        @Override
        GlideString[] toArgs() {
            return new GlideString[] {
                gs(ClauseType.LIMIT.toString()),
                gs(Integer.toString(offset)),
                gs(Integer.toString(count))
            };
        }
    }

    /**
     * Filter the results using predicate expression relating to values in each result. It is
     * applied post query and relate to the current state of the pipeline.
     */
    public static class Filter extends FTAggregateClause {
        private final GlideString expression;

        /**
         * Initialize a new instance.
         *
         * @param expression The expression to filter the results.
         */
        public Filter(@NonNull GlideString expression) {
            this.expression = expression;
        }

        /**
         * Initialize a new instance.
         *
         * @param expression The expression to filter the results.
         */
        public Filter(@NonNull String expression) {
            this.expression = gs(expression);
        }

        @Override
        GlideString[] toArgs() {
            return new GlideString[] {gs(ClauseType.FILTER.toString()), expression};
        }
    }

    /** A clause for grouping the results in the pipeline based on one or more properties. */
    public static class GroupBy extends FTAggregateClause {
        private final GlideString[] properties;
        private final Reducer[] reducers;

        /**
         * Initialize a new instance.
         *
         * @param properties The list of properties to be used for grouping the results in the
         *     pipeline.
         * @param reducers The list of functions that handles the group entries by performing
         *     multiple aggregate operations.
         */
        public GroupBy(@NonNull GlideString[] properties, @NonNull Reducer[] reducers) {
            this.properties = properties;
            this.reducers = reducers;
        }

        /**
         * Initialize a new instance.
         *
         * @param properties The list of properties to be used for grouping the results in the
         *     pipeline.
         * @param reducers The list of functions that handles the group entries by performing
         *     multiple aggregate operations.
         */
        public GroupBy(@NonNull String[] properties, @NonNull Reducer[] reducers) {
            this.properties = toGlideStringArray(properties);
            this.reducers = reducers;
        }

        @Override
        GlideString[] toArgs() {
            return concatenateArrays(
                    new GlideString[] {
                        gs(ClauseType.GROUPBY.toString()), gs(Integer.toString(properties.length))
                    },
                    properties,
                    Stream.of(reducers)
                            .map(Reducer::toArgs)
                            .flatMap(Stream::of)
                            .toArray(size -> new GlideString[size]));
        }

        /**
         * A function that handles the group entries, either counting them, or performing multiple
         * aggregate operations.
         */
        public static class Reducer {
            private final String function;
            private final GlideString[] args;
            private final String name;

            /**
             * Initialize a new instance.
             *
             * @param function The reduction function names for the respective group.
             * @param args The list of arguments for the reducer.
             * @param name User defined property name for the reducer.
             */
            public Reducer(
                    @NonNull String function, @NonNull GlideString[] args, @NonNull String name) {
                this.function = function;
                this.args = args;
                this.name = name;
            }

            /**
             * Initialize a new instance.
             *
             * @param function The reduction function names for the respective group.
             * @param args The list of arguments for the reducer.
             */
            public Reducer(@NonNull String function, @NonNull GlideString[] args) {
                this.function = function;
                this.args = args;
                this.name = null;
            }

            /**
             * Initialize a new instance.
             *
             * @param function The reduction function names for the respective group.
             * @param args The list of arguments for the reducer.
             * @param name User defined property name for the reducer.
             */
            public Reducer(@NonNull String function, @NonNull String[] args, @NonNull String name) {
                this.function = function;
                this.args = toGlideStringArray(args);
                this.name = name;
            }

            /**
             * Initialize a new instance.
             *
             * @param function The reduction function names for the respective group.
             * @param args The list of arguments for the reducer.
             */
            public Reducer(@NonNull String function, @NonNull String[] args) {
                this.function = function;
                this.args = toGlideStringArray(args);
                this.name = null;
            }

            GlideString[] toArgs() {
                return concatenateArrays(
                        new GlideString[] {
                            gs(ClauseType.REDUCE.toString()),
                            gs(function),
                            gs(Integer.toString(args.length))
                        },
                        args,
                        name == null ? new GlideString[0] : new GlideString[] {gs("AS"), gs(name)});
            }

            /**
             * Creates a <code>TOLIST</code> reducer, which collects all values for <code>field
             * </code> across the records in a group into a single array, removing duplicates and
             * <code>nil
             * </code> values. If a value is itself an array, it is flattened one level before being
             * added. Values are returned in the order they were first encountered.
             *
             * @param field The property whose values are collected into a list.
             * @param name User defined property name for the reducer.
             * @return A new {@link Reducer} instance configured as <code>TOLIST</code>.
             */
            public static Reducer toList(@NonNull String field, @NonNull String name) {
                return new Reducer("TOLIST", new String[] {field}, name);
            }

            /**
             * Creates a <code>FIRST_VALUE</code> reducer, which returns the value of <code>field
             * </code> from a single record in the group. Without <code>by</code>, the first record
             * encountered wins. With <code>by</code>, the record whose <code>by</code> property is
             * smallest (<code>
             * ASC</code>, the default) or largest (<code>DESC</code>) wins; ties keep the
             * first-encountered record.
             *
             * @param field The property whose value is returned.
             * @param by The property used to choose which record's value of <code>field</code> is
             *     returned. May be <code>null</code> if <code>order</code> is also <code>null
             *     </code>.
             * @param order The direction used to pick the winning record when <code>by</code> is
             *     set. May be <code>null</code> to use the default (<code>ASC</code>).
             * @param name User defined property name for the reducer.
             * @return A new {@link Reducer} instance configured as <code>FIRST_VALUE</code>.
             * @throws IllegalArgumentException if <code>order</code> is specified without <code>by
             *     </code>.
             */
            public static Reducer firstValue(
                    @NonNull String field,
                    String by,
                    SortBy.SortOrder order,
                    @NonNull String name) {
                if (order != null && by == null) {
                    throw new IllegalArgumentException("firstValue: order requires by to be set.");
                }

                List<String> args = new ArrayList<>();
                args.add(field);

                if (by != null) {
                    args.add("BY");
                    args.add(by);

                    if (order != null) {
                        args.add(order.toString());
                    }
                }

                return new Reducer("FIRST_VALUE", args.toArray(new String[0]), name);
            }

            /**
             * Creates a <code>RANDOM_SAMPLE</code> reducer, which returns a uniform random sample
             * of up to <code>size</code> values of <code>field</code> from the records in a group,
             * using reservoir sampling.
             *
             * @param field The property to sample values from.
             * @param size The maximum number of values to sample. Must be a non-negative integer no
             *     greater than <code>1000</code>; the server rejects values outside this range.
             * @param name User defined property name for the reducer.
             * @return A new {@link Reducer} instance configured as <code>RANDOM_SAMPLE</code>.
             */
            public static Reducer randomSample(
                    @NonNull String field, int size, @NonNull String name) {
                return new Reducer(
                        "RANDOM_SAMPLE", new String[] {field, Integer.toString(size)}, name);
            }
        }
    }

    /** Sort the pipeline using a list of properties. */
    public static class SortBy extends FTAggregateClause {

        private final SortProperty[] properties;
        private final Integer max;

        /**
         * Initialize a new instance.
         *
         * @param properties A list of sorting parameters for the sort operation.
         */
        public SortBy(@NonNull SortProperty[] properties) {
            this.properties = properties;
            this.max = null;
        }

        /**
         * Initialize a new instance.
         *
         * @param properties A list of sorting parameters for the sort operation.
         * @param max The MAX value for optimizing the sorting, by sorting only for the n-largest
         *     elements.
         */
        public SortBy(@NonNull SortProperty[] properties, int max) {
            this.properties = properties;
            this.max = max;
        }

        @Override
        GlideString[] toArgs() {
            return concatenateArrays(
                    new GlideString[] {
                        gs(ClauseType.SORTBY.toString()),
                        gs(Integer.toString(properties.length * 2)),
                    },
                    Stream.of(properties)
                            .map(SortProperty::toArgs)
                            .flatMap(Stream::of)
                            .toArray(size -> new GlideString[size]),
                    max == null
                            ? new GlideString[0]
                            : new GlideString[] {gs("MAX"), gs(max.toString())});
        }

        public enum SortOrder {
            ASC,
            DESC
        }

        /** A sorting parameter. */
        public static class SortProperty {
            private final GlideString property;
            private final SortOrder order;

            /**
             * Initialize a new instance.
             *
             * @param property The sorting parameter name.
             * @param order The order for the sorting.
             */
            public SortProperty(@NonNull GlideString property, @NonNull SortOrder order) {
                this.property = property;
                this.order = order;
            }

            /**
             * Initialize a new instance.
             *
             * @param property The sorting parameter name.
             * @param order The order for the sorting.
             */
            public SortProperty(@NonNull String property, @NonNull SortOrder order) {
                this.property = gs(property);
                this.order = order;
            }

            GlideString[] toArgs() {
                return new GlideString[] {property, gs(order.toString())};
            }
        }
    }

    /**
     * Apply a 1-to-1 transformation on one or more properties and either stores the result as a new
     * property down the pipeline or replace any property using this transformation.
     */
    public static class Apply extends FTAggregateClause {
        private final GlideString expression;
        private final GlideString name;

        /**
         * Initialize a new instance.
         *
         * @param expression The transformation expression.
         * @param name The new property name to store the result of apply.
         */
        public Apply(@NonNull GlideString expression, @NonNull GlideString name) {
            this.expression = expression;
            this.name = name;
        }

        /**
         * Initialize a new instance.
         *
         * @param expression The transformation expression.
         * @param name The new property name to store the result of apply.
         */
        public Apply(@NonNull String expression, @NonNull String name) {
            this.expression = gs(expression);
            this.name = gs(name);
        }

        @Override
        GlideString[] toArgs() {
            return new GlideString[] {gs(ClauseType.APPLY.toString()), expression, gs("AS"), name};
        }
    }
}
