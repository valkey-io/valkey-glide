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

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRemoveAsync(ValkeyKey, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRemoveAsync(ValkeyKey, ValkeyValue, CommandFlags)" /></returns>
    IBatch SortedSetRemove(ValkeyKey key, ValkeyValue member);

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRemoveAsync(ValkeyKey, ValkeyValue[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRemoveAsync(ValkeyKey, ValkeyValue[], CommandFlags)" /></returns>
    IBatch SortedSetRemove(ValkeyKey key, ValkeyValue[] members);

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetLengthAsync(ValkeyKey, double, double, Exclude, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetLengthAsync(ValkeyKey, double, double, Exclude, CommandFlags)" /></returns>
    IBatch SortedSetLength(ValkeyKey key, double min = double.NegativeInfinity, double max = double.PositiveInfinity, Exclude exclude = Exclude.None);

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetCardAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetCardAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch SortedSetCard(ValkeyKey key);

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetCountAsync(ValkeyKey, double, double, Exclude, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetCountAsync(ValkeyKey, double, double, Exclude, CommandFlags)" /></returns>
    IBatch SortedSetCount(ValkeyKey key, double min = double.NegativeInfinity, double max = double.PositiveInfinity, Exclude exclude = Exclude.None);

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByRankAsync(ValkeyKey, long, long, Order, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByRankAsync(ValkeyKey, long, long, Order, CommandFlags)" /></returns>
    IBatch SortedSetRangeByRank(ValkeyKey key, long start = 0, long stop = -1, Order order = Order.Ascending);

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByRankWithScoresAsync(ValkeyKey, long, long, Order, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByRankWithScoresAsync(ValkeyKey, long, long, Order, CommandFlags)" /></returns>
    IBatch SortedSetRangeByRankWithScores(ValkeyKey key, long start = 0, long stop = -1, Order order = Order.Ascending);

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByScoreAsync(ValkeyKey, double, double, Exclude, Order, long, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByScoreAsync(ValkeyKey, double, double, Exclude, Order, long, long, CommandFlags)" /></returns>
    IBatch SortedSetRangeByScore(ValkeyKey key, double start = double.NegativeInfinity, double stop = double.PositiveInfinity, Exclude exclude = Exclude.None, Order order = Order.Ascending, long skip = 0, long take = -1);

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByScoreWithScoresAsync(ValkeyKey, double, double, Exclude, Order, long, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByScoreWithScoresAsync(ValkeyKey, double, double, Exclude, Order, long, long, CommandFlags)" /></returns>
    IBatch SortedSetRangeByScoreWithScores(ValkeyKey key, double start = double.NegativeInfinity, double stop = double.PositiveInfinity, Exclude exclude = Exclude.None, Order order = Order.Ascending, long skip = 0, long take = -1);

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByValueAsync(ValkeyKey, ValkeyValue, ValkeyValue, Exclude, long, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByValueAsync(ValkeyKey, ValkeyValue, ValkeyValue, Exclude, long, long, CommandFlags)" /></returns>
    IBatch SortedSetRangeByValue(ValkeyKey key, ValkeyValue min, ValkeyValue max, Exclude exclude = Exclude.None, long skip = 0, long take = -1);

    /// <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByValueAsync(ValkeyKey, ValkeyValue, ValkeyValue, Exclude, Order, long, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="Commands.ISortedSetCommands.SortedSetRangeByValueAsync(ValkeyKey, ValkeyValue, ValkeyValue, Exclude, Order, long, long, CommandFlags)" /></returns>
    IBatch SortedSetRangeByValue(ValkeyKey key, ValkeyValue min = default, ValkeyValue max = default, Exclude exclude = Exclude.None, Order order = Order.Ascending, long skip = 0, long take = -1);
}
