/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.search;

import java.util.List;
import java.util.Map;
import redis.clients.jedis.Builder;

/** SearchResult compatibility class for Valkey GLIDE wrapper. */
public class SearchResult {
    private final long totalResults;
    private final List<Document> documents;

    public SearchResult(long totalResults, List<Document> documents) {
        this.totalResults = totalResults;
        this.documents = documents;
    }

    public long getTotalResults() {
        return totalResults;
    }

    public List<Document> getDocuments() {
        return documents;
    }

    @Override
    public String toString() {
        return "SearchResult{totalResults=" + totalResults + ", documents=" + documents + "}";
    }

    /** SearchResultBuilder compatibility class. */
    public static class SearchResultBuilder extends Builder<SearchResult> {
        private final boolean hasContent;
        private final boolean hasScores;
        private final boolean hasPayloads;
        private final boolean decode;
        private final Map<String, Boolean> isFieldDecode;

        public SearchResultBuilder() {
            this(false, false, false, false);
        }

        public SearchResultBuilder(
                boolean hasContent, boolean hasScores, boolean hasPayloads, boolean decode) {
            this.hasContent = hasContent;
            this.hasScores = hasScores;
            this.hasPayloads = hasPayloads;
            this.decode = decode;
            this.isFieldDecode = null;
        }

        public SearchResultBuilder(
                boolean hasContent, boolean hasScores, boolean decode, Map<String, Boolean> isFieldDecode) {
            this.hasContent = hasContent;
            this.hasScores = hasScores;
            this.hasPayloads = false;
            this.decode = decode;
            this.isFieldDecode = isFieldDecode;
        }

        @Override
        public SearchResult build(Object data) {
            // Simple stub implementation
            return new SearchResult(0, java.util.Collections.emptyList());
        }
    }
}
