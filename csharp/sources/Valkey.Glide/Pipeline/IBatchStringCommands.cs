// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Supports commands for the "String Commands" group for batch requests.
/// </summary>
internal interface IBatchStringCommands
{
    /// <inheritdoc cref="Commands.IStringBaseCommands.StringGetAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringGetAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch StringGetAsync(ValkeyKey key);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringGetAsync(ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringGetAsync(ValkeyKey[], CommandFlags)" /></returns>
    IBatch StringGetAsync(ValkeyKey[] keys);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringSetAsync(ValkeyKey, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringSetAsync(ValkeyKey, ValkeyValue, CommandFlags)" /></returns>
    IBatch StringSetAsync(ValkeyKey key, ValkeyValue value);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringSetAsync(KeyValuePair{ValkeyKey, ValkeyValue}[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringSetAsync(KeyValuePair{ValkeyKey, ValkeyValue}[], CommandFlags)" /></returns>
    IBatch StringSetAsync(KeyValuePair<ValkeyKey, ValkeyValue>[] values);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringGetRangeAsync(ValkeyKey, long, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringGetRangeAsync(ValkeyKey, long, long, CommandFlags)" /></returns>
    IBatch StringGetRangeAsync(ValkeyKey key, long start, long end);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringSetRangeAsync(ValkeyKey, long, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringSetRangeAsync(ValkeyKey, long, ValkeyValue, CommandFlags)" /></returns>
    IBatch StringSetRangeAsync(ValkeyKey key, long offset, ValkeyValue value);

    /// <inheritdoc cref="Commands.IStringBaseCommands.StringLengthAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringBaseCommands.StringLengthAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch StringLengthAsync(ValkeyKey key);
}
