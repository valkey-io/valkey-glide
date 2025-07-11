// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.IntegrationTests.Utils;
using Xunit;

namespace Valkey.Glide.IntegrationTests;

[Collection("GlideTests")]
public class SortedSetCommandTests
{
    private readonly GlideTestFixture _fixture;

    public SortedSetCommandTests(GlideTestFixture fixture)
    {
        _fixture = fixture;
    }

    [Fact]
    public async Task SortedSetAddAsync_SingleMember_NewMember_ReturnsTrue()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var member = "test_member";
        var score = 1.5;

        // Act
        var result = await client.SortedSetAddAsync(key, member, score);

        // Assert
        Assert.True(result);
    }

    [Fact]
    public async Task SortedSetAddAsync_SingleMember_ExistingMember_ReturnsFalse()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var member = "test_member";
        var score1 = 1.5;
        var score2 = 2.5;

        // Act
        await client.SortedSetAddAsync(key, member, score1);
        var result = await client.SortedSetAddAsync(key, member, score2);

        // Assert
        Assert.False(result);
    }

    [Fact]
    public async Task SortedSetAddAsync_MultipleMembers_NewMembers_ReturnsCount()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var entries = new SortedSetEntry[]
        {
            new("member1", 1.0),
            new("member2", 2.5),
            new("member3", 3.7)
        };

        // Act
        var result = await client.SortedSetAddAsync(key, entries);

        // Assert
        Assert.Equal(3, result);
    }

    [Fact]
    public async Task SortedSetAddAsync_MultipleMembers_SomeExisting_ReturnsNewCount()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        
        // Add initial members
        var initialEntries = new SortedSetEntry[]
        {
            new("member1", 1.0),
            new("member2", 2.0)
        };
        await client.SortedSetAddAsync(key, initialEntries);

        // Add mix of new and existing members
        var mixedEntries = new SortedSetEntry[]
        {
            new("member1", 1.5), // existing, score updated
            new("member3", 3.0), // new
            new("member4", 4.0)  // new
        };

        // Act
        var result = await client.SortedSetAddAsync(key, mixedEntries);

        // Assert
        Assert.Equal(2, result); // Only 2 new members added
    }

    [Fact]
    public async Task SortedSetAddAsync_EmptyEntries_ReturnsZero()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var entries = Array.Empty<SortedSetEntry>();

        // Act
        var result = await client.SortedSetAddAsync(key, entries);

        // Assert
        Assert.Equal(0, result);
    }

    [Fact]
    public async Task SortedSetAddAsync_NegativeScore_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var member = "test_member";
        var score = -2.5;

        // Act
        var result = await client.SortedSetAddAsync(key, member, score);

        // Assert
        Assert.True(result);
    }

    [Fact]
    public async Task SortedSetAddAsync_ZeroScore_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var member = "test_member";
        var score = 0.0;

        // Act
        var result = await client.SortedSetAddAsync(key, member, score);

        // Assert
        Assert.True(result);
    }

    [Fact]
    public async Task SortedSetAddAsync_LargeScore_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var member = "test_member";
        var score = double.MaxValue;

        // Act
        var result = await client.SortedSetAddAsync(key, member, score);

        // Assert
        Assert.True(result);
    }

    [Fact]
    public async Task SortedSetAddAsync_DuplicateScores_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var entries = new SortedSetEntry[]
        {
            new("member1", 1.0),
            new("member2", 1.0), // Same score
            new("member3", 1.0)  // Same score
        };

        // Act
        var result = await client.SortedSetAddAsync(key, entries);

        // Assert
        Assert.Equal(3, result);
    }

    [Fact]
    public async Task SortedSetAddAsync_BinaryData_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var binaryMember = new byte[] { 0x01, 0x02, 0x03, 0xFF };
        var score = 1.5;

        // Act
        var result = await client.SortedSetAddAsync(key, binaryMember, score);

        // Assert
        Assert.True(result);
    }
}
