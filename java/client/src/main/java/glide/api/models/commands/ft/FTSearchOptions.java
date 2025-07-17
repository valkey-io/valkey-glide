/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.ft;

import lombok.experimental.SuperBuilder;

/**
 * Options for FT.SEARCH command.
 * This class provides configuration options for searching indices.
 */
@SuperBuilder
public class FTSearchOptions {
    
    /**
     * Maximum number of results to return.
     */
    private final int limit;
    
    /**
     * Offset for pagination.
     */
    private final int offset;
    
    /**
     * Whether to return only document IDs.
     */
    private final boolean noContent;
    
    /**
     * Sort by field.
     */
    private final String sortBy;
    
    /**
     * Sort order ascending/descending.
     */
    private final boolean ascending;
    
    /**
     * Get the limit.
     * @return The limit
     */
    public int getLimit() {
        return limit;
    }
    
    /**
     * Get the offset.
     * @return The offset
     */
    public int getOffset() {
        return offset;
    }
    
    /**
     * Check if no content should be returned.
     * @return true if no content should be returned
     */
    public boolean isNoContent() {
        return noContent;
    }
    
    /**
     * Get the sort field.
     * @return The sort field
     */
    public String getSortBy() {
        return sortBy;
    }
    
    /**
     * Check if sort order is ascending.
     * @return true if ascending
     */
    public boolean isAscending() {
        return ascending;
    }
}