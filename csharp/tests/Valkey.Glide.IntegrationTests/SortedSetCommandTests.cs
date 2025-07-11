// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
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
        Assert.False(result); // Member already existed, score was updated
    }

    [Fact]
    public async Task SortedSetAddAsync_SingleMember_WithNotExists_NewMember_ReturnsTrue()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var member = "test_member";
        var score = 1.5;

        // Act
        var result = await client.SortedSetAddAsync(key, member, score, When.NotExists);

        // Assert
        Assert.True(result);
    }

    [Fact]
    public async Task SortedSetAddAsync_SingleMember_WithNotExists_ExistingMember_ReturnsFalse()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var member = "test_member";
        var score1 = 1.5;
        var score2 = 2.5;

        // Act
        await client.SortedSetAddAsync(key, member, score1);
        var result = await client.SortedSetAddAsync(key, member, score2, When.NotExists);

        // Assert
        Assert.False(result); // Member already exists, NX prevents update
    }

    [Fact]
    public async Task SortedSetAddAsync_SingleMember_WithExists_ExistingMember_ReturnsFalse()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var member = "test_member";
        var score1 = 1.5;
        var score2 = 2.5;

        // Act
        await client.SortedSetAddAsync(key, member, score1);
        var result = await client.SortedSetAddAsync(key, member, score2, When.Exists);

        // Assert
        Assert.False(result); // Member exists and was updated, but returns false for updates
    }

    [Fact]
    public async Task SortedSetAddAsync_SingleMember_WithExists_NewMember_ReturnsFalse()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var member = "test_member";
        var score = 1.5;

        // Act
        var result = await client.SortedSetAddAsync(key, member, score, When.Exists);

        // Assert
        Assert.False(result); // Member doesn't exist, XX prevents addition
    }

    [Fact]
    public async Task SortedSetAddAsync_SingleMember_WithGreaterThan_HigherScore_ReturnsFalse()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var member = "test_member";
        var score1 = 1.5;
        var score2 = 2.5;

        // Act
        await client.SortedSetAddAsync(key, member, score1);
        var result = await client.SortedSetAddAsync(key, member, score2, SortedSetWhen.GreaterThan);

        // Assert
        Assert.False(result); // Score was updated but returns false for updates
    }

    [Fact]
    public async Task SortedSetAddAsync_MultipleMembers_NewMembers_ReturnsCount()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var values = new SortedSetEntry[]
        {
            new("member1", 1.0),
            new("member2", 2.5),
            new("member3", 3.7)
        };

        // Act
        var result = await client.SortedSetAddAsync(key, values);

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
        var initialValues = new SortedSetEntry[]
        {
            new("member1", 1.0),
            new("member2", 2.0)
        };
        await client.SortedSetAddAsync(key, initialValues);

        // Add mix of new and existing members
        var mixedValues = new SortedSetEntry[]
        {
            new("member1", 1.5), // existing, score updated
            new("member3", 3.0), // new
            new("member4", 4.0)  // new
        };

        // Act
        var result = await client.SortedSetAddAsync(key, mixedValues);

        // Assert
        Assert.Equal(2, result); // Only 2 new members added
    }

    [Fact]
    public async Task SortedSetAddAsync_MultipleMembers_WithNotExists_ReturnsNewCount()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        
        // Add initial member
        await client.SortedSetAddAsync(key, "existing_member", 1.0);

        // Try to add mix of new and existing members with NX
        var values = new SortedSetEntry[]
        {
            new("existing_member", 2.0), // existing, should be ignored
            new("new_member1", 3.0),     // new
            new("new_member2", 4.0)      // new
        };

        // Act
        var result = await client.SortedSetAddAsync(key, values, When.NotExists);

        // Assert
        Assert.Equal(2, result); // Only 2 new members added, existing ignored
    }

    [Fact]
    public async Task SortedSetAddAsync_AllOverloads_WithCommandFlags_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var member = "test_member";
        var score = 1.5;
        var values = new SortedSetEntry[] { new("member1", 1.0) };

        // Act & Assert - All overloads should work with CommandFlags
        var result1 = await client.SortedSetAddAsync(key, member, score, CommandFlags.None);
        Assert.True(result1);

        var result2 = await client.SortedSetAddAsync(key, "member2", 2.0, When.Always, CommandFlags.None);
        Assert.True(result2);

        var result3 = await client.SortedSetAddAsync(key, "member3", 3.0, SortedSetWhen.Always, CommandFlags.None);
        Assert.True(result3);

        var result4 = await client.SortedSetAddAsync(key + "_array", values, CommandFlags.None);
        Assert.Equal(1, result4);

        var result5 = await client.SortedSetAddAsync(key + "_array2", values, When.Always, CommandFlags.None);
        Assert.Equal(1, result5);

        var result6 = await client.SortedSetAddAsync(key + "_array3", values, SortedSetWhen.Always, CommandFlags.None);
        Assert.Equal(1, result6);
    }
}
