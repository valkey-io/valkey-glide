/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.vss;

import static glide.api.models.GlideString.gs;

import glide.api.commands.VectorSearchBaseCommands;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import org.apache.commons.lang3.tuple.Pair;

/** Mandatory parameters for {@link VectorSearchBaseCommands#ftsearch}. */
@Builder
public class FTSearchOptions {
    /**
     * Which fields of a key to be returned.<br>
     * Map keys are field names and their values are aliases. Aliases are optional, use <code>null
     * </code> to omit.
     */
    @Builder.Default private final Map<String, String> identifiers = new HashMap<>();

    /** Query timeout in milliseconds. */
    private final Integer timeout;

    private final Pair<Integer, Integer> limit;

    @Builder.Default private final boolean count = false;

    /**
     * Query parameters, which could be referenced in the query by <code>$</code> sign, followed by
     * the parameter name.
     */
    @Builder.Default private final Map<String, GlideString> params = new HashMap<>();

    // TODO maxstale?
    // dialect is no-op

    /** Convert to module API. */
    public GlideString[] toArgs() {
        var args = new ArrayList<GlideString>();
        if (!identifiers.isEmpty()) {
            args.add(gs("RETURN"));
            int tokenCount = 0;
            for (var pair : identifiers.entrySet()) {
                tokenCount++;
                args.add(gs(pair.getKey()));
                if (pair.getValue() != null) {
                    tokenCount += 2;
                    args.add(gs("AS"));
                    args.add(gs(pair.getValue()));
                }
            }
            args.add(1, gs(Integer.toString(tokenCount)));
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
        if (limit != null) {
            args.add(gs("LIMIT"));
            args.add(gs(Integer.toString(limit.getLeft())));
            args.add(gs(Integer.toString(limit.getRight())));
        }
        if (count) {
            args.add(gs("COUNT"));
        }
        return args.toArray(GlideString[]::new);
    }

    public static class FTSearchOptionsBuilder {

        // private - hiding this API from user
        void limit(Pair<Integer, Integer> limit) {}

        void count(boolean count) {}

        /**
         * Configure query pagination. By default only first 10 documents are returned.
         *
         * @param offset Zero-based offset.
         * @param count Number of elements to return.
         */
        public FTSearchOptionsBuilder limit(int offset, int count) {
            this.limit = Pair.of(offset, count);
            return this;
        }

        /**
         * Once set, the query will return only number of documents in the result set without actually
         * returning them.
         */
        public FTSearchOptionsBuilder count() {
            this.count$value = true;
            this.count$set = true;
            return this;
        }
    }
}
