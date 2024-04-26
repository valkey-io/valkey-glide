/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.StreamBaseCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;

/** Optional arguments for {@link StreamBaseCommands} */
public final class StreamOptions {

    /**
     * Optional arguments to {@link StreamBaseCommands#xadd(String, Map, StreamAddOptions)}
     *
     * @see <a href="https://redis.io/commands/xadd/">redis.io</a>
     */
    @Builder
    public static final class StreamAddOptions {
        public static final String NO_MAKE_STREAM_REDIS_API = "NOMKSTREAM";
        public static final String ID_WILDCARD_REDIS_API = "*";

        /** If set, the new entry will be added with this <code>id</code>. */
        private final String id;

        /**
         * If set to <code>false</code>, a new stream won't be created if no stream matches the given
         * key. <br>
         * Equivalent to <code>NOMKSTREAM</code> in the Redis API.
         */
        private final Boolean makeStream;

        /** If set, the add operation will also trim the older entries in the stream. */
        private final StreamTrimOptions trim;

        /**
         * Converts options for Xadd into a String[].
         *
         * @return String[]
         */
        public String[] toArgs() {
            List<String> optionArgs = new ArrayList<>();

            if (makeStream != null && !makeStream) {
                optionArgs.add(NO_MAKE_STREAM_REDIS_API);
            }

            if (trim != null) {
                optionArgs.addAll(trim.getRedisApi());
            }

            if (id != null) {
                optionArgs.add(id);
            } else {
                optionArgs.add(ID_WILDCARD_REDIS_API);
            }

            return optionArgs.toArray(new String[0]);
        }
    }

    /**
     * Optional arguments for {@link StreamBaseCommands#xtrim(String, StreamTrimOptions)}
     *
     * @see <a href="https://redis.io/commands/xtrim/">redis.io</a>
     */
    public abstract static class StreamTrimOptions {

        public static final String TRIM_MAXLEN_REDIS_API = "MAXLEN";
        public static final String TRIM_MINID_REDIS_API = "MINID";
        public static final String TRIM_EXACT_REDIS_API = "=";
        public static final String TRIM_NOT_EXACT_REDIS_API = "~";
        public static final String TRIM_LIMIT_REDIS_API = "LIMIT";

        /**
         * If <code>true</code>, the stream will be trimmed exactly. Equivalent to <code>=</code> in the
         * Redis API. Otherwise, the stream will be trimmed in a near-exact manner, which is more
         * efficient, equivalent to <code>~</code> in the Redis API.
         */
        protected Boolean exact;

        /** If set, sets the maximal amount of entries that will be deleted. */
        protected Long limit;

        protected abstract String getMethod();

        protected abstract String getThreshold();

        protected List<String> getRedisApi() {
            List<String> optionArgs = new ArrayList<>();

            optionArgs.add(this.getMethod());
            if (this.exact != null) {
                optionArgs.add(this.exact ? TRIM_EXACT_REDIS_API : TRIM_NOT_EXACT_REDIS_API);
            }
            optionArgs.add(this.getThreshold());
            if (this.limit != null) {
                optionArgs.add(TRIM_LIMIT_REDIS_API);
                optionArgs.add(this.limit.toString());
            }

            return optionArgs;
        }

        /**
         * Converts options for {@link StreamBaseCommands#xtrim(String, StreamTrimOptions)} into a
         * String[].
         *
         * @return String[]
         */
        public String[] toArgs() {
            List<String> optionArgs = new ArrayList<>();

            if (this.getRedisApi() != null) {
                optionArgs.addAll(this.getRedisApi());
            }

            return optionArgs.toArray(new String[0]);
        }
    }

    /** Option to trim the stream according to minimum ID. */
    public static class MinId extends StreamTrimOptions {
        /** Trim the stream according to entry ID. Equivalent to <code>MINID</code> in the Redis API. */
        private final String threshold;

        /**
         * Create a trim option to trim stream based on stream ID.
         *
         * @param threshold Comparison id.
         */
        public MinId(@NonNull String threshold) {
            this.threshold = threshold;
        }

        /**
         * Create a trim option to trim stream based on stream ID.
         *
         * @param exact Whether to match exactly on the threshold.
         * @param threshold Comparison id.
         */
        public MinId(boolean exact, @NonNull String threshold) {
            this.threshold = threshold;
            this.exact = exact;
        }

        /**
         * Create a trim option to trim stream based on stream ID.
         *
         * @param threshold Comparison id.
         * @param limit Max number of stream entries to be trimmed for non-exact match.
         */
        public MinId(@NonNull String threshold, long limit) {
            this.exact = false;
            this.threshold = threshold;
            this.limit = limit;
        }

        @Override
        protected String getMethod() {
            return TRIM_MINID_REDIS_API;
        }

        @Override
        protected String getThreshold() {
            return threshold;
        }
    }

    /** Option to trim the stream according to maximum stream length. */
    public static class MaxLen extends StreamTrimOptions {
        /**
         * Trim the stream according to length.<br>
         * Equivalent to <code>MAXLEN</code> in the Redis API.
         */
        private final Long threshold;

        /**
         * Create a Max Length trim option to trim stream based on length.
         *
         * @param threshold Comparison count.
         */
        public MaxLen(long threshold) {
            this.threshold = threshold;
        }

        /**
         * Create a Max Length trim option to trim stream based on length.
         *
         * @param exact Whether to match exactly on the threshold.
         * @param threshold Comparison count.
         */
        public MaxLen(boolean exact, long threshold) {
            this.threshold = threshold;
            this.exact = exact;
        }

        /**
         * Create a Max Length trim option to trim stream entries exceeds the threshold.
         *
         * @param threshold Comparison count.
         * @param limit Max number of stream entries to be trimmed for non-exact match.
         */
        public MaxLen(long threshold, long limit) {
            this.exact = false;
            this.threshold = threshold;
            this.limit = limit;
        }

        @Override
        protected String getMethod() {
            return TRIM_MAXLEN_REDIS_API;
        }

        @Override
        protected String getThreshold() {
            return threshold.toString();
        }
    }
}
