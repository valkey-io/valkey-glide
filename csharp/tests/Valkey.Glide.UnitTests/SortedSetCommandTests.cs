// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

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
            () => Assert.Equal(["ZREM", "key", "member"], Request.SortedSetRemoveAsync("key", "member", CommandFlags.None).GetArgs()),

            // SortedSetRemove - Multiple Members
            () => Assert.Equal(["ZREM", "key", "member1", "member2", "member3"], Request.SortedSetRemoveAsync("key", ["member1", "member2", "member3"]).GetArgs()),
            () => Assert.Equal(["ZREM", "key", "member1", "member2"], Request.SortedSetRemoveAsync("key", ["member1", "member2"], CommandFlags.None).GetArgs()),
            () => Assert.Equal(["ZREM", "key"], Request.SortedSetRemoveAsync("key", Array.Empty<ValkeyValue>()).GetArgs()),
            () => Assert.Equal(["ZREM", "key", "", " ", "null", "0", "-1"], Request.SortedSetRemoveAsync("key", ["", " ", "null", "0", "-1"]).GetArgs()),

            // Double formatting tests
            () => Assert.Equal("+inf", double.PositiveInfinity.ToGlideString().ToString()),
            () => Assert.Equal("-inf", double.NegativeInfinity.ToGlideString().ToString()),
            () => Assert.Equal("nan", double.NaN.ToGlideString().ToString()),
            () => Assert.Equal("0", 0.0.ToGlideString().ToString()),
            () => Assert.Equal("10.5", 10.5.ToGlideString().ToString())
        );
    }

    [Fact]
    public void ValidateSortedSetCommandExceptions()
    {
        Assert.Multiple(
            // SortedSetAdd exceptions
            () => Assert.Throws<NotImplementedException>(() => Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.Always, CommandFlags.DemandReplica)),
            () => Assert.Throws<NotImplementedException>(() => Request.SortedSetAddAsync("key", [new SortedSetEntry("member", 10.5)], SortedSetWhen.Always, CommandFlags.DemandReplica)),

            // SortedSetRemove exceptions
            () => Assert.Throws<NotImplementedException>(() => Request.SortedSetRemoveAsync("key", "member", CommandFlags.DemandReplica)),
            () => Assert.Throws<NotImplementedException>(() => Request.SortedSetRemoveAsync("key", ["member1", "member2"], CommandFlags.DemandReplica))
        );
    }
}
