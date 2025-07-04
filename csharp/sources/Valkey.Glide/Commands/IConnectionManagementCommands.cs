// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "Connection Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=string#connection">valkey.io</see>.
/// </summary>
internal interface IConnectionManagementCommands
{
    /// <summary>
    /// Pings the server.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/ping"/>
    /// <param name="ignored">The command flags to use. Currently flags are ignored.</param>
    /// <returns>The observed latency.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// TimeSpan result = await client.PingAsync();
    /// </code>
    /// </example>
    /// </remarks>
    Task<TimeSpan> PingAsync(CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Pings the server.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/ping"/>
    /// <param name="message">The message to send.</param>
    /// <param name="ignored">The command flags to use. Currently flags are ignored.</param>
    /// <returns>The observed latency.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// TimeSpan result = await client.PingAsync("ping!");
    /// </code>
    /// </example>
    /// </remarks>
    Task<TimeSpan> PingAsync(ValkeyValue message, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Echo the provided message back.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/echo"/>
    /// <param name="message">The provided message.</param>
    /// <param name="ignored">The command flags to use. Currently flags are ignored.</param>
    /// <returns>The provided message.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue result = await client.EchoAsync(key, value);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> EchoAsync(ValkeyValue message, CommandFlags ignored = CommandFlags.None);
}
