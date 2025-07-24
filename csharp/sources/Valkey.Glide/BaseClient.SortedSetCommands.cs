// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : ISortedSetCommands
{
    public async Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, CommandFlags flags)
        => await SortedSetAddAsync(key, member, score, SortedSetWhen.Always, flags);

    public async Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, When when, CommandFlags flags = CommandFlags.None)
        => await SortedSetAddAsync(key, member, score, SortedSetWhenExtensions.Parse(when), flags);

    public async Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetAddAsync(key, member, score, when, flags));

    public async Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, CommandFlags flags)
        => await SortedSetAddAsync(key, values, SortedSetWhen.Always, flags);

    public async Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, When when, CommandFlags flags = CommandFlags.None)
        => await SortedSetAddAsync(key, values, SortedSetWhenExtensions.Parse(when), flags);

    public async Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetAddAsync(key, values, when, flags));

    public async Task<bool> SortedSetRemoveAsync(ValkeyKey key, ValkeyValue member, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetRemoveAsync(key, member, flags));

    public async Task<long> SortedSetRemoveAsync(ValkeyKey key, ValkeyValue[] members, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetRemoveAsync(key, members, flags));

    public async Task<long> SortedSetLengthAsync(ValkeyKey key, double min = double.NegativeInfinity, double max = double.PositiveInfinity, Exclude exclude = Exclude.None, CommandFlags flags = CommandFlags.None)
    {
        // If both min and max are infinity (default values), use ZCARD
        if (double.IsNegativeInfinity(min) && double.IsPositiveInfinity(max))
        {
            return await SortedSetCardAsync(key, flags);
        }

        // Otherwise use ZCOUNT with the specified range
        return await SortedSetCountAsync(key, min, max, exclude, flags);
    }

    public async Task<long> SortedSetCardAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetCardAsync(key, flags));

    public async Task<long> SortedSetCountAsync(ValkeyKey key, double min = double.NegativeInfinity, double max = double.PositiveInfinity, Exclude exclude = Exclude.None, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetCountAsync(key, min, max, exclude, flags));

    public async Task<ValkeyValue[]> SortedSetRangeByRankAsync(ValkeyKey key, long start = 0, long stop = -1, Order order = Order.Ascending, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetRangeByRankAsync(key, start, stop, order, flags));

    public async Task<SortedSetEntry[]> SortedSetRangeByRankWithScoresAsync(ValkeyKey key, long start = 0, long stop = -1, Order order = Order.Ascending, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetRangeByRankWithScoresAsync(key, start, stop, order, flags));

    public async Task<ValkeyValue[]> SortedSetRangeByScoreAsync(ValkeyKey key, double start = double.NegativeInfinity, double stop = double.PositiveInfinity, Exclude exclude = Exclude.None, Order order = Order.Ascending, long skip = 0, long take = -1, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetRangeByScoreAsync(key, start, stop, exclude, order, skip, take, flags));

    public async Task<SortedSetEntry[]> SortedSetRangeByScoreWithScoresAsync(ValkeyKey key, double start = double.NegativeInfinity, double stop = double.PositiveInfinity, Exclude exclude = Exclude.None, Order order = Order.Ascending, long skip = 0, long take = -1, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetRangeByScoreWithScoresAsync(key, start, stop, exclude, order, skip, take, flags));

    public async Task<ValkeyValue[]> SortedSetRangeByValueAsync(ValkeyKey key, ValkeyValue min, ValkeyValue max, Exclude exclude, long skip, long take, CommandFlags flags = CommandFlags.None)
        => await SortedSetRangeByValueAsync(key, min, max, exclude, Order.Ascending, skip, take, flags);

    public async Task<ValkeyValue[]> SortedSetRangeByValueAsync(
        ValkeyKey key,
        ValkeyValue min = default,
        ValkeyValue max = default,
        Exclude exclude = Exclude.None,
        Order order = Order.Ascending,
        long skip = 0,
        long take = -1,
        CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetRangeByValueAsync(key, min, max, exclude, order, skip, take, flags));
}
