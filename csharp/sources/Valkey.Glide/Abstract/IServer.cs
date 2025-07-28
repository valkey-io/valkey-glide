// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Net;

namespace Valkey.Glide;

public interface IServer
{
    /// <summary>
    /// Gets the address of the connected server.
    /// </summary>
    EndPoint EndPoint { get; }

    /// <summary>
    /// Gets whether the connection to the server is active and usable.
    /// </summary>
    bool IsConnected { get; }

    /// <summary>
    /// The protocol being used to communicate with this server (if not connected/known, then the anticipated protocol from the configuration is returned, assuming success).
    /// </summary>
    Protocol Protocol { get; }

    /// <summary>
    /// Gets the version of the connected server.
    /// </summary>
    Version Version { get; }

    /// <summary>
    /// Gets the operating mode of the connected server.
    /// </summary>
    ServerType ServerType { get; }

    /// <summary>
    /// Execute an arbitrary command against the server; this is primarily intended for
    /// executing modules, but may also be used to provide access to new features that lack
    /// a direct API.
    /// </summary>
    /// <param name="command">The command to run.</param>
    /// <param name="args">The arguments to pass for the command.</param>
    /// <returns>A dynamic representation of the command's result.</returns>
    /// <remarks>This API should be considered an advanced feature; inappropriate use can be harmful.</remarks>
    ValkeyResult Execute(string command, params object[] args);

    /// <inheritdoc cref="Execute(string, object[])"/>
    Task<ValkeyResult> ExecuteAsync(string command, params object[] args);

    /// <summary>
    /// Execute an arbitrary command against the server; this is primarily intended for
    /// executing modules, but may also be used to provide access to new features that lack
    /// a direct API.
    /// </summary>
    /// <param name="command">The command to run.</param>
    /// <param name="args">The arguments to pass for the command.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>A dynamic representation of the command's result.</returns>
    /// <remarks>This API should be considered an advanced feature; inappropriate use can be harmful.</remarks>
    ValkeyResult Execute(string command, ICollection<object> args, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="Execute(string, ICollection{object}, CommandFlags)"/>
    Task<ValkeyResult> ExecuteAsync(string command, ICollection<object> args, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// The INFO command returns information and statistics about the server in a format that is simple to parse by computers and easy to read by humans.
    /// </summary>
    /// <param name="section">The info section to get, if getting a specific one.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The entire raw <c>INFO</c> string.</returns>
    /// <remarks><seealso href="https://valkey.io/commands/info/"/></remarks>
    Task<string?> InfoRawAsync(ValkeyValue section = default, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// The INFO command returns information and statistics about the server in a format that is simple to parse by computers and easy to read by humans.
    /// </summary>
    /// <param name="section">The info section to get, if getting a specific one.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>A grouping of key/value pairs, grouped by their section header.</returns>
    /// <remarks><seealso href="https://valkey.io/commands/info/"/></remarks>
    Task<IGrouping<string, KeyValuePair<string, string>>[]> InfoAsync(ValkeyValue section = default, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="InfoRawAsync(ValkeyValue, CommandFlags)"/>
    string? InfoRaw(ValkeyValue section = default, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="InfoAsync(ValkeyValue, CommandFlags)"/>
    IGrouping<string, KeyValuePair<string, string>>[] Info(ValkeyValue section = default, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// This command is often used to test if a connection is still alive, or to measure latency.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/ping"/>
    /// <param name="flags">The command flags to use. Currently flags are ignored.</param>
    /// <returns>The observed latency.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// TimeSpan result = await client.PingAsync();
    /// </code>
    /// </example>
    /// </remarks>
    Task<TimeSpan> PingAsync(CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// This command is often used to test if a connection is still alive, or to measure latency.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/ping"/>
    /// <param name="message">The message to send.</param>
    /// <param name="flags">The command flags to use. Currently flags are ignored.</param>
    /// <returns>The observed latency.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// TimeSpan result = await client.PingAsync("ping!");
    /// </code>
    /// </example>
    /// </remarks>
    Task<TimeSpan> PingAsync(ValkeyValue message, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Return the same message passed in.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/echo"/>
    /// <param name="message">The message to echo.</param>
    /// <param name="flags">The command flags to use. Currently flags are ignored.</param>
    /// <returns>The provided message.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue result = await client.EchoAsync(key, value);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> EchoAsync(ValkeyValue message, CommandFlags flags = CommandFlags.None);
}
