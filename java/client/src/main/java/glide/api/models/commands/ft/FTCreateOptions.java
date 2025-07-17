/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.ft;

import lombok.experimental.SuperBuilder;

/**
 * Options for FT.CREATE command.
 * This class provides configuration options for creating search indices.
 */
@SuperBuilder
public class FTCreateOptions {
    
    /**
     * Index data structure types.
     */
    public enum IndexType {
        /** Hash index */
        HASH,
        /** JSON index */
        JSON
    }
    
    /**
     * The type of index to create.
     */
    private final IndexType indexType;
    
    /**
     * Key prefix for the index.
     */
    private final String prefix;
    
    /**
     * Language for text processing.
     */
    private final String language;
    
    /**
     * Get the index type.
     * @return The index type
     */
    public IndexType getIndexType() {
        return indexType;
    }
    
    /**
     * Get the key prefix.
     * @return The key prefix
     */
    public String getPrefix() {
        return prefix;
    }
    
    /**
     * Get the language.
     * @return The language
     */
    public String getLanguage() {
        return language;
    }
}