/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import glide.api.commands.servermodules.Json;

/** Type of debug information requested in {@link Json#debug} command. */
public enum JsonDebugType {
    /** Reports memory usage in bytes of a JSON value. */
    MEMORY,
    /**
     * Reports the number of fields at the specified document path. Each non-container JSON value
     * counts as one field. Objects and arrays recursively count one field for each of their
     * containing JSON values. Each container value, except the root container, counts as one
     * additional field.
     */
    FIELDS
}
