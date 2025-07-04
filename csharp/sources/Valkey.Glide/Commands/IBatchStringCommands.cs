// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports batch commands for the "String Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=string">valkey.io</see>.
/// </summary>
public interface IBatchStringCommands
{
    /// <inheritdoc cref="IStringBaseCommands.Set(GlideString, GlideString)" path="/summary" />
    /// <inheritdoc cref="IStringBaseCommands.Set(GlideString, GlideString)" path="/param" />
    /// <returns>Command Response - <inheritdoc cref="IStringBaseCommands.Set(GlideString, GlideString)" /></returns>
    IBatchStringCommands Set(GlideString key, GlideString value);

    /// <inheritdoc cref="IStringBaseCommands.Get(GlideString)" path="/summary" />
    /// <inheritdoc cref="IStringBaseCommands.Get(GlideString)" path="/param" />
    /// <returns>Command Response - <inheritdoc cref="IStringBaseCommands.Get(GlideString)" /></returns>
    IBatchStringCommands Get(GlideString key);

    /// <inheritdoc cref="IStringBaseCommands.Strlen(GlideString)" path="/summary" />
    /// <inheritdoc cref="IStringBaseCommands.Strlen(GlideString)" path="/param" />
    /// <returns>Command Response - <inheritdoc cref="IStringBaseCommands.Strlen(GlideString)" /></returns>
    IBatchStringCommands Strlen(GlideString key);
}
