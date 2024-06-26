/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.api.commands.SortedSetBaseCommands.WITH_SCORES_REDIS_API;
import static glide.utils.ArrayTransformUtils.concatenateArrays;

import glide.api.commands.SortedSetBaseCommands;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Arguments for {@link SortedSetBaseCommands#zcount}, {@link
 * SortedSetBaseCommands#zremrangebyrank}, {@link SortedSetBaseCommands#zremrangebylex(String,
 * LexRange, LexRange)}, {@link SortedSetBaseCommands#zremrangebyscore}, {@link
 * SortedSetBaseCommands#zrange}, {@link SortedSetBaseCommands#zrangestore}, {@link
 * SortedSetBaseCommands#zrangeWithScores}, and {@link SortedSetBaseCommands#zlexcount}
 *
 * @see <a href="https://redis.io/commands/zcount/">redis.io</a>
 * @see <a href="https://redis.io/commands/zremrangebyrank/">redis.io</a>
 * @see <a href="https://redis.io/commands/zremrangebylex/">redis.io</a>
 * @see <a href="https://redis.io/commands/zremrangebyscore/">redis.io</a>
 * @see <a href="https://redis.io/commands/zrange/">redis.io</a>
 * @see <a href="https://redis.io/commands/zrangestore/">redis.io</a>
 * @see <a href="https://redis.io/commands/zlexcount/">redis.io</a>
 */
public class RangeOptions {

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link InfScoreBound}
     *   <li>{@link ScoreBoundary}
     * </ul>
     */
    public interface ScoreRange {
        String toArgs();
    }

    /** Enumeration representing numeric positive and negative infinity bounds for a sorted set. */
    @RequiredArgsConstructor
    public enum InfScoreBound implements ScoreRange {
        POSITIVE_INFINITY("+inf"),
        NEGATIVE_INFINITY("-inf");

        private final String redisApi;

        public String toArgs() {
            return redisApi;
        }
    }

    /** Represents a specific numeric score boundary in a sorted set. */
    public static class ScoreBoundary implements ScoreRange {
        private final double bound;
        private final boolean isInclusive;

        /**
         * Creates a specific numeric score boundary in a sorted set.
         *
         * @param bound The score value.
         * @param isInclusive Whether the score value is inclusive. Defaults to true if not set.
         */
        public ScoreBoundary(double bound, boolean isInclusive) {
            this.bound = bound;
            this.isInclusive = isInclusive;
        }

        /**
         * Creates a specific numeric score boundary in a sorted set.
         *
         * @param bound The score value.
         */
        public ScoreBoundary(double bound) {
            this(bound, true);
        }

        /** Convert the score boundary to the Redis protocol format. */
        public String toArgs() {
            return (isInclusive ? "" : "(") + bound;
        }
    }

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link InfLexBound}
     *   <li>{@link LexBoundary}
     * </ul>
     */
    public interface LexRange {
        String toArgs();
    }

    /**
     * Enumeration representing lexicographic positive and negative infinity bounds for sorted set.
     */
    @RequiredArgsConstructor
    public enum InfLexBound implements LexRange {
        POSITIVE_INFINITY("+"),
        NEGATIVE_INFINITY("-");

        private final String redisApi;

        @Override
        public String toArgs() {
            return redisApi;
        }
    }

    /** Represents a specific lexicographic boundary in a sorted set. */
    public static class LexBoundary implements LexRange {
        private final String value;
        private final boolean isInclusive;

        /**
         * Creates a specific lexicographic boundary in a sorted set.
         *
         * @param value The lex value.
         * @param isInclusive Whether the lex value is inclusive. Defaults to true if not set.
         */
        public LexBoundary(@NonNull String value, boolean isInclusive) {
            this.value = value;
            this.isInclusive = isInclusive;
        }

        /**
         * Creates a specific lexicographic boundary in a sorted set.
         *
         * @param value The lex value.
         */
        public LexBoundary(@NonNull String value) {
            this(value, true);
        }

        /** Convert the lex boundary to the Redis protocol format. */
        @Override
        public String toArgs() {
            return (isInclusive ? "[" : "(") + value;
        }
    }

    /**
     * Represents a limit argument for a range query in a sorted set.<br>
     * The optional <code>LIMIT</code> argument can be used to obtain a sub-range from the matching
     * elements (similar to <code>SELECT LIMIT</code> offset, count in SQL).
     */
    @RequiredArgsConstructor
    @Getter
    public static class Limit {
        /** The offset from the start of the range. */
        private final long offset;

        /**
         * The number of elements to include in the range. A negative count returns all elements from
         * the offset.
         */
        private final long count;
    }

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link RangeByIndex}
     *   <li>{@link RangeByScore}
     *   <li>{@link RangeByLex}
     * </ul>
     */
    public interface RangeQuery {
        String getStart();

        String getEnd();

        Limit getLimit();
    }

    /**
     * Represents a range by lexicographical order in a sorted set.<br>
     * The <code>start</code> and <code>stop</code> arguments represent lexicographical boundaries.
     */
    @Getter
    public static class RangeByLex implements RangeQuery {
        private final String start;
        private final String end;
        private final Limit limit;

        /**
         * Creates a range by lexicographical order in a sorted set.<br>
         * The <code>start</code> and <code>stop</code> arguments represent lexicographical boundaries.
         *
         * @param start The start lexicographic boundary.
         * @param end The stop lexicographic boundary.
         * @param limit The limit argument for a range query. Defaults to null. See <code>Limit</code>
         *     class for more information.
         */
        public RangeByLex(
                @NonNull RangeOptions.LexRange start,
                @NonNull RangeOptions.LexRange end,
                @NonNull Limit limit) {
            this.start = start.toArgs();
            this.end = end.toArgs();
            this.limit = limit;
        }

        /**
         * Creates a range by lexicographical order in a sorted set.<br>
         * The <code>start</code> and <code>stop</code> arguments represent lexicographical boundaries.
         *
         * @param start The start lexicographic boundary.
         * @param end The stop lexicographic boundary.
         */
        public RangeByLex(@NonNull RangeOptions.LexRange start, @NonNull RangeOptions.LexRange end) {
            this.start = start.toArgs();
            this.end = end.toArgs();
            this.limit = null;
        }
    }

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link RangeByIndex}
     *   <li>{@link RangeByScore}
     * </ul>
     */
    public interface ScoredRangeQuery extends RangeQuery {}

    /**
     * Represents a range by index (rank) in a sorted set.<br>
     * The <code>start</code> and <code>stop</code> arguments represent zero-based indexes.
     */
    @Getter
    public static class RangeByIndex implements ScoredRangeQuery {
        private final String start;
        private final String end;

        /**
         * Creates a range by index (rank) in a sorted set.<br>
         * The <code>start</code> and <code>stop</code> arguments represent zero-based indexes.
         *
         * @param start The start index of the range.
         * @param end The stop index of the range.
         */
        public RangeByIndex(long start, long end) {
            this.start = Long.toString(start);
            this.end = Long.toString(end);
        }

        @Override
        public Limit getLimit() {
            return null;
        }
    }

    /**
     * Represents a range by score in a sorted set.<br>
     * The <code>start</code> and <code>stop</code> arguments represent score boundaries.
     */
    @Getter
    public static class RangeByScore implements ScoredRangeQuery {
        private final String start;
        private final String end;
        private final Limit limit;

        /**
         * Creates a range by score in a sorted set.<br>
         * The <code>start</code> and <code>stop</code> arguments represent score boundaries.
         *
         * @param start The start score boundary.
         * @param end The stop score boundary.
         * @param limit The limit argument for a range query. Defaults to null. See <code>Limit</code>
         *     class for more information.
         */
        public RangeByScore(
                @NonNull RangeOptions.ScoreRange start,
                @NonNull RangeOptions.ScoreRange end,
                @NonNull Limit limit) {
            this.start = start.toArgs();
            this.end = end.toArgs();
            this.limit = limit;
        }

        /**
         * Creates a range by score in a sorted set.<br>
         * The <code>start</code> and <code>stop</code> arguments represent score boundaries.
         *
         * @param start The start score boundary.
         * @param end The stop score boundary.
         */
        public RangeByScore(
                @NonNull RangeOptions.ScoreRange start, @NonNull RangeOptions.ScoreRange end) {
            this.start = start.toArgs();
            this.end = end.toArgs();
            this.limit = null;
        }
    }

    public static String[] createZRangeArgs(
            String key, RangeQuery rangeQuery, boolean reverse, boolean withScores) {
        String[] arguments =
                concatenateArrays(new String[] {key}, createZRangeBaseArgs(rangeQuery, reverse));
        if (withScores) {
            arguments = concatenateArrays(arguments, new String[] {WITH_SCORES_REDIS_API});
        }

        return arguments;
    }

    public static String[] createZRangeStoreArgs(
            String destination, String source, RangeQuery rangeQuery, boolean reverse) {
        return concatenateArrays(
                new String[] {destination, source}, createZRangeBaseArgs(rangeQuery, reverse));
    }

    private static String[] createZRangeBaseArgs(RangeQuery rangeQuery, boolean reverse) {
        String[] arguments = new String[] {rangeQuery.getStart(), rangeQuery.getEnd()};

        if (rangeQuery instanceof RangeByScore) {
            arguments = concatenateArrays(arguments, new String[] {"BYSCORE"});
        } else if (rangeQuery instanceof RangeByLex) {
            arguments = concatenateArrays(arguments, new String[] {"BYLEX"});
        }

        if (reverse) {
            arguments = concatenateArrays(arguments, new String[] {"REV"});
        }

        if (rangeQuery.getLimit() != null) {
            arguments =
                    concatenateArrays(
                            arguments,
                            new String[] {
                                "LIMIT",
                                Long.toString(rangeQuery.getLimit().getOffset()),
                                Long.toString(rangeQuery.getLimit().getCount())
                            });
        }

        return arguments;
    }
}
