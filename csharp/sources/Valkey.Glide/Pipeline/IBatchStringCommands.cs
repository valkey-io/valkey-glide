// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Supports commands for the "String Commands" group for batch requests.
/// </summary>
internal interface IBatchStringCommands
{
    /// <inheritdoc cref="Commands.IStringCommands.StringGetAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringGetAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch StringGet(ValkeyKey key);

    /// <inheritdoc cref="Commands.IStringCommands.StringGetAsync(ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringGetAsync(ValkeyKey[], CommandFlags)" /></returns>
    IBatch StringGet(ValkeyKey[] keys);

    /// <inheritdoc cref="Commands.IStringCommands.StringSetAsync(ValkeyKey, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringSetAsync(ValkeyKey, ValkeyValue, CommandFlags)" /></returns>
    IBatch StringSet(ValkeyKey key, ValkeyValue value);

    /// <inheritdoc cref="Commands.IStringCommands.StringSetAsync(KeyValuePair{ValkeyKey, ValkeyValue}[], When, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringSetAsync(KeyValuePair{ValkeyKey, ValkeyValue}[], When, CommandFlags)" /></returns>
    IBatch StringSet(KeyValuePair<ValkeyKey, ValkeyValue>[] values);

    /// <inheritdoc cref="Commands.IStringCommands.StringGetRangeAsync(ValkeyKey, long, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringGetRangeAsync(ValkeyKey, long, long, CommandFlags)" /></returns>
    IBatch StringGetRange(ValkeyKey key, long start, long end);

    /// <inheritdoc cref="Commands.IStringCommands.StringSetRangeAsync(ValkeyKey, long, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringSetRangeAsync(ValkeyKey, long, ValkeyValue, CommandFlags)" /></returns>
    IBatch StringSetRange(ValkeyKey key, long offset, ValkeyValue value);

    /// <inheritdoc cref="Commands.IStringCommands.StringLengthAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringLengthAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch StringLength(ValkeyKey key);

    /// <inheritdoc cref="Commands.IStringCommands.StringAppendAsync(ValkeyKey, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringAppendAsync(ValkeyKey, ValkeyValue, CommandFlags)" /></returns>
    IBatch StringAppend(ValkeyKey key, ValkeyValue value);

    /// <inheritdoc cref="Commands.IStringCommands.StringDecrementAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringDecrementAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch StringDecrement(ValkeyKey key);

    /// <inheritdoc cref="Commands.IStringCommands.StringDecrementAsync(ValkeyKey, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringDecrementAsync(ValkeyKey, long, CommandFlags)" /></returns>
    IBatch StringDecrement(ValkeyKey key, long decrement);

    /// <inheritdoc cref="Commands.IStringCommands.StringIncrementAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringIncrementAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch StringIncrement(ValkeyKey key);

    /// <inheritdoc cref="Commands.IStringCommands.StringIncrementAsync(ValkeyKey, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringIncrementAsync(ValkeyKey, long, CommandFlags)" /></returns>
    IBatch StringIncrement(ValkeyKey key, long increment);

    /// <inheritdoc cref="Commands.IStringCommands.StringIncrementAsync(ValkeyKey, double, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringIncrementAsync(ValkeyKey, double, CommandFlags)" /></returns>
    IBatch StringIncrement(ValkeyKey key, double increment);

    /// <inheritdoc cref="Commands.IStringCommands.StringGetDeleteAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringGetDeleteAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch StringGetDelete(ValkeyKey key);

    /// <inheritdoc cref="Commands.IStringCommands.StringGetSetExpiryAsync(ValkeyKey, TimeSpan?, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringGetSetExpiryAsync(ValkeyKey, TimeSpan?, CommandFlags)" /></returns>
    IBatch StringGetSetExpiry(ValkeyKey key, TimeSpan? expiry);

    /// <inheritdoc cref="Commands.IStringCommands.StringGetSetExpiryAsync(ValkeyKey, DateTime, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringGetSetExpiryAsync(ValkeyKey, DateTime, CommandFlags)" /></returns>
    IBatch StringGetSetExpiry(ValkeyKey key, DateTime expiry);

    /// <inheritdoc cref="Commands.IStringCommands.StringLongestCommonSubsequenceAsync(ValkeyKey, ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringLongestCommonSubsequenceAsync(ValkeyKey, ValkeyKey, CommandFlags)" /></returns>
    IBatch StringLongestCommonSubsequence(ValkeyKey first, ValkeyKey second);

    /// <inheritdoc cref="Commands.IStringCommands.StringLongestCommonSubsequenceLengthAsync(ValkeyKey, ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringLongestCommonSubsequenceLengthAsync(ValkeyKey, ValkeyKey, CommandFlags)" /></returns>
    IBatch StringLongestCommonSubsequenceLength(ValkeyKey first, ValkeyKey second);

    /// <inheritdoc cref="Commands.IStringCommands.StringLongestCommonSubsequenceWithMatchesAsync(ValkeyKey, ValkeyKey, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.IStringCommands.StringLongestCommonSubsequenceWithMatchesAsync(ValkeyKey, ValkeyKey, long, CommandFlags)" /></returns>
    IBatch StringLongestCommonSubsequenceWithMatches(ValkeyKey first, ValkeyKey second, long minLength = 0);
}
