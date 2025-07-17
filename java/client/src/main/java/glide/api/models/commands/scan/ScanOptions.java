/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

/**
 * Optional arguments for SCAN command.
 */
public class ScanOptions {
    
    /** The TYPE option string for SCAN command. */
    public static final String TYPE_OPTION_STRING = "TYPE";
    
    /**
     * Object type filter for SCAN command.
     */
    public enum ObjectType {
        STRING,
        LIST, 
        SET,
        ZSET,
        HASH,
        STREAM
    }
    
    // Additional scan options to be implemented
}
