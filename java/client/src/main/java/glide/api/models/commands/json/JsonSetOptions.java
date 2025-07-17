/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import lombok.experimental.SuperBuilder;

/**
 * Options for JSON SET command.
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
     * Get the set mode.
     * @return The set mode
     */
    public SetMode getMode() {
        return mode;
    }
}