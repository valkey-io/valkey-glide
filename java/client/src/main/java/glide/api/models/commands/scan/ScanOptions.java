/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

/**
 * Optional arguments for SCAN command.
 */
public class ScanOptions {
    
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
