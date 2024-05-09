/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.utils.ArrayTransformUtils.concatenateArrays;

import glide.api.commands.SortedSetBaseCommands;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Arguments for {@link SortedSetBaseCommands#zunion}, {@link
 * SortedSetBaseCommands#zunionWithScores}, and {@link SortedSetBaseCommands#zinterstore}.
 *
 * @see <a href="https://redis.io/commands/zunion/">redis.io</a> for more details.
 * @see <a href="https://redis.io/commands/zinterstore/">redis.io</a> for more details.
 */
public abstract class WeightAggregateOptions {
    public static final String WEIGHTS_REDIS_API = "WEIGHTS";
    public static final String AGGREGATE_REDIS_API = "AGGREGATE";

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

        public String[] toArgs() {
            return new String[] {AGGREGATE_REDIS_API, toString()};
        }
    }

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link KeyArray}
     *   <li>{@link WeightedKeys}
     * </ul>
     */
    public interface KeysOrWeightedKeys {
        /** Convert to command arguments according to the Redis API. */
        String[] toArgs();
    }

    /** Represents the keys of the sorted sets involved in the aggregation operation. */
    @RequiredArgsConstructor
    public static class KeyArray implements KeysOrWeightedKeys {
        private final String[] keys;

        @Override
        public String[] toArgs() {
            return concatenateArrays(new String[] {Integer.toString(keys.length)}, keys);
        }
    }

    /**
     * Represents the mapping of sorted set keys to their score weights. Each weight is used to boost
     * the scores of elements in the corresponding sorted set by multiplying them before their scores
     * are aggregated.
     */
    @RequiredArgsConstructor
    public static class WeightedKeys implements KeysOrWeightedKeys {
        private final List<Pair<String, Double>> keysWeights;

        @Override
        public String[] toArgs() {
            List<String> keys = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            List<String> argumentsList = new ArrayList<>();

            for (Pair<String, Double> entry : keysWeights) {
                keys.add(entry.getLeft());
                weights.add(entry.getRight());
            }
            argumentsList.add(Integer.toString(keys.size()));
            argumentsList.addAll(keys);
            argumentsList.add(WEIGHTS_REDIS_API);
            for (Double weight : weights) {
                argumentsList.add(weight.toString());
            }

            return argumentsList.toArray(new String[0]);
        }
    }
}
