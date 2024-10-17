/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

import static glide.api.models.GlideString.gs;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.toGlideStringArray;

import glide.api.BaseClient;
import glide.api.commands.servermodules.FT;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Builder;

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

    private final List<FTAggregateExpression> expressions;

    /** Convert to module API. */
    public GlideString[] toArgs() {
        var args = new ArrayList<GlideString>();
        if (loadAll) {
            args.add(gs("LOAD"));
            args.add(gs("*"));
        } else if (loadFields != null) {
            args.add(gs("LOAD"));
            args.add(gs(Integer.toString(loadFields.length)));
            args.addAll(List.of(loadFields));
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
        if (expressions != null) {
            for (var expression : expressions) {
                args.addAll(List.of(expression.toArgs()));
            }
        }
        return args.toArray(GlideString[]::new);
    }

    /**
     * Query parameters, which could be referenced in the query by <code>$</code> sign, followed by
     * the parameter name.
     */
    @Builder.Default private final Map<String, GlideString> params = new HashMap<>();

    public static class FTAggregateOptionsBuilder {
        // private - hiding this API from user
        void loadAll(boolean loadAll) {}

        void expressions(List<FTAggregateExpression> expressions) {}

        public FTAggregateOptionsBuilder loadAll() {
            loadAll = true;
            return this;
        }

        public FTAggregateOptionsBuilder loadFields(String[] fields) {
            loadFields = toGlideStringArray(fields);
            loadAll = false;
            return this;
        }

        public FTAggregateOptionsBuilder loadFields(GlideString[] fields) {
            loadFields = fields;
            loadAll = false;
            return this;
        }

        public FTAggregateOptionsBuilder addExpression(FTAggregateExpression expression) {
            if (expressions == null) expressions = new ArrayList<>();
            expressions.add(expression);
            return this;
        }
    }

    public abstract static class FTAggregateExpression {
        abstract GlideString[] toArgs();
    }

    enum ExpressionType {
        LIMIT,
        FILTER,
        GROUPBY,
        SORTBY,
        REDUCE,
        APPLY
    }

    /** Configure results limiting. */
    public static class Limit extends FTAggregateExpression {
        private final int offset;
        private final int count;

        public Limit(int offset, int count) {
            this.offset = offset;
            this.count = count;
        }

        @Override
        GlideString[] toArgs() {
            return new GlideString[] {
                gs(ExpressionType.LIMIT.toString()),
                gs(Integer.toString(offset)),
                gs(Integer.toString(count))
            };
        }
    }

    /**
     * Filter the results using predicate expression relating to values in each result. It is applied
     * post query and relate to the current state of the pipeline.
     */
    public static class Filter extends FTAggregateExpression {
        private final GlideString expression;

        public Filter(GlideString expression) {
            this.expression = expression;
        }

        public Filter(String expression) {
            this.expression = gs(expression);
        }

        @Override
        GlideString[] toArgs() {
            return new GlideString[] {gs(ExpressionType.FILTER.toString()), expression};
        }
    }

    /**
     * Filter the results using predicate expression relating to values in each result. It is applied
     * post query and relate to the current state of the pipeline.
     */
    public static class GroupBy extends FTAggregateExpression {
        private final GlideString[] properties;
        private final Reducer[] reducers;

        public GroupBy(GlideString[] properties, Reducer[] reducers) {
            this.properties = properties;
            this.reducers = reducers;
        }

        public GroupBy(String[] properties, Reducer[] reducers) {
            this.properties = toGlideStringArray(properties);
            this.reducers = reducers;
        }

        public GroupBy(GlideString[] properties) {
            this.properties = properties;
            this.reducers = new Reducer[0];
        }

        public GroupBy(String[] properties) {
            this.properties = toGlideStringArray(properties);
            this.reducers = new Reducer[0];
        }

        @Override
        GlideString[] toArgs() {
            return concatenateArrays(
                    new GlideString[] {
                        gs(ExpressionType.GROUPBY.toString()), gs(Integer.toString(properties.length))
                    },
                    properties,
                    Stream.of(reducers).map(Reducer::toArgs).flatMap(Stream::of).toArray(GlideString[]::new));
        }

        /**
         * A function that handles the group entries, either counting them, or performing multiple
         * aggregate operations.
         */
        public static class Reducer {
            private final String function;
            private final GlideString[] args;
            private final String alias;

            public Reducer(String function, GlideString[] args, String alias) {
                this.function = function;
                this.args = args;
                this.alias = alias;
            }

            public Reducer(String function, GlideString[] args) {
                this.function = function;
                this.args = args;
                this.alias = null;
            }

            public Reducer(String function, String[] args, String alias) {
                this.function = function;
                this.args = toGlideStringArray(args);
                this.alias = alias;
            }

            public Reducer(String function, String[] args) {
                this.function = function;
                this.args = toGlideStringArray(args);
                this.alias = null;
            }

            GlideString[] toArgs() {
                return concatenateArrays(
                        new GlideString[] {
                            gs(ExpressionType.REDUCE.toString()), gs(function), gs(Integer.toString(args.length))
                        },
                        args,
                        alias == null ? new GlideString[0] : new GlideString[] {gs("AS"), gs(alias)});
            }
        }
    }

    /** Sort the pipeline using a list of properties. */
    public static class SortBy extends FTAggregateExpression {

        private final SortProperty[] properties;
        private final Integer max;

        public SortBy(SortProperty[] properties) {
            this.properties = properties;
            this.max = null;
        }

        public SortBy(SortProperty[] properties, int max) {
            this.properties = properties;
            this.max = max;
        }

        @Override
        GlideString[] toArgs() {
            return concatenateArrays(
                    new GlideString[] {
                        gs(ExpressionType.SORTBY.toString()), gs(Integer.toString(properties.length * 2)),
                    },
                    Stream.of(properties)
                            .map(SortProperty::toArgs)
                            .flatMap(Stream::of)
                            .toArray(GlideString[]::new),
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

            public SortProperty(GlideString property, SortOrder order) {
                this.property = property;
                this.order = order;
            }

            public SortProperty(String property, SortOrder order) {
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
    public static class Apply extends FTAggregateExpression {
        private final GlideString expression;
        private final GlideString alias;

        public Apply(GlideString expression, GlideString alias) {
            this.expression = expression;
            this.alias = alias;
        }

        public Apply(String expression, String alias) {
            this.expression = gs(expression);
            this.alias = gs(alias);
        }

        @Override
        GlideString[] toArgs() {
            return new GlideString[] {gs(ExpressionType.APPLY.toString()), expression, gs("AS"), alias};
        }
    }
}
