// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Supports batch commands for the "String Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=string">valkey.io</see>.
/// </summary>
internal interface IBatchStringCommands
{
    /// <inheritdoc cref="Commands.IStringBaseCommands.Get(GlideString)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.Get(GlideString)" /></returns>
    IBatch Get(GlideString key);

    /// <inheritdoc cref="Commands.IStringBaseCommands.MGet(GlideString[])" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.MGet(GlideString[])" /></returns>
    IBatch MGet(GlideString[] keys);

    /// <inheritdoc cref="Commands.IStringBaseCommands.Set(GlideString, GlideString)" path="/summary" />
    /// <inheritdoc cref="Commands.IStringBaseCommands.Set(GlideString, GlideString)" path="/param" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.Set(GlideString, GlideString)" /></returns>
    IBatch Set(GlideString key, GlideString value);

    /// <inheritdoc cref="Commands.IStringBaseCommands.MSet(Dictionary{GlideString, GlideString})" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.MSet(Dictionary{GlideString, GlideString})" /></returns>
    IBatch MSet(Dictionary<GlideString, GlideString> keyValueMap);

    /// <inheritdoc cref="Commands.IStringBaseCommands.GetRange(GlideString, long, long)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.GetRange(GlideString, long, long)" /></returns>
    IBatch GetRange(GlideString key, long start, long end);

    /// <inheritdoc cref="Commands.IStringBaseCommands.SetRange(GlideString, long, GlideString)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.SetRange(GlideString, long, GlideString)" /></returns>
    IBatch SetRange(GlideString key, long offset, GlideString value);

    /// <inheritdoc cref="Commands.IStringBaseCommands.Strlen(GlideString)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.Strlen(GlideString)" /></returns>
    IBatch Strlen(GlideString key);
}
