/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.search;

import java.util.List;

/** SearchResult compatibility stub for Valkey GLIDE wrapper. */
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

    /** SearchResultBuilder compatibility stub. */
    public static class SearchResultBuilder {
        private long totalResults = 0;
        private List<Document> documents = java.util.Collections.emptyList();

        public SearchResultBuilder totalResults(long totalResults) {
            this.totalResults = totalResults;
            return this;
        }

        public SearchResultBuilder documents(List<Document> documents) {
            this.documents = documents;
            return this;
        }

        public SearchResult build() {
            return new SearchResult(totalResults, documents);
        }
    }
}
