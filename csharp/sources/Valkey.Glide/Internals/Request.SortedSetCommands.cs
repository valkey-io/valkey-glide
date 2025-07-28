// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;

using static Valkey.Glide.Commands.Constants.Constants;
using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    private static void AddSortedSetWhenOptions(List<GlideString> args, SortedSetWhen when)
    {
        // Add conditional options
        if (when.HasFlag(SortedSetWhen.Exists))
        {
            args.Add("XX");
        }
        else if (when.HasFlag(SortedSetWhen.NotExists))
        {
            args.Add("NX");
        }

        if (when.HasFlag(SortedSetWhen.GreaterThan))
        {
            args.Add("GT");
        }
        else if (when.HasFlag(SortedSetWhen.LessThan))
        {
            args.Add("LT");
        }
    }

    public static Cmd<long, bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always)
    {
        List<GlideString> args = [key.ToGlideString()];
        AddSortedSetWhenOptions(args, when);

        // Add score and member
        args.Add(score.ToGlideString());
        args.Add(member.ToGlideString());

        return new(RequestType.ZAdd, [.. args], false, response => response == 1);
    }

    public static Cmd<long, long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when = SortedSetWhen.Always)
    {
        List<GlideString> args = [key.ToGlideString()];
        AddSortedSetWhenOptions(args, when);

        // Add score-member pairs
        foreach (SortedSetEntry entry in values)
        {
            args.Add(entry.Score.ToGlideString());
            args.Add(entry.Element.ToGlideString());
        }

        return Simple<long>(RequestType.ZAdd, [.. args]);
    }

    public static Cmd<long, bool> SortedSetRemoveAsync(ValkeyKey key, ValkeyValue member)
        => Boolean<long>(RequestType.ZRem, [key.ToGlideString(), member.ToGlideString()]);

    public static Cmd<long, long> SortedSetRemoveAsync(ValkeyKey key, ValkeyValue[] members)
    {
        List<GlideString> args = [key.ToGlideString()];
        args.AddRange(members.Select(member => member.ToGlideString()));

        return Simple<long>(RequestType.ZRem, [.. args]);
    }

    public static Cmd<long, long> SortedSetCardAsync(ValkeyKey key)
        => Simple<long>(RequestType.ZCard, [key.ToGlideString()]);

    public static Cmd<long, long> SortedSetCountAsync(ValkeyKey key, double min = double.NegativeInfinity, double max = double.PositiveInfinity, Exclude exclude = Exclude.None)
    {
        // Create score boundaries based on exclude flags
        ScoreBoundary minBoundary = exclude.HasFlag(Exclude.Start)
            ? ScoreBoundary.Exclusive(min)
            : ScoreBoundary.Inclusive(min);

        ScoreBoundary maxBoundary = exclude.HasFlag(Exclude.Stop)
            ? ScoreBoundary.Exclusive(max)
            : ScoreBoundary.Inclusive(max);

        ZCountRange range = new(minBoundary, maxBoundary);
        string[] rangeArgs = range.ToArgs();

        List<GlideString> args = [key.ToGlideString()];
        args.AddRange(rangeArgs.Select(arg => arg.ToGlideString()));

        return Simple<long>(RequestType.ZCount, [.. args]);
    }

    public static Cmd<object[], ValkeyValue[]> SortedSetRangeByRankAsync(ValkeyKey key, long start = 0, long stop = -1, Order order = Order.Ascending)
    {
        RangeByIndex query = new(start, stop);
        if (order == Order.Descending)
        {
            _ = query.SetReverse();
        }

        string[] queryArgs = query.ToArgs();
        List<GlideString> args = [key.ToGlideString()];
        args.AddRange(queryArgs.Select(arg => arg.ToGlideString()));

        return new(RequestType.ZRange, [.. args], false, array => [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<Dictionary<GlideString, object>, SortedSetEntry[]> SortedSetRangeByRankWithScoresAsync(ValkeyKey key, long start = 0, long stop = -1, Order order = Order.Ascending)
    {
        RangeByIndex query = new(start, stop);
        if (order == Order.Descending)
        {
            _ = query.SetReverse();
        }

        string[] queryArgs = query.ToArgs();
        List<GlideString> args = [key.ToGlideString()];
        args.AddRange(queryArgs.Select(arg => arg.ToGlideString()));
        args.Add(WithScoresKeyword);

        return new(RequestType.ZRange, [.. args], false, dict =>
        {
            List<SortedSetEntry> entries = [];
            foreach (KeyValuePair<GlideString, object> kvp in dict)
            {
                ValkeyValue element = (ValkeyValue)kvp.Key;
                double score = Convert.ToDouble(kvp.Value);
                entries.Add(new SortedSetEntry(element, score));
            }

            // Sort by score, then by element for consistent ordering
            IOrderedEnumerable<SortedSetEntry> sortedEntries = order == Order.Ascending
                ? entries.OrderBy(e => e.Score).ThenBy(e => e.Element.ToString())
                : entries.OrderByDescending(e => e.Score).ThenByDescending(e => e.Element.ToString());
            return [.. sortedEntries];
        });
    }

    public static Cmd<object[], ValkeyValue[]> SortedSetRangeByScoreAsync(ValkeyKey key, double start = double.NegativeInfinity, double stop = double.PositiveInfinity, Exclude exclude = Exclude.None, Order order = Order.Ascending, long skip = 0, long take = -1)
    {
        // Create score boundaries based on exclude flags
        ScoreBoundary startBoundary = exclude.HasFlag(Exclude.Start)
            ? ScoreBoundary.Exclusive(start)
            : ScoreBoundary.Inclusive(start);

        ScoreBoundary stopBoundary = exclude.HasFlag(Exclude.Stop)
            ? ScoreBoundary.Exclusive(stop)
            : ScoreBoundary.Inclusive(stop);

        RangeByScore query = new(startBoundary, stopBoundary);
        if (order == Order.Descending)
        {
            _ = query.SetReverse();
        }

        if (take != -1)
        {
            _ = query.SetLimit(skip, take);
        }

        string[] queryArgs = query.ToArgs();
        List<GlideString> args = [key.ToGlideString()];
        args.AddRange(queryArgs.Select(arg => arg.ToGlideString()));

        return new(RequestType.ZRange, [.. args], false, array => [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<Dictionary<GlideString, object>, SortedSetEntry[]> SortedSetRangeByScoreWithScoresAsync(ValkeyKey key, double start = double.NegativeInfinity, double stop = double.PositiveInfinity, Exclude exclude = Exclude.None, Order order = Order.Ascending, long skip = 0, long take = -1)
    {
        // Create score boundaries based on exclude flags
        ScoreBoundary startBoundary = exclude.HasFlag(Exclude.Start)
            ? ScoreBoundary.Exclusive(start)
            : ScoreBoundary.Inclusive(start);

        ScoreBoundary stopBoundary = exclude.HasFlag(Exclude.Stop)
            ? ScoreBoundary.Exclusive(stop)
            : ScoreBoundary.Inclusive(stop);

        RangeByScore query = new(startBoundary, stopBoundary);
        if (order == Order.Descending)
        {
            _ = query.SetReverse();
        }

        if (take != -1)
        {
            _ = query.SetLimit(skip, take);
        }

        string[] queryArgs = query.ToArgs();
        List<GlideString> args = [key.ToGlideString()];
        args.AddRange(queryArgs.Select(arg => arg.ToGlideString()));
        args.Add(WithScoresKeyword);

        return new(RequestType.ZRange, [.. args], false, dict =>
        {
            List<SortedSetEntry> entries = [];
            foreach (KeyValuePair<GlideString, object> kvp in dict)
            {
                ValkeyValue element = (ValkeyValue)kvp.Key;
                double score = Convert.ToDouble(kvp.Value);
                entries.Add(new SortedSetEntry(element, score));
            }

            // Sort by score, then by element for consistent ordering
            IOrderedEnumerable<SortedSetEntry> sortedEntries = order == Order.Ascending
                ? entries.OrderBy(e => e.Score).ThenBy(e => e.Element.ToString())
                : entries.OrderByDescending(e => e.Score).ThenByDescending(e => e.Element.ToString());
            return [.. sortedEntries];
        });
    }

    public static Cmd<object[], ValkeyValue[]> SortedSetRangeByValueAsync(ValkeyKey key, ValkeyValue min, ValkeyValue max, Exclude exclude = Exclude.None, long skip = 0, long take = -1)
    {
        // Create lexicographical boundaries based on exclude flags
        LexBoundary minBoundary = exclude.HasFlag(Exclude.Start)
            ? LexBoundary.Exclusive(min)
            : LexBoundary.Inclusive(min);

        LexBoundary maxBoundary = exclude.HasFlag(Exclude.Stop)
            ? LexBoundary.Exclusive(max)
            : LexBoundary.Inclusive(max);

        RangeByLex query = new(minBoundary, maxBoundary);

        if (take != -1)
        {
            _ = query.SetLimit(skip, take);
        }

        string[] queryArgs = query.ToArgs();
        List<GlideString> args = [key.ToGlideString()];
        args.AddRange(queryArgs.Select(arg => arg.ToGlideString()));

        return new(RequestType.ZRange, [.. args], false, array => [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<object[], ValkeyValue[]> SortedSetRangeByValueAsync(ValkeyKey key, ValkeyValue min = default, ValkeyValue max = default, Exclude exclude = Exclude.None, Order order = Order.Ascending, long skip = 0, long take = -1)
    {
        // Handle default values for min and max
        ValkeyValue actualMin = min.IsNull ? NegativeInfinity : min;
        ValkeyValue actualMax = max.IsNull ? PositiveInfinity : max;

        // Create lexicographical boundaries based on exclude flags
        // Handle double infinity values and default infinity symbols by converting them to lexicographical infinity symbols
        string minStr = actualMin.ToString();
        LexBoundary minBoundary = minStr switch
        {
            NegativeInfinityScore or NegativeInfinity => LexBoundary.NegativeInfinity(),
            PositiveInfinityScore or PositiveInfinity => LexBoundary.PositiveInfinity(),
            _ => exclude.HasFlag(Exclude.Start) ? LexBoundary.Exclusive(actualMin) : LexBoundary.Inclusive(actualMin)
        };

        string maxStr = actualMax.ToString();
        LexBoundary maxBoundary = maxStr switch
        {
            NegativeInfinityScore or NegativeInfinity => LexBoundary.NegativeInfinity(),
            PositiveInfinityScore or PositiveInfinity => LexBoundary.PositiveInfinity(),
            _ => exclude.HasFlag(Exclude.Stop) ? LexBoundary.Exclusive(actualMax) : LexBoundary.Inclusive(actualMax)
        };

        RangeByLex query = new(minBoundary, maxBoundary);
        if (order == Order.Descending)
        {
            _ = query.SetReverse();
        }

        if (take != -1)
        {
            _ = query.SetLimit(skip, take);
        }

        string[] queryArgs = query.ToArgs();
        List<GlideString> args = [key.ToGlideString()];
        args.AddRange(queryArgs.Select(arg => arg.ToGlideString()));

        return new(RequestType.ZRange, [.. args], false, array => [.. array.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }
}
