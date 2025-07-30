// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Commands.Options.InfoOptions;

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "Server Management" group for a standalone client.
/// <br />
/// See <see href="https://valkey.io/commands#server">Server Management Commands</see>.
/// </summary>
public interface IServerManagementCommands
{
    /// <summary>
    /// Get information and statistics about the server using <see cref="Section.DEFAULT" /> option.<br />
    /// See <see href="https://valkey.io/commands/info/">valkey.io</see> for details.
    /// </summary>
    /// <inheritdoc cref="IServerManagementClusterCommands.Info()" path="/remarks" />
    /// <returns>A <see langword="string" /> containing the information for the sections requested.</returns>
    Task<string> Info();

    /// <summary>
    /// Get information and statistics about the server.<br />
    /// Starting from server version 7, command supports multiple <see cref="Section" /> arguments.<br />
    /// See <see href="https://valkey.io/commands/info/">valkey.io</see> for details.
    /// </summary>
    /// <inheritdoc cref="IServerManagementClusterCommands.Info(Section[])" path="/remarks" />
    /// <inheritdoc cref="IServerManagementClusterCommands.Info(Section[])" path="/param" />
    /// <returns>
    /// <inheritdoc cref="Info()" />
    /// </returns>
    Task<string> Info(Section[] sections);

    /// <summary>
    /// Echo the given message back from the server.<br />
    /// See <see href="https://valkey.io/commands/echo/">valkey.io</see> for details.
    /// </summary>
    /// <param name="message">The message to echo</param>
    /// <param name="flags">The command flags. Currently flags are ignored.</param>
    /// <returns>The echoed message as a <see cref="ValkeyValue"/>.</returns>
    Task<ValkeyValue> EchoAsync(ValkeyValue message, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Ping the server and measure the round-trip time.<br />
    /// See <see href="https://valkey.io/commands/ping/">valkey.io</see> for details.
    /// </summary>
    /// <param name="flags">The command flags. Currently flags are ignored.</param>
    /// <returns>The round-trip time as a <see cref="TimeSpan"/>.</returns>
    Task<TimeSpan> PingAsync(CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Ping the server with a message and measure the round-trip time.<br />
    /// See <see href="https://valkey.io/commands/ping/">valkey.io</see> for details.
    /// </summary>
    /// <param name="message">The message to send with the ping</param>
    /// <param name="flags">The command flags. Currently flags are ignored.</param>
    /// <returns>The round-trip time as a <see cref="TimeSpan"/>.</returns>
    Task<TimeSpan> PingAsync(ValkeyValue message, CommandFlags flags = CommandFlags.None);
}
