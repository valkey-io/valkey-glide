// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;
using Xunit;

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.UnitTests;

public class SortedSetCommandTests
{
    [Fact]
    public void SortedSetAddAsync_SingleMember_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var member = "test_member";
        var score = 1.5;

        // Act
        var result = Request.SortedSetAddAsync(key, member, score);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Equal(3, result.Arguments.Length);
        Assert.Equal(key, result.Arguments[0]);
        Assert.Equal("1.5", result.Arguments[1]);
        Assert.Equal(member, result.Arguments[2]);
        Assert.False(result.IsNullable);
        
        // Test converter - returns true if 1 member was added, false if updated
        Assert.True(result.Converter(1));
        Assert.False(result.Converter(0));
    }

    [Fact]
    public void SortedSetAddAsync_SingleMember_WithNotExists_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var member = "test_member";
        var score = 1.5;

        // Act
        var result = Request.SortedSetAddAsync(key, member, score, SortedSetWhen.NotExists);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Equal(4, result.Arguments.Length);
        Assert.Equal(key, result.Arguments[0]);
        Assert.Equal("NX", result.Arguments[1]);
        Assert.Equal("1.5", result.Arguments[2]);
        Assert.Equal(member, result.Arguments[3]);
    }

    [Fact]
    public void SortedSetAddAsync_SingleMember_WithExists_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var member = "test_member";
        var score = 1.5;

        // Act
        var result = Request.SortedSetAddAsync(key, member, score, SortedSetWhen.Exists);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Equal(4, result.Arguments.Length);
        Assert.Equal(key, result.Arguments[0]);
        Assert.Equal("XX", result.Arguments[1]);
        Assert.Equal("1.5", result.Arguments[2]);
        Assert.Equal(member, result.Arguments[3]);
    }

    [Fact]
    public void SortedSetAddAsync_SingleMember_WithGreaterThan_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var member = "test_member";
        var score = 1.5;

        // Act
        var result = Request.SortedSetAddAsync(key, member, score, SortedSetWhen.GreaterThan);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Equal(4, result.Arguments.Length);
        Assert.Equal(key, result.Arguments[0]);
        Assert.Equal("GT", result.Arguments[1]);
        Assert.Equal("1.5", result.Arguments[2]);
        Assert.Equal(member, result.Arguments[3]);
    }

    [Fact]
    public void SortedSetAddAsync_SingleMember_WithLessThan_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var member = "test_member";
        var score = 1.5;

        // Act
        var result = Request.SortedSetAddAsync(key, member, score, SortedSetWhen.LessThan);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Equal(4, result.Arguments.Length);
        Assert.Equal(key, result.Arguments[0]);
        Assert.Equal("LT", result.Arguments[1]);
        Assert.Equal("1.5", result.Arguments[2]);
        Assert.Equal(member, result.Arguments[3]);
    }

    [Fact]
    public void SortedSetAddAsync_SingleMember_WithCombinedFlags_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var member = "test_member";
        var score = 1.5;

        // Act
        var result = Request.SortedSetAddAsync(key, member, score, SortedSetWhen.Exists | SortedSetWhen.GreaterThan);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Equal(5, result.Arguments.Length);
        Assert.Equal(key, result.Arguments[0]);
        Assert.Equal("XX", result.Arguments[1]);
        Assert.Equal("GT", result.Arguments[2]);
        Assert.Equal("1.5", result.Arguments[3]);
        Assert.Equal(member, result.Arguments[4]);
    }

    [Fact]
    public void SortedSetAddAsync_MultipleMembers_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var values = new SortedSetEntry[]
        {
            new("member1", 1.0),
            new("member2", 2.5),
            new("member3", 3.7)
        };

        // Act
        var result = Request.SortedSetAddAsync(key, values);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Equal(7, result.Arguments.Length); // key + (score, member) * 3
        Assert.Equal(key, result.Arguments[0]);
        
        // Check first entry: score then member
        Assert.Equal("1", result.Arguments[1]);
        Assert.Equal("member1", result.Arguments[2]);
        
        // Check second entry: score then member
        Assert.Equal("2.5", result.Arguments[3]);
        Assert.Equal("member2", result.Arguments[4]);
        
        // Check third entry: score then member
        Assert.Equal("3.7", result.Arguments[5]);
        Assert.Equal("member3", result.Arguments[6]);
        
        Assert.False(result.IsNullable);
    }

    [Fact]
    public void SortedSetAddAsync_MultipleMembers_WithNotExists_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var values = new SortedSetEntry[]
        {
            new("member1", 1.0),
            new("member2", 2.5)
        };

        // Act
        var result = Request.SortedSetAddAsync(key, values, SortedSetWhen.NotExists);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Equal(6, result.Arguments.Length); // key + NX + (score, member) * 2
        Assert.Equal(key, result.Arguments[0]);
        Assert.Equal("NX", result.Arguments[1]);
        Assert.Equal("1", result.Arguments[2]);
        Assert.Equal("member1", result.Arguments[3]);
        Assert.Equal("2.5", result.Arguments[4]);
        Assert.Equal("member2", result.Arguments[5]);
    }

    [Fact]
    public void SortedSetAddAsync_EmptyValues_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var values = Array.Empty<SortedSetEntry>();

        // Act
        var result = Request.SortedSetAddAsync(key, values);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Single(result.Arguments); // Only key
        Assert.Equal(key, result.Arguments[0]);
    }

    [Fact]
    public void SortedSetAddAsync_NegativeScore_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var member = "test_member";
        var score = -2.5;

        // Act
        var result = Request.SortedSetAddAsync(key, member, score);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Equal(3, result.Arguments.Length);
        Assert.Equal(key, result.Arguments[0]);
        Assert.Equal("-2.5", result.Arguments[1]);
        Assert.Equal(member, result.Arguments[2]);
    }

    [Fact]
    public void SortedSetAddAsync_ZeroScore_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var member = "test_member";
        var score = 0.0;

        // Act
        var result = Request.SortedSetAddAsync(key, member, score);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Equal(3, result.Arguments.Length);
        Assert.Equal(key, result.Arguments[0]);
        Assert.Equal("0", result.Arguments[1]);
        Assert.Equal(member, result.Arguments[2]);
    }

    [Fact]
    public void SortedSetWhenExtensions_Parse_ConvertsCorrectly()
    {
        // Act & Assert
        Assert.Equal(SortedSetWhen.Always, SortedSetWhenExtensions.Parse(When.Always));
        Assert.Equal(SortedSetWhen.Exists, SortedSetWhenExtensions.Parse(When.Exists));
        Assert.Equal(SortedSetWhen.NotExists, SortedSetWhenExtensions.Parse(When.NotExists));
    }

    [Fact]
    public void SortedSetWhenExtensions_Parse_ThrowsForInvalidWhen()
    {
        // Act & Assert
        Assert.Throws<ArgumentOutOfRangeException>(() => SortedSetWhenExtensions.Parse((When)999));
    }

    [Fact]
    public void SortedSetWhenExtensions_ToArgs_ReturnsCorrectArgs()
    {
        // Act & Assert
        Assert.Empty(SortedSetWhen.Always.ToArgs());
        Assert.Equal(["NX"], SortedSetWhen.NotExists.ToArgs());
        Assert.Equal(["XX"], SortedSetWhen.Exists.ToArgs());
        Assert.Equal(["GT"], SortedSetWhen.GreaterThan.ToArgs());
        Assert.Equal(["LT"], SortedSetWhen.LessThan.ToArgs());
        
        // Test combined flags
        var combined = SortedSetWhen.Exists | SortedSetWhen.GreaterThan;
        var args = combined.ToArgs();
        Assert.Contains("XX", args);
        Assert.Contains("GT", args);
        Assert.Equal(2, args.Length);
    }

    [Fact]
    public void SortedSetWhenExtensions_CountBits_ReturnsCorrectCount()
    {
        // Act & Assert
        Assert.Equal(0u, SortedSetWhen.Always.CountBits());
        Assert.Equal(1u, SortedSetWhen.NotExists.CountBits());
        Assert.Equal(1u, SortedSetWhen.Exists.CountBits());
        Assert.Equal(1u, SortedSetWhen.GreaterThan.CountBits());
        Assert.Equal(1u, SortedSetWhen.LessThan.CountBits());
        Assert.Equal(2u, (SortedSetWhen.Exists | SortedSetWhen.GreaterThan).CountBits());
        Assert.Equal(3u, (SortedSetWhen.Exists | SortedSetWhen.GreaterThan | SortedSetWhen.LessThan).CountBits());
    }
}
