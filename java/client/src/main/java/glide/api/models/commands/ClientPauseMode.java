/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.ConnectionManagementClusterCommands;
import glide.api.commands.ConnectionManagementCommands;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines the pause mode for the <code>CLIENT PAUSE</code> command.
 *
 * @see ConnectionManagementCommands#clientPause(long, ClientPauseMode)
 * @see ConnectionManagementClusterCommands#clientPause(long, ClientPauseMode)
 * @see ConnectionManagementClusterCommands#clientPause(long, ClientPauseMode, Route)
 * @see <a href="https://valkey.io/commands/client-pause/">client-pause</a> at valkey.io
 */
@RequiredArgsConstructor
@Getter
public enum ClientPauseMode {

    /** Pause all client commands. */
    ALL("ALL"),

    /** Pause client write commands. */
    WRITE("WRITE");

    private final String valkeyApi;
}
