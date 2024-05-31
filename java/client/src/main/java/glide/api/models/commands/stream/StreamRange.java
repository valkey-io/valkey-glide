/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.stream;

import glide.utils.ArrayTransformUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Arguments for {@link glide.api.commands.StreamBaseCommands#xrange} and {@link
 * glide.api.commands.StreamBaseCommands#xrevrange} to specify the starting and ending range for the
 * stream search by stream ID.
 *
 * @see <a href="https://redis.io/commands/xrange/">redis.io</a>
 * @see <a href="https://redis.io/commands/xrevrange/">redis.io</a>
 */
public interface StreamRange {

    String getRedisApi();

    String MINIMUM_RANGE_REDIS_API = "-";
    String MAXIMUM_RANGE_REDIS_API = "+";
    String RANGE_COUNT_REDIS_API = "COUNT";

    /**
     * Enumeration representing minimum or maximum stream entry bounds for the range search, to get
     * the first or last stream ID.
     */
    @RequiredArgsConstructor
    @Getter
    enum InfRangeBound implements StreamRange {
        MIN(MINIMUM_RANGE_REDIS_API),
        MAX(MAXIMUM_RANGE_REDIS_API);

        private final String redisApi;
    };

    /**
     * Stream ID used to specify a range of IDs to search. Stream ID bounds can be complete with a
     * timestamp and sequence number separated by a dash (<code>"-"</code>), for example <code>
     * "1526985054069-0"</code>.<br>
     * Stream ID bounds can also be incomplete, with just a timestamp.<br>
     * Stream ID bounds are inclusive by default. When <code>isInclusive==false</code>, a <code>"("
     * </code> is prepended for the Redis API.
     */
    @Getter
    class IdBound implements StreamRange {
        private final String redisApi;

        /**
         * Default constructor
         *
         * @param id The stream id.
         */
        private IdBound(String id) {
            redisApi = id;
        }

        /**
         * Creates a stream ID boundary by stream id for range search.
         *
         * @param id The stream id.
         */
        public static IdBound of(String id) {
            return new IdBound(id);
        }

        /**
         * Creates an incomplete stream ID boundary without the sequence number for range search.
         *
         * @param timestamp The stream timestamp as ID.
         */
        public static IdBound of(long timestamp) {
            return new IdBound(Long.toString(timestamp));
        }

        /**
         * Creates an incomplete stream ID exclusive boundary without the sequence number for range
         * search.
         *
         * @param timestamp The stream timestamp as ID.
         */
        public static IdBound ofExclusive(long timestamp) {
            return new IdBound("(" + timestamp);
        }

        /**
         * Creates a stream ID exclusive boundary by stream id for range search.
         *
         * @param id The stream id.
         */
        public static IdBound ofExclusive(String id) {
            return new IdBound("(" + id);
        }
    }

    /**
     * Convert StreamRange arguments to a string array
     *
     * @return arguments converted to an array to be consumed by Redis
     */
    static String[] toArgs(StreamRange start, StreamRange end) {
        return new String[] {start.getRedisApi(), end.getRedisApi()};
    }

    /**
     * Convert StreamRange arguments to a string array
     *
     * @return arguments converted to an array to be consumed by Redis
     */
    static String[] toArgs(StreamRange start, StreamRange end, long count) {
        return ArrayTransformUtils.concatenateArrays(
                toArgs(start, end), new String[] {RANGE_COUNT_REDIS_API, Long.toString(count)});
    }
}
