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

    private final List<FTAggregateClause> clauses;

    // 1.2 options
    @Builder.Default private final boolean verbatim = false;

    @Builder.Default private final boolean inorder = false;

    private final Integer slop;

    private final Integer dialect;

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
        } else if (loadFields != null) {
            args.add(gs("LOAD"));
            args.add(gs(Integer.toString(loadFields.length)));
            args.addAll(Arrays.asList(loadFields));
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
        return args.toArray(new GlideString[0]);
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
         * Add {@link Filter}, {@link Limit}, {@link GroupBy}, {@link SortBy} or {@link Apply} clause to
         * the pipeline, that can be repeated multiple times in any order and be freely intermixed. They
         * are applied in the order specified, with the output of one clause feeding the input of the
         * next clause.
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
                gs(ClauseType.LIMIT.toString()), gs(Integer.toString(offset)), gs(Integer.toString(count))
            };
        }
    }

    /**
     * Filter the results using predicate expression relating to values in each result. It is applied
     * post query and relate to the current state of the pipeline.
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
         * @param properties The list of properties to be used for grouping the results in the pipeline.
         * @param reducers The list of functions that handles the group entries by performing multiple
         *     aggregate operations.
         */
        public GroupBy(@NonNull GlideString[] properties, @NonNull Reducer[] reducers) {
            this.properties = properties;
            this.reducers = reducers;
        }

        /**
         * Initialize a new instance.
         *
         * @param properties The list of properties to be used for grouping the results in the pipeline.
         * @param reducers The list of functions that handles the group entries by performing multiple
         *     aggregate operations.
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
            public Reducer(@NonNull String function, @NonNull GlideString[] args, @NonNull String name) {
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
                            gs(ClauseType.REDUCE.toString()), gs(function), gs(Integer.toString(args.length))
                        },
                        args,
                        name == null ? new GlideString[0] : new GlideString[] {gs("AS"), gs(name)});
            }

            /**
             * Creates a <code>TOLIST</code> reducer, which collects all values for <code>field</code>
             * across the records in a group into a single array, removing duplicates and <code>nil
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
             * Creates a <code>FIRST_VALUE</code> reducer, which returns the value of <code>field</code>
             * from a single record in the group. Without <code>by</code>, the first record encountered
             * wins. With <code>by</code>, the record whose <code>by</code> property is smallest (<code>
             * ASC</code>, the default) or largest (<code>DESC</code>) wins; ties keep the
             * first-encountered record.
             *
             * @param field The property whose value is returned.
             * @param by The property used to choose which record's value of <code>field</code> is
             *     returned. May be <code>null</code> if <code>order</code> is also <code>null</code>.
             * @param order The direction used to pick the winning record when <code>by</code> is set. May
             *     be <code>null</code> to use the default (<code>ASC</code>).
             * @param name User defined property name for the reducer.
             * @return A new {@link Reducer} instance configured as <code>FIRST_VALUE</code>.
             * @throws IllegalArgumentException if <code>order</code> is specified without <code>by
             *     </code>.
             */
            public static Reducer firstValue(
                    @NonNull String field, String by, SortBy.SortOrder order, @NonNull String name) {
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
             * Creates a <code>RANDOM_SAMPLE</code> reducer, which returns a uniform random sample of up
             * to <code>size</code> values of <code>field</code> from the records in a group, using
             * reservoir sampling.
             *
             * @param field The property to sample values from.
             * @param size The maximum number of values to sample. Must be a non-negative integer no
             *     greater than <code>1000</code>; the server rejects values outside this range.
             * @param name User defined property name for the reducer.
             * @return A new {@link Reducer} instance configured as <code>RANDOM_SAMPLE</code>.
             */
            public static Reducer randomSample(@NonNull String field, int size, @NonNull String name) {
                return new Reducer("RANDOM_SAMPLE", new String[] {field, Integer.toString(size)}, name);
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
                        gs(ClauseType.SORTBY.toString()), gs(Integer.toString(properties.length * 2)),
                    },
                    Stream.of(properties)
                            .map(SortProperty::toArgs)
                            .flatMap(Stream::of)
                            .toArray(size -> new GlideString[size]),
                    max == null ? new GlideString[0] : new GlideString[] {gs("MAX"), gs(max.toString())});
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
