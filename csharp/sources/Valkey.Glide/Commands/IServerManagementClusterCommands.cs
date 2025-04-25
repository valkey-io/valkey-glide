// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Commands.Options.InfoOptions;

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "Server Management" group for a cluster client.<br />
/// See <see href="https://valkey.io/commands/?group=server">Server Management Commands</see>.
/// </summary>
public interface IServerManagementClusterCommands
{
    /// <summary>
    /// Get information and statistics about the server using <see cref="Section.DEFAULT" /> option.<br />
    /// The command will be routed to all primary nodes.<br />
    /// See <see href="https://valkey.io/commands/info/">valkey.io</see> for details.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// string response = await client.Info();
    /// response.Split().First(l => l.Contains("total_net_input_bytes"))
    /// </code>
    /// </example>
    /// </remarks>
    /// <returns>A <see langword="string" /> containing the information for the sections requested per cluster node.</returns>
    Task<Dictionary<string, string>> Info();

    /// <summary>
    /// Get information and statistics about the server.<br />
    /// Starting from server version 7, command supports multiple <see cref="Section" /> arguments.<br />
    /// The command will be routed to all primary nodes.<br />
    /// See <see href="https://valkey.io/commands/info/">valkey.io</see> for details.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// string response = await client.Info([ Section.STATS ]);
    /// response.Split().First(l => l.Contains("total_net_input_bytes"))
    /// </code>
    /// </example>
    /// </remarks>
    /// <inheritdoc cref="Info(Section[], Route)" path="/param" />
    /// <returns>A <see langword="string" /> containing the information for the sections requested per cluster node.</returns>
    Task<Dictionary<string, string>> Info(Section[] sections);

    /// <summary>
    /// Get information and statistics about the server using <see cref="Section.DEFAULT" /> option.<br />
    /// The command will be routed to all primary nodes.<br />
    /// See <see href="https://valkey.io/commands/info/">valkey.io</see> for details.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// Dictionary&lt;string, string&gt; response = (await client.Info(Route.AllNodes)).MultiValue;
    /// response.Select(pair =>
    ///         (Node: pair.Key, Value: pair.Value.Split().First(l => l.Contains("total_net_input_bytes")))
    ///     ).ToDictionary(p => p.Key, p => p.Value)
    /// </code>
    /// </example>
    /// </remarks>
    /// <inheritdoc cref="Info(Section[], Route)" path="/param" />
    /// <returns>
    /// <inheritdoc cref="Info(Section[], Route)" />
    /// </returns>
    Task<ClusterValue<string>> Info(Route route);

    /// <summary>
    /// Get information and statistics about the server.<br />
    /// Starting from server version 7, command supports multiple <see cref="Section" /> arguments.<br />
    /// The command will be routed to all primary nodes.<br />
    /// See <see href="https://valkey.io/commands/info/">valkey.io</see> for details.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// Dictionary&lt;string, string&gt; response = (await client.Info([ Section.STATS ], Route.AllNodes)).MultiValue;
    /// response.Select(pair =>
    ///         (Node: pair.Key, Value: pair.Value.Split().First(l => l.Contains("total_net_input_bytes")))
    ///     ).ToDictionary(p => p.Key, p => p.Value)
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="sections">A list of <see cref="Section" /> values specifying which sections of information to
    /// retrieve. When no parameter is provided, the <see cref="Section.DEFAULT" /> option is assumed.</param>
    /// <param name="route">Specifies the routing configuration for the command. The client will route the
    /// command to the nodes defined by <c>route</c>.</param>
    /// <returns>
    /// A <see cref="ClusterValue{T}" /> containing the information for the sections requested.<br />
    /// When specifying a <paramref name="route" /> other than a single node, it returns a multi-value <see cref="ClusterValue{T}" />
    /// with a <c>Dictionary&lt;string, string&gt;</c> with each address as the key and its corresponding
    /// value is the information for the node. For a single node route it returns a <see cref="ClusterValue{T}" /> with a single value.
    /// </returns>
    Task<ClusterValue<string>> Info(Section[] sections, Route route);
}
