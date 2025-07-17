/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

/**
 * Stub implementation of FT.SEARCH options for compilation compatibility.
 * This is a basic stub to allow tests to compile and will need full implementation.
 */
public class FTSearchOptions {
    
    // Basic stub - constructor
    public FTSearchOptions() {
    }
    
    // Stub method for setting limit
    public FTSearchOptions limit(int offset, int count) {
        return this;
    }
    
    // Stub method for setting return fields
    public FTSearchOptions returnFields(String... fields) {
        return this;
    }
    
    // Stub method for setting sort by
    public FTSearchOptions sortBy(String field, boolean ascending) {
        return this;
    }
}