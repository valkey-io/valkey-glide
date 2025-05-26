// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Commands.Options.InfoOptions;

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "Server Management" group for a standalone client.<br />
/// See <see href="https://valkey.io/commands/?group=server">Server Management Commands</see>.
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
}
