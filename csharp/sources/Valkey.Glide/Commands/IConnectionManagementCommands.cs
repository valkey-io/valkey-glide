// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for connection management that are common to both standalone and cluster clients.
/// </summary>
internal interface IConnectionManagementCommands
{
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

    /// <summary>
    /// Ping the server and measure the round-trip time.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/ping/">valkey.io</seealso> for details.
    /// <param name="route">Specifies the routing configuration for the command. The client will route the
    /// command to the nodes defined by <c>route</c>.</param>
    /// <param name="flags">The command flags. Currently flags are ignored.</param>
    /// <returns>
    /// The round-trip time as a <see cref="TimeSpan"/>.<br />
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// TimeSpan response = await client.PingAsync(Route.AllPrimaries);
    /// </code>
    /// </example>
    /// </remarks>
    Task<TimeSpan> PingAsync(Route route, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Ping the server and measure the round-trip time.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/ping/">valkey.io</seealso> for details.
    /// <param name="message">The message to send with the ping</param>
    /// <param name="route">Specifies the routing configuration for the command. The client will route the
    /// command to the nodes defined by <c>route</c>.</param>
    /// <param name="flags">The command flags. Currently flags are ignored.</param>
    /// <returns>
    /// The round-trip time as a <see cref="TimeSpan"/>.<br />
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// TimeSpan response = await client.PingAsync("Hello World", Route.AllPrimaries);
    /// </code>
    /// </example>
    /// </remarks>
    Task<TimeSpan> PingAsync(ValkeyValue message, Route route, CommandFlags flags = CommandFlags.None);
}
