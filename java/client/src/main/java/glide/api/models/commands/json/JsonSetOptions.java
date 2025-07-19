/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import lombok.experimental.SuperBuilder;

/**
 * Options for JSON Set command.
 * This class provides configuration options for setting JSON values.
 */
@SuperBuilder
public class JsonSetOptions {
    
    /**
     * Set operation modes.
     */
    public enum SetMode {
        /** Only set if the key doesn't exist */
        NX,
        /** Only set if the key exists */
        XX
    }
    
    /**
     * The set mode to use.
     */
    private final SetMode mode;
    
    /**
     * GET the set mode.
     * @return The set mode
     */
    public SetMode getMode() {
        return mode;
    }
}