/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;

/**
 * Defines reply modes for the <code>CLIENT REPLY</code> command.
 *
 * <ul>
 *   <li><code>CLIENT REPLY</code> command implemented by {@link
 *       GlideClient#clientReply(ClientReplyMode)}, {@link
 *       GlideClusterClient#clientReply(ClientReplyMode)}, and {@link
 *       GlideClusterClient#clientReply(ClientReplyMode, Route)}.
 * </ul>
 *
 * @since Valkey 3.2 and above.
 * @see <a href="https://valkey.io/commands/client-reply/">client-reply</a> at valkey.io
 */
public enum ClientReplyMode {
    /** The server will reply to commands as usual. */
    ON,

    /**
     * The server will not reply to commands. The connection is still open and can receive commands.
     */
    OFF,

    /** The server will skip the reply of the immediately following command. */
    SKIP;

    // Pre-allocated argument arrays to reduce allocations
    private static final String[] ON_ARGS = new String[] {"ON"};
    private static final String[] OFF_ARGS = new String[] {"OFF"};
    private static final String[] SKIP_ARGS = new String[] {"SKIP"};

    /**
     * Returns the command arguments for this client reply mode. Uses pre-allocated arrays to reduce
     * GC pressure.
     *
     * @return Array containing the reply mode as a string argument
     */
    public String[] toArgs() {
        switch (this) {
            case ON:
                return ON_ARGS;
            case OFF:
                return OFF_ARGS;
            case SKIP:
                return SKIP_ARGS;
            default:
                throw new IllegalStateException("Unexpected ClientReplyMode: " + this);
        }
    }
}
