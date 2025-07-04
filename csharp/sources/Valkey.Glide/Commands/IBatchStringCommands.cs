// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports batch commands for the "String Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=string">valkey.io</see>.
/// </summary>
internal interface IBatchStringCommands
{
    /// <inheritdoc cref="IStringBaseCommands.Get(GlideString)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IStringBaseCommands.Get(GlideString)" /></returns>
    IBatch Get(GlideString key);

    /// <inheritdoc cref="IStringBaseCommands.Set(GlideString, GlideString)" path="/summary" />
    /// <inheritdoc cref="IStringBaseCommands.Set(GlideString, GlideString)" path="/param" />
    /// <returns>Command Response - <inheritdoc cref="IStringBaseCommands.Set(GlideString, GlideString)" /></returns>
    IBatch Set(GlideString key, GlideString value);

    /// <inheritdoc cref="IStringBaseCommands.Strlen(GlideString)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IStringBaseCommands.Strlen(GlideString)" /></returns>
    IBatch Strlen(GlideString key);
}
