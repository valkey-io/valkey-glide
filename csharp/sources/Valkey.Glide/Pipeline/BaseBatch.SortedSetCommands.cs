// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.Request;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Sorted Set commands for BaseBatch.
/// </summary>
public abstract partial class BaseBatch<T>
{
    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, ValkeyValue, double, SortedSetWhen)" />
    public T SortedSetAdd(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always) => AddCmd(SortedSetAddAsync(key, member, score, when));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetAdd(ValkeyKey, SortedSetEntry[], SortedSetWhen)" />
    public T SortedSetAdd(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when = SortedSetWhen.Always) => AddCmd(SortedSetAddAsync(key, values, when));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetRemove(ValkeyKey, ValkeyValue)" />
    public T SortedSetRemove(ValkeyKey key, ValkeyValue member) => AddCmd(SortedSetRemoveAsync(key, member));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetRemove(ValkeyKey, ValkeyValue[])" />
    public T SortedSetRemove(ValkeyKey key, ValkeyValue[] members) => AddCmd(SortedSetRemoveAsync(key, members));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetLength(ValkeyKey, double, double, Exclude)" />
    public T SortedSetLength(ValkeyKey key, double min = double.NegativeInfinity, double max = double.PositiveInfinity, Exclude exclude = Exclude.None)
    {
        // If both min and max are infinity (default values), use ZCARD
        if (double.IsNegativeInfinity(min) && double.IsPositiveInfinity(max))
        {
            return SortedSetCard(key);
        }

        // Otherwise use ZCOUNT with the specified range
        return SortedSetCount(key, min, max, exclude);
    }

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetCard(ValkeyKey)" />
    public T SortedSetCard(ValkeyKey key) => AddCmd(SortedSetCardAsync(key));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetCount(ValkeyKey, double, double, Exclude)" />
    public T SortedSetCount(ValkeyKey key, double min = double.NegativeInfinity, double max = double.PositiveInfinity, Exclude exclude = Exclude.None) => AddCmd(SortedSetCountAsync(key, min, max, exclude));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetRangeByRank(ValkeyKey, long, long, Order)" />
    public T SortedSetRangeByRank(ValkeyKey key, long start = 0, long stop = -1, Order order = Order.Ascending) => AddCmd(SortedSetRangeByRankAsync(key, start, stop, order));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetRangeByRankWithScores(ValkeyKey, long, long, Order)" />
    public T SortedSetRangeByRankWithScores(ValkeyKey key, long start = 0, long stop = -1, Order order = Order.Ascending) => AddCmd(SortedSetRangeByRankWithScoresAsync(key, start, stop, order));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetRangeByScore(ValkeyKey, double, double, Exclude, Order, long, long)" />
    public T SortedSetRangeByScore(ValkeyKey key, double start = double.NegativeInfinity, double stop = double.PositiveInfinity, Exclude exclude = Exclude.None, Order order = Order.Ascending, long skip = 0, long take = -1) => AddCmd(SortedSetRangeByScoreAsync(key, start, stop, exclude, order, skip, take));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetRangeByScoreWithScores(ValkeyKey, double, double, Exclude, Order, long, long)" />
    public T SortedSetRangeByScoreWithScores(ValkeyKey key, double start = double.NegativeInfinity, double stop = double.PositiveInfinity, Exclude exclude = Exclude.None, Order order = Order.Ascending, long skip = 0, long take = -1) => AddCmd(SortedSetRangeByScoreWithScoresAsync(key, start, stop, exclude, order, skip, take));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetRangeByValue(ValkeyKey, ValkeyValue, ValkeyValue, Exclude, long, long)" />
    public T SortedSetRangeByValue(ValkeyKey key, ValkeyValue min, ValkeyValue max, Exclude exclude = Exclude.None, long skip = 0, long take = -1) => AddCmd(SortedSetRangeByValueAsync(key, min, max, exclude, skip, take));

    /// <inheritdoc cref="IBatchSortedSetCommands.SortedSetRangeByValue(ValkeyKey, ValkeyValue, ValkeyValue, Exclude, Order, long, long)" />
    public T SortedSetRangeByValue(ValkeyKey key, ValkeyValue min = default, ValkeyValue max = default, Exclude exclude = Exclude.None, Order order = Order.Ascending, long skip = 0, long take = -1) => AddCmd(SortedSetRangeByValueAsync(key, min, max, exclude, order, skip, take));

    // Explicit interface implementations for IBatchSortedSetCommands
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when) => SortedSetAdd(key, member, score, when);
    IBatch IBatchSortedSetCommands.SortedSetAdd(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when) => SortedSetAdd(key, values, when);
    IBatch IBatchSortedSetCommands.SortedSetRemove(ValkeyKey key, ValkeyValue member) => SortedSetRemove(key, member);
    IBatch IBatchSortedSetCommands.SortedSetRemove(ValkeyKey key, ValkeyValue[] members) => SortedSetRemove(key, members);
    IBatch IBatchSortedSetCommands.SortedSetLength(ValkeyKey key, double min, double max, Exclude exclude) => SortedSetLength(key, min, max, exclude);
    IBatch IBatchSortedSetCommands.SortedSetCard(ValkeyKey key) => SortedSetCard(key);
    IBatch IBatchSortedSetCommands.SortedSetCount(ValkeyKey key, double min, double max, Exclude exclude) => SortedSetCount(key, min, max, exclude);
    IBatch IBatchSortedSetCommands.SortedSetRangeByRank(ValkeyKey key, long start, long stop, Order order) => SortedSetRangeByRank(key, start, stop, order);
    IBatch IBatchSortedSetCommands.SortedSetRangeByRankWithScores(ValkeyKey key, long start, long stop, Order order) => SortedSetRangeByRankWithScores(key, start, stop, order);
    IBatch IBatchSortedSetCommands.SortedSetRangeByScore(ValkeyKey key, double start, double stop, Exclude exclude, Order order, long skip, long take) => SortedSetRangeByScore(key, start, stop, exclude, order, skip, take);
    IBatch IBatchSortedSetCommands.SortedSetRangeByScoreWithScores(ValkeyKey key, double start, double stop, Exclude exclude, Order order, long skip, long take) => SortedSetRangeByScoreWithScores(key, start, stop, exclude, order, skip, take);
    IBatch IBatchSortedSetCommands.SortedSetRangeByValue(ValkeyKey key, ValkeyValue min, ValkeyValue max, Exclude exclude, long skip, long take) => SortedSetRangeByValue(key, min, max, exclude, skip, take);
    IBatch IBatchSortedSetCommands.SortedSetRangeByValue(ValkeyKey key, ValkeyValue min, ValkeyValue max, Exclude exclude, Order order, long skip, long take) => SortedSetRangeByValue(key, min, max, exclude, order, skip, take);
}
