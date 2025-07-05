// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Supports commands for the "String Commands" group for batch requests.
/// </summary>
internal interface IBatchStringCommands
{
    /// <inheritdoc cref="Commands.IStringBaseCommands.StringGet(GlideString)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringGet(GlideString)" /></returns>
    IBatch StringGet(GlideString key);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringGet(GlideString[])" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringGet(GlideString[])" /></returns>
    IBatch StringGet(GlideString[] keys);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringSet(GlideString, GlideString)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringSet(GlideString, GlideString)" /></returns>
    IBatch StringSet(GlideString key, GlideString value);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringSet(KeyValuePair{GlideString, GlideString}[])" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringSet(KeyValuePair{GlideString, GlideString}[])" /></returns>
    IBatch StringSet(KeyValuePair<GlideString, GlideString>[] values);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringGetRange(GlideString, long, long)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringGetRange(GlideString, long, long)" /></returns>
    IBatch StringGetRange(GlideString key, long start, long end);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringSetRange(GlideString, long, GlideString)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringSetRange(GlideString, long, GlideString)" /></returns>
    IBatch StringSetRange(GlideString key, long offset, GlideString value);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringLength(GlideString)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringLength(GlideString)" /></returns>
    IBatch StringLength(GlideString key);
}
