// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.IntegrationTests.Utils;
using Valkey.Glide.Pipeline;
using Xunit;

namespace Valkey.Glide.IntegrationTests;

[Collection("GlideTests")]
public class SortedSetBatchTests
{
    private readonly GlideTestFixture _fixture;

    public SortedSetBatchTests(GlideTestFixture fixture)
    {
        _fixture = fixture;
    }

    [Fact]
    public async Task SortedSetBatch_SingleOperations_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var batch = new Batch();

        // Act
        batch.SortedSetAdd(key, "member1", 1.0);
        batch.SortedSetAdd(key, "member2", 2.0);
        
        var results = await client.ExecuteBatch(batch);

        // Assert
        Assert.Equal(2, results.Length);
        Assert.True((bool)results[0]);
        Assert.True((bool)results[1]);
    }

    [Fact]
    public async Task SortedSetBatch_MultipleOperations_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var batch = new Batch();
        var entries = new SortedSetEntry[]
        {
            new("member1", 1.0),
            new("member2", 2.0),
            new("member3", 3.0)
        };

        // Act
        batch.SortedSetAdd(key, entries);
        batch.SortedSetAdd(key, "member4", 4.0);
        
        var results = await client.ExecuteBatch(batch);

        // Assert
        Assert.Equal(2, results.Length);
        Assert.Equal(3L, (long)results[0]); // 3 new members added
        Assert.True((bool)results[1]); // 1 new member added
    }

    [Fact]
    public async Task SortedSetBatch_MixedWithOtherCommands_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var sortedSetKey = TestUtils.CreateKeyName();
        var stringKey = TestUtils.CreateKeyName();
        var batch = new Batch();

        // Act
        batch.Set(stringKey, "test_value");
        batch.SortedSetAdd(sortedSetKey, "member1", 1.0);
        batch.Get(stringKey);
        batch.SortedSetAdd(sortedSetKey, [new SortedSetEntry("member2", 2.0)]);
        
        var results = await client.ExecuteBatch(batch);

        // Assert
        Assert.Equal(4, results.Length);
        Assert.Equal("OK", results[0].ToString());
        Assert.True((bool)results[1]);
        Assert.Equal("test_value", results[2].ToString());
        Assert.Equal(1L, (long)results[3]);
    }

    [Fact]
    public async Task SortedSetBatch_EmptyEntries_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var batch = new Batch();
        var emptyEntries = Array.Empty<SortedSetEntry>();

        // Act
        batch.SortedSetAdd(key, emptyEntries);
        
        var results = await client.ExecuteBatch(batch);

        // Assert
        Assert.Single(results);
        Assert.Equal(0L, (long)results[0]);
    }

    [Fact]
    public async Task SortedSetBatch_UpdateExistingMembers_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var key = TestUtils.CreateKeyName();
        var batch1 = new Batch();
        var batch2 = new Batch();

        // Act - First batch: add initial members
        batch1.SortedSetAdd(key, [new SortedSetEntry("member1", 1.0), new SortedSetEntry("member2", 2.0)]);
        await client.ExecuteBatch(batch1);

        // Act - Second batch: update existing and add new
        batch2.SortedSetAdd(key, [
            new SortedSetEntry("member1", 1.5), // Update existing
            new SortedSetEntry("member3", 3.0)  // Add new
        ]);
        
        var results = await client.ExecuteBatch(batch2);

        // Assert
        Assert.Single(results);
        Assert.Equal(1L, (long)results[0]); // Only 1 new member added
    }
}
