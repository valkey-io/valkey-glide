/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.params;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameters for sorted set aggregation operations like ZUNIONSTORE, ZINTERSTORE, ZUNION, ZINTER,
 * etc. Provides options for specifying weights and aggregation functions (SUM, MIN, MAX).
 *
 * <p>This class is compatible with Jedis ZParams and provides the same builder-style API.
 */
public class ZParams {

    public enum Aggregate {
        SUM,
        MIN,
        MAX
    }

    private final List<Double> weightsList = new ArrayList<>();
    private Aggregate aggregate;

    public ZParams() {}

    /**
     * Set weights for the input sorted sets.
     *
     * @param weights the weights to apply to each input sorted set
     * @return ZParams
     */
    public ZParams weights(final double... weights) {
        for (final double weight : weights) {
            weightsList.add(weight);
        }
        return this;
    }

    /**
     * Set the aggregation function to use when combining scores.
     *
     * @param aggregate the aggregation function (SUM, MIN, or MAX)
     * @return ZParams
     */
    public ZParams aggregate(final Aggregate aggregate) {
        this.aggregate = aggregate;
        return this;
    }

    public List<Double> getWeights() {
        return weightsList;
    }

    public Aggregate getAggregate() {
        return aggregate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZParams zParams = (ZParams) o;
        return java.util.Objects.equals(weightsList, zParams.weightsList)
                && aggregate == zParams.aggregate;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(weightsList, aggregate);
    }
}
