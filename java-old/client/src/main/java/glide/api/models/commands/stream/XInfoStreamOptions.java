/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.stream;

import glide.api.commands.StreamBaseCommands;
import glide.api.models.BaseBatch;

/**
 * Valkey API Keywords for <code>XINFO STREAM</code> command represented by {@link
 * StreamBaseCommands#xinfoStreamFull} and {@link BaseBatch#xinfoStreamFull}.
 */
public abstract class XInfoStreamOptions {
    /** Used by <code>XINFO STREAM</code> to query detailed info. */
    public static final String FULL = "FULL";

    /** Used by <code>XINFO STREAM</code> to limit count of PEL entries. */
    public static final String COUNT = "COUNT";
}
