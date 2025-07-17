/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.ft;

import lombok.experimental.SuperBuilder;

/**
 * Options for FT.AGGREGATE command.
 * This class provides configuration options for aggregating search results.
 */
@SuperBuilder
public class FTAggregateOptions {
    
    /**
     * Maximum number of results to return.
     */
    private final int limit;
    
    /**
     * Offset for pagination.
     */
    private final int offset;
    
    /**
     * Group by field.
     */
    private final String groupBy;
    
    /**
     * Whether to load document content.
     */
    private final boolean load;
    
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
     * Get the group by field.
     * @return The group by field
     */
    public String getGroupBy() {
        return groupBy;
    }
    
    /**
     * Check if document content should be loaded.
     * @return true if content should be loaded
     */
    public boolean isLoad() {
        return load;
    }
}