// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;
using Valkey.Glide.Internals;

using Xunit;

namespace Valkey.Glide.UnitTests;

public class SortedSetCommandTests
{
    [Fact]
    public void ValidateSortedSetCommandArgs()
    {
        Assert.Multiple(
            // SortedSetAdd - Single Member
            () => Assert.Equal(["ZADD", "key", "10.5", "member"], Request.SortedSetAddAsync("key", "member", 10.5).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "NX", "10.5", "member"], Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.NotExists).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "XX", "10.5", "member"], Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.Exists).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "GT", "10.5", "member"], Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.GreaterThan).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "LT", "10.5", "member"], Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.LessThan).GetArgs()),

            // SortedSetAdd - Multiple Members
            () => Assert.Equal(["ZADD", "key", "10.5", "member1", "8.25", "member2"], Request.SortedSetAddAsync("key", [
                new SortedSetEntry("member1", 10.5),
                new SortedSetEntry("member2", 8.25)
            ]).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "NX", "10.5", "member1", "8.25", "member2"], Request.SortedSetAddAsync("key", [
                new SortedSetEntry("member1", 10.5),
                new SortedSetEntry("member2", 8.25)
            ], SortedSetWhen.NotExists).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "XX", "10.5", "member1", "8.25", "member2"], Request.SortedSetAddAsync("key", [
                new SortedSetEntry("member1", 10.5),
                new SortedSetEntry("member2", 8.25)
            ], SortedSetWhen.Exists).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "GT", "10.5", "member1", "8.25", "member2"], Request.SortedSetAddAsync("key", [
                new SortedSetEntry("member1", 10.5),
                new SortedSetEntry("member2", 8.25)
            ], SortedSetWhen.GreaterThan).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "LT", "10.5", "member1", "8.25", "member2"], Request.SortedSetAddAsync("key", [
                new SortedSetEntry("member1", 10.5),
                new SortedSetEntry("member2", 8.25)
            ], SortedSetWhen.LessThan).GetArgs()),

            // SortedSetRemove - Single Member
            () => Assert.Equal(["ZREM", "key", "member"], Request.SortedSetRemoveAsync("key", "member").GetArgs()),

            // SortedSetRemove - Multiple Members
            () => Assert.Equal(["ZREM", "key", "member1", "member2", "member3"], Request.SortedSetRemoveAsync("key", ["member1", "member2", "member3"]).GetArgs()),
            () => Assert.Equal(["ZREM", "key"], Request.SortedSetRemoveAsync("key", Array.Empty<ValkeyValue>()).GetArgs()),
            () => Assert.Equal(["ZREM", "key", "", " ", "null", "0", "-1"], Request.SortedSetRemoveAsync("key", ["", " ", "null", "0", "-1"]).GetArgs()),

            // Double formatting tests
            () => Assert.Equal("+inf", double.PositiveInfinity.ToGlideString().ToString()),
            () => Assert.Equal("-inf", double.NegativeInfinity.ToGlideString().ToString()),
            () => Assert.Equal("nan", double.NaN.ToGlideString().ToString()),
            () => Assert.Equal("0", 0.0.ToGlideString().ToString()),
            () => Assert.Equal("10.5", 10.5.ToGlideString().ToString()),

            () => Assert.Equal(["ZRANGE", "key", "-", "+", "BYLEX"], Request.SortedSetRangeByValueAsync("key", double.NegativeInfinity, double.PositiveInfinity, Exclude.None, Order.Ascending, 0, -1).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "6", "2", "BYSCORE", "REV", "WITHSCORES"], Request.SortedSetRangeByScoreWithScoresAsync("key", 2.0, 6.0, order: Order.Descending).GetArgs())
        );
    }

    [Fact]
    public void RangeByLex_ToArgs_GeneratesCorrectArguments()
    {
        Assert.Multiple(
            // Basic range
            () => Assert.Equal(["[a", "[z", "BYLEX"], new RangeByLex(LexBoundary.Inclusive("a"), LexBoundary.Inclusive("z")).ToArgs()),

            // Exclusive boundaries
            () => Assert.Equal(["(a", "(z", "BYLEX"], new RangeByLex(LexBoundary.Exclusive("a"), LexBoundary.Exclusive("z")).ToArgs()),

            // Mixed boundaries
            () => Assert.Equal(["[a", "(z", "BYLEX"], new RangeByLex(LexBoundary.Inclusive("a"), LexBoundary.Exclusive("z")).ToArgs()),

            // Infinity boundaries
            () => Assert.Equal(["-", "+", "BYLEX"], new RangeByLex(LexBoundary.NegativeInfinity(), LexBoundary.PositiveInfinity()).ToArgs()),

            // With reverse
            () => Assert.Equal(["[z", "[a", "BYLEX", "REV"], new RangeByLex(LexBoundary.Inclusive("a"), LexBoundary.Inclusive("z")).SetReverse().ToArgs()),

            // With limit
            () => Assert.Equal(["[a", "[z", "BYLEX", "LIMIT", "10", "20"], new RangeByLex(LexBoundary.Inclusive("a"), LexBoundary.Inclusive("z")).SetLimit(10, 20).ToArgs()),

            // With reverse and limit
            () => Assert.Equal(["[z", "[a", "BYLEX", "REV", "LIMIT", "5", "15"], new RangeByLex(LexBoundary.Inclusive("a"), LexBoundary.Inclusive("z")).SetReverse().SetLimit(5, 15).ToArgs())
        );
    }

    [Fact]
    public void RangeByScore_ToArgs_GeneratesCorrectArguments()
    {
        Assert.Multiple(
            // Basic range
            () => Assert.Equal(["10", "20", "BYSCORE"], new RangeByScore(ScoreBoundary.Inclusive(10), ScoreBoundary.Inclusive(20)).ToArgs()),

            // Exclusive boundaries
            () => Assert.Equal(["(10", "(20", "BYSCORE"], new RangeByScore(ScoreBoundary.Exclusive(10), ScoreBoundary.Exclusive(20)).ToArgs()),

            // Mixed boundaries
            () => Assert.Equal(["10", "(20", "BYSCORE"], new RangeByScore(ScoreBoundary.Inclusive(10), ScoreBoundary.Exclusive(20)).ToArgs()),

            // Infinity boundaries
            () => Assert.Equal(["-inf", "+inf", "BYSCORE"], new RangeByScore(ScoreBoundary.NegativeInfinity(), ScoreBoundary.PositiveInfinity()).ToArgs()),

            // With reverse
            () => Assert.Equal(["20", "10", "BYSCORE", "REV"], new RangeByScore(ScoreBoundary.Inclusive(10), ScoreBoundary.Inclusive(20)).SetReverse().ToArgs()),

            // With limit
            () => Assert.Equal(["10", "20", "BYSCORE", "LIMIT", "10", "20"], new RangeByScore(ScoreBoundary.Inclusive(10), ScoreBoundary.Inclusive(20)).SetLimit(10, 20).ToArgs()),

            // With reverse and limit
            () => Assert.Equal(["20", "10", "BYSCORE", "REV", "LIMIT", "5", "15"], new RangeByScore(ScoreBoundary.Inclusive(10), ScoreBoundary.Inclusive(20)).SetReverse().SetLimit(5, 15).ToArgs())
        );
    }

    [Fact]
    public void SortedSetCardAsync_ValidatesArguments()
    {
        Assert.Multiple(
            // Basic ZCARD command
            () => Assert.Equal(["ZCARD", "key"], Request.SortedSetCardAsync("key").GetArgs()),
            () => Assert.Equal(["ZCARD", "mykey"], Request.SortedSetCardAsync("mykey").GetArgs()),
            () => Assert.Equal(["ZCARD", "test:sorted:set"], Request.SortedSetCardAsync("test:sorted:set").GetArgs()),

            // Empty key should work
            () => Assert.Equal(["ZCARD", ""], Request.SortedSetCardAsync("").GetArgs())
        );
    }

    [Fact]
    public void SortedSetCountAsync_ValidatesArguments()
    {
        Assert.Multiple(
            // Default parameters (negative infinity to positive infinity)
            () => Assert.Equal(["ZCOUNT", "key", "-inf", "+inf"], Request.SortedSetCountAsync("key").GetArgs()),

            // Specific score ranges
            () => Assert.Equal(["ZCOUNT", "key", "1", "10"], Request.SortedSetCountAsync("key", 1.0, 10.0).GetArgs()),
            () => Assert.Equal(["ZCOUNT", "key", "0", "100"], Request.SortedSetCountAsync("key", 0.0, 100.0).GetArgs()),
            () => Assert.Equal(["ZCOUNT", "key", "-5", "5"], Request.SortedSetCountAsync("key", -5.0, 5.0).GetArgs()),

            // Decimal scores
            () => Assert.Equal(["ZCOUNT", "key", "1.5", "9.75"], Request.SortedSetCountAsync("key", 1.5, 9.75).GetArgs()),
            () => Assert.Equal(["ZCOUNT", "key", "0.1", "0.9"], Request.SortedSetCountAsync("key", 0.1, 0.9).GetArgs()),

            // Infinity values
            () => Assert.Equal(["ZCOUNT", "key", "-inf", "10"], Request.SortedSetCountAsync("key", double.NegativeInfinity, 10.0).GetArgs()),
            () => Assert.Equal(["ZCOUNT", "key", "0", "+inf"], Request.SortedSetCountAsync("key", 0.0, double.PositiveInfinity).GetArgs()),
            () => Assert.Equal(["ZCOUNT", "key", "-inf", "+inf"], Request.SortedSetCountAsync("key", double.NegativeInfinity, double.PositiveInfinity).GetArgs()),

            // Exclude options
            () => Assert.Equal(["ZCOUNT", "key", "1", "10"], Request.SortedSetCountAsync("key", 1.0, 10.0, Exclude.None).GetArgs()),
            () => Assert.Equal(["ZCOUNT", "key", "(1", "10"], Request.SortedSetCountAsync("key", 1.0, 10.0, Exclude.Start).GetArgs()),
            () => Assert.Equal(["ZCOUNT", "key", "1", "(10"], Request.SortedSetCountAsync("key", 1.0, 10.0, Exclude.Stop).GetArgs()),
            () => Assert.Equal(["ZCOUNT", "key", "(1", "(10"], Request.SortedSetCountAsync("key", 1.0, 10.0, Exclude.Both).GetArgs()),

            // Edge cases
            () => Assert.Equal(["ZCOUNT", "key", "0", "0"], Request.SortedSetCountAsync("key", 0.0, 0.0).GetArgs()),
            () => Assert.Equal(["ZCOUNT", "key", "(0", "(0"], Request.SortedSetCountAsync("key", 0.0, 0.0, Exclude.Both).GetArgs()),

            // Different key names
            () => Assert.Equal(["ZCOUNT", "mykey", "1", "10"], Request.SortedSetCountAsync("mykey", 1.0, 10.0).GetArgs()),
            () => Assert.Equal(["ZCOUNT", "test:sorted:set", "1", "10"], Request.SortedSetCountAsync("test:sorted:set", 1.0, 10.0).GetArgs())
        );
    }

    [Fact]
    public void SortedSetRangeByRank_ValidatesArguments()
    {
        Assert.Multiple(
            // Basic functionality
            () => Assert.Equal(["ZRANGE", "key", "0", "-1"], Request.SortedSetRangeByRankAsync("key").GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "1", "5"], Request.SortedSetRangeByRankAsync("key", 1, 5).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "-5", "-1"], Request.SortedSetRangeByRankAsync("key", -5, -1).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "0", "-1", "REV"], Request.SortedSetRangeByRankAsync("key", 0, -1, Order.Descending).GetArgs()),

            // With scores
            () => Assert.Equal(["ZRANGE", "key", "0", "-1", "WITHSCORES"], Request.SortedSetRangeByRankWithScoresAsync("key").GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "1", "5", "WITHSCORES"], Request.SortedSetRangeByRankWithScoresAsync("key", 1, 5).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "0", "-1", "REV", "WITHSCORES"], Request.SortedSetRangeByRankWithScoresAsync("key", 0, -1, Order.Descending).GetArgs()),

            // Edge cases
            () => Assert.Equal(["ZRANGE", "key", "0", "0"], Request.SortedSetRangeByRankAsync("key", 0, 0).GetArgs()),
            () => Assert.Equal(["ZRANGE", "mykey", "0", "10"], Request.SortedSetRangeByRankAsync("mykey", 0, 10).GetArgs())
        );
    }

    [Fact]
    public void SortedSetRangeByScore_ValidatesArguments()
    {
        Assert.Multiple(
            // Basic functionality
            () => Assert.Equal(["ZRANGE", "key", "-inf", "+inf", "BYSCORE"], Request.SortedSetRangeByScoreAsync("key").GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "1", "10", "BYSCORE"], Request.SortedSetRangeByScoreAsync("key", 1.0, 10.0).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "1.5", "9.75", "BYSCORE"], Request.SortedSetRangeByScoreAsync("key", 1.5, 9.75).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "-inf", "10", "BYSCORE"], Request.SortedSetRangeByScoreAsync("key", double.NegativeInfinity, 10.0).GetArgs()),

            // Exclude options
            () => Assert.Equal(["ZRANGE", "key", "(1", "10", "BYSCORE"], Request.SortedSetRangeByScoreAsync("key", 1.0, 10.0, Exclude.Start).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "1", "(10", "BYSCORE"], Request.SortedSetRangeByScoreAsync("key", 1.0, 10.0, Exclude.Stop).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "(1", "(10", "BYSCORE"], Request.SortedSetRangeByScoreAsync("key", 1.0, 10.0, Exclude.Both).GetArgs()),

            // Order and limit
            () => Assert.Equal(["ZRANGE", "key", "10", "1", "BYSCORE", "REV"], Request.SortedSetRangeByScoreAsync("key", 1.0, 10.0, Exclude.None, Order.Descending).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "1", "10", "BYSCORE", "LIMIT", "2", "3"], Request.SortedSetRangeByScoreAsync("key", 1.0, 10.0, Exclude.None, Order.Ascending, 2, 3).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "(10", "(1", "BYSCORE", "REV", "LIMIT", "1", "5"], Request.SortedSetRangeByScoreAsync("key", 1.0, 10.0, Exclude.Both, Order.Descending, 1, 5).GetArgs()),

            // With scores
            () => Assert.Equal(["ZRANGE", "key", "-inf", "+inf", "BYSCORE", "WITHSCORES"], Request.SortedSetRangeByScoreWithScoresAsync("key").GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "1", "10", "BYSCORE", "WITHSCORES"], Request.SortedSetRangeByScoreWithScoresAsync("key", 1.0, 10.0).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "10", "1", "BYSCORE", "REV", "WITHSCORES"], Request.SortedSetRangeByScoreWithScoresAsync("key", 1.0, 10.0, Exclude.None, Order.Descending).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "1", "10", "BYSCORE", "LIMIT", "2", "3", "WITHSCORES"], Request.SortedSetRangeByScoreWithScoresAsync("key", 1.0, 10.0, Exclude.None, Order.Ascending, 2, 3).GetArgs()),

            // Edge cases
            () => Assert.Equal(["ZRANGE", "key", "0", "0", "BYSCORE"], Request.SortedSetRangeByScoreAsync("key", 0.0, 0.0).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "(0", "(0", "BYSCORE"], Request.SortedSetRangeByScoreAsync("key", 0.0, 0.0, Exclude.Both).GetArgs())
        );
    }

    [Fact]
    public void SortedSetRangeByValue_ValidatesArguments()
    {
        Assert.Multiple(
            // Basic lexicographical ranges with explicit min/max
            () => Assert.Equal(["ZRANGE", "key", "[a", "[z", "BYLEX"], Request.SortedSetRangeByValueAsync("key", "a", "z", Exclude.None, 0, -1).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "[apple", "[zebra", "BYLEX"], Request.SortedSetRangeByValueAsync("key", "apple", "zebra", Exclude.None, 0, -1).GetArgs()),

            // Exclude options with explicit min/max
            () => Assert.Equal(["ZRANGE", "key", "(a", "[z", "BYLEX"], Request.SortedSetRangeByValueAsync("key", "a", "z", Exclude.Start, 0, -1).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "[a", "(z", "BYLEX"], Request.SortedSetRangeByValueAsync("key", "a", "z", Exclude.Stop, 0, -1).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "(a", "(z", "BYLEX"], Request.SortedSetRangeByValueAsync("key", "a", "z", Exclude.Both, 0, -1).GetArgs()),

            // Skip and take parameters with explicit min/max
            () => Assert.Equal(["ZRANGE", "key", "[a", "[z", "BYLEX", "LIMIT", "2", "3"], Request.SortedSetRangeByValueAsync("key", "a", "z", Exclude.None, 2, 3).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "(a", "(z", "BYLEX", "LIMIT", "1", "5"], Request.SortedSetRangeByValueAsync("key", "a", "z", Exclude.Both, 1, 5).GetArgs()),

            // Default parameters (should use lexicographical infinity symbols)
            () => Assert.Equal(["ZRANGE", "key", "-", "+", "BYLEX"], Request.SortedSetRangeByValueAsync("key").GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "-", "+", "BYLEX"], Request.SortedSetRangeByValueAsync("key", default, default, Exclude.None, Order.Ascending, 0, -1).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "+", "-", "BYLEX", "REV"], Request.SortedSetRangeByValueAsync("key", default, default, Exclude.None, Order.Descending, 0, -1).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "-", "+", "BYLEX", "LIMIT", "2", "3"], Request.SortedSetRangeByValueAsync("key", default, default, Exclude.None, Order.Ascending, 2, 3).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "-", "+", "BYLEX"], Request.SortedSetRangeByValueAsync("key", double.NegativeInfinity, double.PositiveInfinity, Exclude.None, Order.Ascending, 0, -1).GetArgs()),

            // Edge cases
            () => Assert.Equal(["ZRANGE", "key", "[a", "[a", "BYLEX"], Request.SortedSetRangeByValueAsync("key", "a", "a", Exclude.None, 0, -1).GetArgs()),
            () => Assert.Equal(["ZRANGE", "key", "[", "[z", "BYLEX"], Request.SortedSetRangeByValueAsync("key", "", "z", Exclude.None, 0, -1).GetArgs())
        );
    }
}
