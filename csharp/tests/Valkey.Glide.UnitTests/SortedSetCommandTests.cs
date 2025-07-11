// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

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
        
        // Test converter
        Assert.True(result.Converter(1));
        Assert.False(result.Converter(0));
    }

    [Fact]
    public void SortedSetAddAsync_MultipleMembers_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var entries = new SortedSetEntry[]
        {
            new("member1", 1.0),
            new("member2", 2.5),
            new("member3", 3.7)
        };

        // Act
        var result = Request.SortedSetAddAsync(key, entries);

        // Assert
        Assert.Equal(RequestType.ZAdd, result.RequestType);
        Assert.Equal(7, result.Arguments.Length); // key + (score, member) * 3
        Assert.Equal(key, result.Arguments[0]);
        
        // Check first entry
        Assert.Equal("1", result.Arguments[1]);
        Assert.Equal("member1", result.Arguments[2]);
        
        // Check second entry
        Assert.Equal("2.5", result.Arguments[3]);
        Assert.Equal("member2", result.Arguments[4]);
        
        // Check third entry
        Assert.Equal("3.7", result.Arguments[5]);
        Assert.Equal("member3", result.Arguments[6]);
        
        Assert.False(result.IsNullable);
    }

    [Fact]
    public void SortedSetAddAsync_EmptyEntries_ReturnsCorrectCommand()
    {
        // Arrange
        var key = "test_key";
        var entries = Array.Empty<SortedSetEntry>();

        // Act
        var result = Request.SortedSetAddAsync(key, entries);

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
}
