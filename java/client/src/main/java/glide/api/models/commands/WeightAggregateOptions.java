/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.SortedSetBaseCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Singular;

/**
 * Optional arguments to {@link SortedSetBaseCommands#zunion(String[], WeightAggregateOptions)}, and
 * {@link SortedSetBaseCommands#zunionWithScores(String[], WeightAggregateOptions)}.
 *
 * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
 */
@Builder
public final class WeightAggregateOptions {
    public static final String WEIGHTS_REDIS_API = "WEIGHTS";
    public static final String AGGREGATE_REDIS_API = "AGGREGATE";

    /**
     * Represents multiplication factors for each sorted set, ready for aggregation. Each
     * multiplication factor corresponds one-to-one with the sets. The score of every element in these
     * sets is multiplied by its associated factor before aggregation.
     */
    @Singular private final List<Double> weights;

    /**
     * Option for the method of aggregating scores from multiple sets. This option defaults to SUM if
     * not specified.
     */
    private final Aggregate aggregate;

    /**
     * Option for the method of aggregating scores from multiple sets. This option defaults to SUM if
     * not specified.
     */
    public enum Aggregate {
        /** Aggregates by summing the scores of each element across sets. */
        SUM,
        /** Aggregates by selecting the minimum score for each element across sets. */
        MIN,
        /** Aggregates by selecting the maximum score for each element across sets. */
        MAX;
    }

    /**
     * Converts WeightAggregateOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>();

        if (!weights.isEmpty()) {
            optionArgs.add(WEIGHTS_REDIS_API);
            optionArgs.addAll(
                    weights.stream().map(element -> Double.toString(element)).collect(Collectors.toList()));
        }

        if (aggregate != null) {
            optionArgs.addAll(List.of(AGGREGATE_REDIS_API, aggregate.toString()));
        }

        return optionArgs.toArray(new String[0]);
    }
}
