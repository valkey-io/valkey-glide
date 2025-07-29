// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;

namespace Valkey.Glide;

/// <summary>
/// Describes functionality that is common to both standalone and cluster servers.<br />
/// See also <see cref="GlideClient" /> and <see cref="GlideClusterClient" />.
/// </summary>
public interface IDatabaseAsync : IConnectionManagementCommands, IGenericCommands, IGenericBaseCommands, IHashCommands, IListCommands, IServerManagementCommands, ISetCommands, ISortedSetCommands, IStringCommands
{
    /// <summary>
    /// Execute an arbitrary command against the server; this is primarily intended for executing modules,
    /// but may also be used to provide access to new features that lack a direct API.
    /// </summary>
    /// <param name="command">The command to run.</param>
    /// <param name="args">The arguments to pass for the command.</param>
    /// <returns>A dynamic representation of the command's result.</returns>
    /// <remarks>This API should be considered an advanced feature; inappropriate use can be harmful.</remarks>
    Task<ValkeyResult> ExecuteAsync(string command, params object[] args);

    /// <summary>
    /// Execute an arbitrary command against the server; this is primarily intended for executing modules,
    /// but may also be used to provide access to new features that lack a direct API.
    /// </summary>
    /// <param name="command">The command to run.</param>
    /// <param name="args">The arguments to pass for the command.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>A dynamic representation of the command's result.</returns>
    /// <remarks>This API should be considered an advanced feature; inappropriate use can be harmful.</remarks>
    Task<ValkeyResult> ExecuteAsync(string command, ICollection<object>? args, CommandFlags flags = CommandFlags.None);
}
