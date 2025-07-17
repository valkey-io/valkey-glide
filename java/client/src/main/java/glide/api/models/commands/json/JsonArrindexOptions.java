/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import lombok.experimental.SuperBuilder;

/**
 * Options for JSON ARRINDEX command.
 * This class provides configuration options for finding array indices in JSON documents.
 */
@SuperBuilder
public class JsonArrindexOptions {
    
    /**
     * The start index for the search.
     */
    private final int start;
    
    /**
     * The end index for the search.
     */
    private final int end;
    
    /**
     * Get the start index.
     * @return The start index
     */
    public int getStart() {
        return start;
    }
    
    /**
     * Get the end index.
     * @return The end index
     */
    public int getEnd() {
        return end;
    }
}