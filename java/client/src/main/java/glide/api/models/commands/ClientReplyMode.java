/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.ConnectionManagementClusterCommands;
import glide.api.commands.ConnectionManagementCommands;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines the reply mode for the <code>CLIENT REPLY</code> command.
 *
 * @see ConnectionManagementCommands#clientReply(ClientReplyMode)
 * @see ConnectionManagementClusterCommands#clientReply(ClientReplyMode, Route)
 * @see <a href="https://valkey.io/commands/client-reply/">client-reply</a> at valkey.io
 */
@RequiredArgsConstructor
@Getter
public enum ClientReplyMode {

    /** Resume normal reply behavior. */
    ON("ON"),

    /** Suppress all replies until <code>CLIENT REPLY ON</code> is sent. */
    OFF("OFF"),

    /** Suppress the reply for the next command only. */
    SKIP("SKIP");

    private final String valkeyApi;
}
