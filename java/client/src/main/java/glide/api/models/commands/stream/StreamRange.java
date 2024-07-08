/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.stream;

import glide.api.models.GlideString;
import glide.utils.ArrayTransformUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Arguments for {@link glide.api.commands.StreamBaseCommands#xrange} and {@link
 * glide.api.commands.StreamBaseCommands#xrevrange} to specify the starting and ending range for the
 * stream search by stream ID.
 *
 * @see <a href="https://valkey.io/commands/xrange/">valkey.io</a>
 * @see <a href="https://valkey.io/commands/xrevrange/">valkey.io</a>
 */
public interface StreamRange {

    String getValkeyApi();

    /** Valkey API string for MINIMUM entry ID range bounds */
    String MINIMUM_RANGE_VALKEY_API = "-";

    /** Valkey API string for MAXIMUM entry ID range bounds */
    String MAXIMUM_RANGE_VALKEY_API = "+";

    /** Valkey API string to designate COUNT */
    String RANGE_COUNT_VALKEY_API = "COUNT";

    /** Valkey API character to designate exclusive range bounds */
    String EXCLUSIVE_RANGE_VALKEY_API = "(";

    /**
     * Enumeration representing minimum or maximum stream entry bounds for the range search, to get
     * the first or last stream ID.
     */
    @RequiredArgsConstructor
    @Getter
    enum InfRangeBound implements StreamRange {
        MIN(MINIMUM_RANGE_VALKEY_API),
        MAX(MAXIMUM_RANGE_VALKEY_API);

        private final String valkeyApi;
    };

    /**
     * Stream ID used to specify a range of IDs to search. Stream ID bounds can be complete with a
     * timestamp and sequence number separated by a dash (<code>"-"</code>), for example <code>
     * "1526985054069-0"</code>.<br>
     * Stream ID bounds can also be incomplete, with just a timestamp.<br>
     * Stream ID bounds are inclusive by default. When <code>isInclusive==false</code>, a <code>"("
     * </code> is prepended for the Valkey API.
     */
    @Getter
    class IdBound implements StreamRange {
        private final String valkeyApi;

        /**
         * Default constructor
         *
         * @param id The stream id.
         */
        private IdBound(String id) {
            valkeyApi = id;
        }

        /**
         * Default constructor
         *
         * @param id The stream id.
         */
        private IdBound(GlideString id) {
            valkeyApi = id.getString();
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
         * Creates a stream ID boundary by stream id for range search.
         *
         * @param id The stream id.
         */
        public static IdBound of(GlideString id) {
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
            return new IdBound(EXCLUSIVE_RANGE_VALKEY_API + timestamp);
        }

        /**
         * Creates a stream ID exclusive boundary by stream id for range search.
         *
         * @param id The stream id.
         */
        public static IdBound ofExclusive(String id) {
            return new IdBound(EXCLUSIVE_RANGE_VALKEY_API + id);
        }

        /**
         * Creates a stream ID exclusive boundary by stream id for range search.
         *
         * @param id The stream id.
         */
        public static IdBound ofExclusive(GlideString id) {
            return new IdBound(EXCLUSIVE_RANGE_VALKEY_API + id.getString());
        }
    }

    /**
     * Convert StreamRange arguments to a string array
     *
     * @return arguments converted to an array to be consumed by Valkey.
     */
    static String[] toArgs(StreamRange start, StreamRange end) {
        return new String[] {start.getValkeyApi(), end.getValkeyApi()};
    }

    /**
     * Convert StreamRange arguments to a string array
     *
     * @return arguments converted to an array to be consumed by Valkey.
     */
    static String[] toArgs(StreamRange start, StreamRange end, long count) {
        return ArrayTransformUtils.concatenateArrays(
                toArgs(start, end), new String[] {RANGE_COUNT_VALKEY_API, Long.toString(count)});
    }
}
