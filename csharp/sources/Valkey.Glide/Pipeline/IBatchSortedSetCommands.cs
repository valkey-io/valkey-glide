// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Supports commands for the "Sorted Set Commands" group for batch operations.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=sorted-set#sorted-set">valkey.io</see>.
/// </summary>
internal interface IBatchSortedSetCommands
{
    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetAddAsync(ValkeyKey, ValkeyValue, double, SortedSetWhen, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetAddAsync(ValkeyKey, ValkeyValue, double, SortedSetWhen, CommandFlags)" /></returns>
    IBatch SortedSetAdd(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always);

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetAddAsync(ValkeyKey, SortedSetEntry[], SortedSetWhen, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetAddAsync(ValkeyKey, SortedSetEntry[], SortedSetWhen, CommandFlags)" /></returns>
    IBatch SortedSetAdd(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when = SortedSetWhen.Always);
}
