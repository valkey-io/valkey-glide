/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import lombok.experimental.SuperBuilder;

/**
 * Options for JSON GET command.
 * This class provides configuration options for retrieving JSON values.
 */
@SuperBuilder
public class JsonGetOptions {
    
    /**
     * The path to retrieve from the JSON document.
     * If not specified, the entire document is returned.
     */
    private final String path;
    
    /**
     * Whether to format the output with indentation.
     */
    private final boolean indent;
    
    /**
     * Whether to include newlines in the output.
     */
    private final boolean newline;
    
    /**
     * Whether to include spaces in the output.
     */
    private final boolean space;
    
    /**
     * Get the path to retrieve.
     * @return The path string
     */
    public String getPath() {
        return path;
    }
    
    /**
     * Check if indentation is enabled.
     * @return true if indentation is enabled
     */
    public boolean isIndent() {
        return indent;
    }
    
    /**
     * Check if newlines are enabled.
     * @return true if newlines are enabled
     */
    public boolean isNewline() {
        return newline;
    }
    
    /**
     * Check if spaces are enabled.
     * @return true if spaces are enabled
     */
    public boolean isSpace() {
        return space;
    }
}