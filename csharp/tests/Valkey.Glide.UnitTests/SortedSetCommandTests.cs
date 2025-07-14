// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

using Xunit;

namespace Valkey.Glide.UnitTests;

public class SortedSetCommandTests
{
    [Fact]
    public void SortedSetAddAsync_SingleMember_ReturnsCorrectArgs()
    {
        // Act
        var result = Request.SortedSetAddAsync("key", "member", 10.5);

        // Assert
        Assert.Equal(["ZADD", "key", "10.5", "member"], result.GetArgs());
    }

    [Fact]
    public void SortedSetAddAsync_SingleMemberWithNX_ReturnsCorrectArgs()
    {
        // Act
        var result = Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.NotExists);

        // Assert
        Assert.Equal(["ZADD", "key", "NX", "10.5", "member"], result.GetArgs());
    }

    [Fact]
    public void SortedSetAddAsync_SingleMemberWithXX_ReturnsCorrectArgs()
    {
        // Act
        var result = Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.Exists);

        // Assert
        Assert.Equal(["ZADD", "key", "XX", "10.5", "member"], result.GetArgs());
    }

    [Fact]
    public void SortedSetAddAsync_SingleMemberWithGT_ReturnsCorrectArgs()
    {
        // Act
        var result = Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.GreaterThan);

        // Assert
        Assert.Equal(["ZADD", "key", "GT", "10.5", "member"], result.GetArgs());
    }

    [Fact]
    public void SortedSetAddAsync_SingleMemberWithLT_ReturnsCorrectArgs()
    {
        // Act
        var result = Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.LessThan);

        // Assert
        Assert.Equal(["ZADD", "key", "LT", "10.5", "member"], result.GetArgs());
    }

    [Fact]
    public void SortedSetAddAsync_MultipleMembers_ReturnsCorrectArgs()
    {
        // Arrange
        var entries = new SortedSetEntry[]
        {
            new("member1", 10.5),
            new("member2", 8.25)
        };

        // Act
        var result = Request.SortedSetAddAsync("key", entries);

        // Assert
        Assert.Equal(["ZADD", "key", "10.5", "member1", "8.25", "member2"], result.GetArgs());
    }

    [Fact]
    public void SortedSetAddAsync_MultipleMembersWithNX_ReturnsCorrectArgs()
    {
        // Arrange
        var entries = new SortedSetEntry[]
        {
            new("member1", 10.5),
            new("member2", 8.25)
        };

        // Act
        var result = Request.SortedSetAddAsync("key", entries, SortedSetWhen.NotExists);

        // Assert
        Assert.Equal(["ZADD", "key", "NX", "10.5", "member1", "8.25", "member2"], result.GetArgs());
    }

    [Fact]
    public void SortedSetAddAsync_MultipleMembersWithXX_ReturnsCorrectArgs()
    {
        // Arrange
        var entries = new SortedSetEntry[]
        {
            new("member1", 10.5),
            new("member2", 8.25)
        };

        // Act
        var result = Request.SortedSetAddAsync("key", entries, SortedSetWhen.Exists);

        // Assert
        Assert.Equal(["ZADD", "key", "XX", "10.5", "member1", "8.25", "member2"], result.GetArgs());
    }

    [Fact]
    public void SortedSetAddAsync_MultipleMembersWithGT_ReturnsCorrectArgs()
    {
        // Arrange
        var entries = new SortedSetEntry[]
        {
            new("member1", 10.5),
            new("member2", 8.25)
        };

        // Act
        var result = Request.SortedSetAddAsync("key", entries, SortedSetWhen.GreaterThan);

        // Assert
        Assert.Equal(["ZADD", "key", "GT", "10.5", "member1", "8.25", "member2"], result.GetArgs());
    }

    [Fact]
    public void SortedSetAddAsync_MultipleMembersWithLT_ReturnsCorrectArgs()
    {
        // Arrange
        var entries = new SortedSetEntry[]
        {
            new("member1", 10.5),
            new("member2", 8.25)
        };

        // Act
        var result = Request.SortedSetAddAsync("key", entries, SortedSetWhen.LessThan);

        // Assert
        Assert.Equal(["ZADD", "key", "LT", "10.5", "member1", "8.25", "member2"], result.GetArgs());
    }

    [Fact]
    public void SortedSetAddAsync_ThrowsOnUnsupportedFlags()
    {
        // Act & Assert
        Assert.Throws<NotImplementedException>(() => Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.Always, CommandFlags.DemandReplica));
    }

    [Fact]
    public void SortedSetAddAsync_MultipleMembersThrowsOnUnsupportedFlags()
    {
        // Arrange
        var entries = new SortedSetEntry[] { new("member", 10.5) };

        // Act & Assert
        Assert.Throws<NotImplementedException>(() => Request.SortedSetAddAsync("key", entries, SortedSetWhen.Always, CommandFlags.DemandReplica));
    }

    [Fact]
    public void DoubleToGlideString_SpecialValues_ReturnsCorrectFormat()
    {
        // Test special double values formatting
        Assert.Equal("+inf", double.PositiveInfinity.ToGlideString().ToString());
        Assert.Equal("-inf", double.NegativeInfinity.ToGlideString().ToString());
        Assert.Equal("nan", double.NaN.ToGlideString().ToString());
        Assert.Equal("0", 0.0.ToGlideString().ToString());
        Assert.Equal("10.5", 10.5.ToGlideString().ToString());
    }
}
