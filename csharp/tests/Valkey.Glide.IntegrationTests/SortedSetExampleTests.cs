// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.IntegrationTests.Utils;
using Xunit;

namespace Valkey.Glide.IntegrationTests;

[Collection("GlideTests")]
public class SortedSetExampleTests
{
    private readonly GlideTestFixture _fixture;

    public SortedSetExampleTests(GlideTestFixture fixture)
    {
        _fixture = fixture;
    }

    [Fact]
    public async Task SortedSetExample_GameLeaderboard_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var leaderboardKey = TestUtils.CreateKeyName();

        // Act - Add players to leaderboard
        var player1Added = await client.SortedSetAddAsync(leaderboardKey, "Alice", 1000.0);
        var player2Added = await client.SortedSetAddAsync(leaderboardKey, "Bob", 1500.0);
        var player3Added = await client.SortedSetAddAsync(leaderboardKey, "Charlie", 800.0);

        // Add multiple players at once
        var multiplePlayersAdded = await client.SortedSetAddAsync(leaderboardKey, [
            new SortedSetEntry("David", 1200.0),
            new SortedSetEntry("Eve", 1800.0),
            new SortedSetEntry("Frank", 900.0)
        ]);

        // Update existing player's score
        var aliceUpdated = await client.SortedSetAddAsync(leaderboardKey, "Alice", 1100.0);

        // Assert
        Assert.True(player1Added);
        Assert.True(player2Added);
        Assert.True(player3Added);
        Assert.Equal(3, multiplePlayersAdded); // 3 new players added
        Assert.False(aliceUpdated); // Alice already existed, score was updated
    }

    [Fact]
    public async Task SortedSetExample_ProductRatings_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var ratingsKey = TestUtils.CreateKeyName();

        // Act - Add product ratings (using negative scores for reverse ordering)
        var products = new SortedSetEntry[]
        {
            new("Product_A", -4.8), // 4.8 stars (negative for reverse order)
            new("Product_B", -4.2), // 4.2 stars
            new("Product_C", -4.9), // 4.9 stars
            new("Product_D", -3.5), // 3.5 stars
            new("Product_E", -4.7)  // 4.7 stars
        };

        var productsAdded = await client.SortedSetAddAsync(ratingsKey, products);

        // Update a product rating
        var productBUpdated = await client.SortedSetAddAsync(ratingsKey, "Product_B", -4.6);

        // Assert
        Assert.Equal(5, productsAdded);
        Assert.False(productBUpdated); // Product_B already existed
    }

    [Fact]
    public async Task SortedSetExample_TimestampBasedEvents_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var eventsKey = TestUtils.CreateKeyName();
        var baseTime = DateTimeOffset.UtcNow.ToUnixTimeSeconds();

        // Act - Add events with timestamps as scores
        var events = new SortedSetEntry[]
        {
            new("user_login", baseTime),
            new("page_view", baseTime + 10),
            new("button_click", baseTime + 25),
            new("user_logout", baseTime + 300),
            new("session_timeout", baseTime + 600)
        };

        var eventsAdded = await client.SortedSetAddAsync(eventsKey, events);

        // Add a late event
        var lateEventAdded = await client.SortedSetAddAsync(eventsKey, "error_occurred", baseTime + 150);

        // Assert
        Assert.Equal(5, eventsAdded);
        Assert.True(lateEventAdded);
    }

    [Fact]
    public async Task SortedSetExample_ScoreRanges_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var scoresKey = TestUtils.CreateKeyName();

        // Act - Add entries with various score ranges
        var entries = new SortedSetEntry[]
        {
            new("negative", -100.5),
            new("zero", 0.0),
            new("small_positive", 0.001),
            new("large_positive", 999999.99),
            new("very_large", double.MaxValue / 2), // Use half of max to avoid overflow
            new("decimal", 3.14159)
        };

        var entriesAdded = await client.SortedSetAddAsync(scoresKey, entries);

        // Assert
        Assert.Equal(6, entriesAdded);
    }

    [Fact]
    public async Task SortedSetExample_BinaryMembers_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var binaryKey = TestUtils.CreateKeyName();

        // Act - Add binary data as members
        var binaryEntries = new SortedSetEntry[]
        {
            new(new byte[] { 0x01, 0x02, 0x03 }, 1.0),
            new(new byte[] { 0xFF, 0xFE, 0xFD }, 2.0),
            new(new byte[] { 0x00 }, 0.5),
            new("mixed_with_string", 1.5)
        };

        var binaryEntriesAdded = await client.SortedSetAddAsync(binaryKey, binaryEntries);

        // Assert
        Assert.Equal(4, binaryEntriesAdded);
    }

    [Fact]
    public async Task SortedSetExample_DuplicateScores_Success()
    {
        // Arrange
        using var client = _fixture.CreateClient();
        var duplicatesKey = TestUtils.CreateKeyName();

        // Act - Add multiple members with the same score
        var sameScoreEntries = new SortedSetEntry[]
        {
            new("first", 1.0),
            new("second", 1.0),
            new("third", 1.0),
            new("fourth", 2.0),
            new("fifth", 1.0)
        };

        var sameScoreEntriesAdded = await client.SortedSetAddAsync(duplicatesKey, sameScoreEntries);

        // Assert
        Assert.Equal(5, sameScoreEntriesAdded);
    }
}
