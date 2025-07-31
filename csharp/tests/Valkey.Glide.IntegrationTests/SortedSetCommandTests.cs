// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Errors;

namespace Valkey.Glide.IntegrationTests;

public class SortedSetCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_SingleMember(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test adding a new member
        Assert.True(await client.SortedSetAddAsync(key, "member1", 10.5));

        // Test updating existing member (should return false)
        Assert.False(await client.SortedSetAddAsync(key, "member1", 15.0));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_MultipleMembers(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        SortedSetEntry[] entries = [
            new("member1", 10.5),
            new("member2", 8.2),
            new("member3", 15.0)
        ];

        // Test adding multiple new members
        Assert.Equal(3, await client.SortedSetAddAsync(key, entries));

        // Test adding mix of new and existing members
        SortedSetEntry[] newEntries = [
            new("member1", 20.0), // Update existing
            new("member4", 12.0)  // Add new
        ];
        Assert.Equal(1, await client.SortedSetAddAsync(key, newEntries)); // Only member4 is new
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_WithNotExists(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Add initial member
        Assert.True(await client.SortedSetAddAsync(key, "member1", 10.5));

        // Try to add existing member with NX (should fail)
        Assert.False(await client.SortedSetAddAsync(key, "member1", 15.0, SortedSetWhen.NotExists));

        // Add new member with NX (should succeed)
        Assert.True(await client.SortedSetAddAsync(key, "member2", 8.0, SortedSetWhen.NotExists));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_WithExists(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Try to update non-existing member with XX (should fail)
        Assert.False(await client.SortedSetAddAsync(key, "member1", 10.5, SortedSetWhen.Exists));

        // Add member normally first
        Assert.True(await client.SortedSetAddAsync(key, "member1", 10.5));

        // Update existing member with XX (should succeed)
        Assert.False(await client.SortedSetAddAsync(key, "member1", 15.0, SortedSetWhen.Exists));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_WithGreaterThan(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Add initial member
        Assert.True(await client.SortedSetAddAsync(key, "member1", 10.0));

        // Update with higher score using GT (should succeed)
        Assert.False(await client.SortedSetAddAsync(key, "member1", 15.0, SortedSetWhen.GreaterThan));

        // Try to update with lower score using GT (should fail)
        Assert.False(await client.SortedSetAddAsync(key, "member1", 5.0, SortedSetWhen.GreaterThan));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_WithLessThan(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Add initial member
        Assert.True(await client.SortedSetAddAsync(key, "member1", 10.0));

        // Update with lower score using LT (should succeed)
        Assert.False(await client.SortedSetAddAsync(key, "member1", 5.0, SortedSetWhen.LessThan));

        // Try to update with higher score using LT (should fail)
        Assert.False(await client.SortedSetAddAsync(key, "member1", 15.0, SortedSetWhen.LessThan));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_MultipleWithConditions(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Add initial members
        SortedSetEntry[] initialEntries = [
            new("member1", 10.0),
            new("member2", 8.0)
        ];
        Assert.Equal(2, await client.SortedSetAddAsync(key, initialEntries));

        // Try to add with NX (should only add new members)
        SortedSetEntry[] nxEntries = [
            new("member1", 15.0), // Existing, should not update
            new("member3", 12.0)  // New, should add
        ];
        Assert.Equal(1, await client.SortedSetAddAsync(key, nxEntries, SortedSetWhen.NotExists));

        // Update existing members with XX
        SortedSetEntry[] xxEntries = [
            new("member1", 20.0), // Existing, should update
            new("member4", 5.0)   // New, should not add
        ];
        Assert.Equal(0, await client.SortedSetAddAsync(key, xxEntries, SortedSetWhen.Exists));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_NegativeScores(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test with negative scores
        Assert.True(await client.SortedSetAddAsync(key, "member1", -10.5));
        Assert.True(await client.SortedSetAddAsync(key, "member2", -5.0));

        SortedSetEntry[] entries = [
            new("member3", -15.0),
            new("member4", 0.0)
        ];
        Assert.Equal(2, await client.SortedSetAddAsync(key, entries));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_SpecialScores(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test with special double values that server supports
        Assert.True(await client.SortedSetAddAsync(key, "inf", double.PositiveInfinity));
        Assert.True(await client.SortedSetAddAsync(key, "neginf", double.NegativeInfinity));
        Assert.True(await client.SortedSetAddAsync(key, "zero", 0.0));

        // Test with very large/small values (but not Min/Max which might not be supported)
        Assert.True(await client.SortedSetAddAsync(key, "large", 1e100));
        Assert.True(await client.SortedSetAddAsync(key, "small", -1e100));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_EmptyArray(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Adding empty array should throw an exception
        await Assert.ThrowsAsync<RequestException>(async () => await client.SortedSetAddAsync(key, []));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_ObsoleteOverloads(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test overload with default parameters
        Assert.True(await client.SortedSetAddAsync(key, "member1", 10.5));

        // Test obsolete overload with When enum
        Assert.False(await client.SortedSetAddAsync(key, "member1", 15.0, When.Exists));

        // Test array overloads
        SortedSetEntry[] entries = [new("member2", 8.0)];
        Assert.Equal(1, await client.SortedSetAddAsync(key, entries));
        Assert.Equal(0, await client.SortedSetAddAsync(key, entries, When.Exists));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetRemove_SingleMember(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test removing from non-existent key
        Assert.False(await client.SortedSetRemoveAsync(key, "member1"));

        // Add members first
        Assert.True(await client.SortedSetAddAsync(key, "member1", 10.5));
        Assert.True(await client.SortedSetAddAsync(key, "member2", 8.2));

        // Test removing existing member
        Assert.True(await client.SortedSetRemoveAsync(key, "member1"));

        // Test removing already removed member
        Assert.False(await client.SortedSetRemoveAsync(key, "member1"));

        // Test removing non-existent member
        Assert.False(await client.SortedSetRemoveAsync(key, "nonexistent"));

        // Verify remaining member still exists
        Assert.True(await client.SortedSetRemoveAsync(key, "member2"));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetRemove_MultipleMembers(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test removing from non-existent key
        Assert.Equal(0, await client.SortedSetRemoveAsync(key, ["member1", "member2"]));

        // Add members first
        SortedSetEntry[] entries = [
            new("member1", 10.5),
            new("member2", 8.2),
            new("member3", 15.0),
            new("member4", 12.0)
        ];
        Assert.Equal(4, await client.SortedSetAddAsync(key, entries));

        // Test removing multiple existing members
        Assert.Equal(2, await client.SortedSetRemoveAsync(key, ["member1", "member3"]));

        // Test removing mix of existing and non-existing members
        Assert.Equal(1, await client.SortedSetRemoveAsync(key, ["member2", "nonexistent", "member5"]));

        // Test removing already removed members
        Assert.Equal(0, await client.SortedSetRemoveAsync(key, ["member1", "member2"]));

        // Verify only member4 remains
        Assert.Equal(1, await client.SortedSetRemoveAsync(key, ["member4"]));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetRemove_EmptyArray(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Add some members first
        Assert.True(await client.SortedSetAddAsync(key, "member1", 10.5));

        // Test removing empty array should throw an exception
        await Assert.ThrowsAsync<RequestException>(async () => await client.SortedSetRemoveAsync(key, []));

        // Verify member still exists
        Assert.True(await client.SortedSetRemoveAsync(key, "member1"));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetRemove_DuplicateMembers(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Add members first
        Assert.True(await client.SortedSetAddAsync(key, "member1", 10.5));
        Assert.True(await client.SortedSetAddAsync(key, "member2", 8.2));

        // Test removing with duplicate member names in array
        ValkeyValue[] membersWithDuplicates = ["member1", "member1", "member2", "member1"];
        Assert.Equal(2, await client.SortedSetRemoveAsync(key, membersWithDuplicates));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetRemove_SpecialValues(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test with special string values
        ValkeyValue[] specialMembers = ["", " ", "null", "0", "-1", "true", "false"];

        // Add special members with various scores
        for (int i = 0; i < specialMembers.Length; i++)
        {
            Assert.True(await client.SortedSetAddAsync(key, specialMembers[i], i * 1.5));
        }

        // Remove some special members
        Assert.Equal(3, await client.SortedSetRemoveAsync(key, ["", "null", "false"]));

        // Remove remaining members one by one
        Assert.True(await client.SortedSetRemoveAsync(key, " "));
        Assert.True(await client.SortedSetRemoveAsync(key, "0"));
        Assert.True(await client.SortedSetRemoveAsync(key, "-1"));
        Assert.True(await client.SortedSetRemoveAsync(key, "true"));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetLengthAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test on non-existent key
        Assert.Equal(0, await client.SortedSetLengthAsync(key));
        Assert.Equal(0, await client.SortedSetLengthAsync(key, 1.0, 10.0));

        // Add members with different scores
        Assert.True(await client.SortedSetAddAsync(key, "member1", 1.0));
        Assert.True(await client.SortedSetAddAsync(key, "member2", 2.5));
        Assert.True(await client.SortedSetAddAsync(key, "member3", 5.0));
        Assert.True(await client.SortedSetAddAsync(key, "member4", 8.0));

        // Test cardinality (default infinity parameters use ZCARD)
        Assert.Equal(4, await client.SortedSetLengthAsync(key));
        Assert.Equal(4, await client.SortedSetLengthAsync(key, double.NegativeInfinity, double.PositiveInfinity));

        // Test count with range parameters (uses ZCOUNT)
        Assert.Equal(2, await client.SortedSetLengthAsync(key, 2.0, 6.0));
        Assert.Equal(1, await client.SortedSetLengthAsync(key, 2.5, 5.0, Exclude.Start));
        Assert.Equal(1, await client.SortedSetLengthAsync(key, 2.5, 5.0, Exclude.Stop));
        Assert.Equal(0, await client.SortedSetLengthAsync(key, 2.5, 5.0, Exclude.Both));

        // Test with no matches
        Assert.Equal(0, await client.SortedSetLengthAsync(key, 15.0, 20.0));

        // Remove a member and test both modes
        Assert.True(await client.SortedSetRemoveAsync(key, "member2"));
        Assert.Equal(3, await client.SortedSetLengthAsync(key));
        Assert.Equal(1, await client.SortedSetLengthAsync(key, 2.0, 6.0));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetCardAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test on non-existent key
        Assert.Equal(0, await client.SortedSetCardAsync(key));

        // Add members and test cardinality
        Assert.True(await client.SortedSetAddAsync(key, "member1", 1.0));
        Assert.True(await client.SortedSetAddAsync(key, "member2", 2.0));
        Assert.True(await client.SortedSetAddAsync(key, "member3", 3.0));

        Assert.Equal(3, await client.SortedSetCardAsync(key));

        // Remove a member and test cardinality
        Assert.True(await client.SortedSetRemoveAsync(key, "member2"));
        Assert.Equal(2, await client.SortedSetCardAsync(key));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetCountAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test on non-existent key
        Assert.Equal(0, await client.SortedSetCountAsync(key));

        // Add members with different scores
        Assert.True(await client.SortedSetAddAsync(key, "member1", 1.0));
        Assert.True(await client.SortedSetAddAsync(key, "member2", 2.5));
        Assert.True(await client.SortedSetAddAsync(key, "member3", 5.0));
        Assert.True(await client.SortedSetAddAsync(key, "member4", 10.0));

        // Test count with default range (all elements)
        Assert.Equal(4, await client.SortedSetCountAsync(key));

        // Test count with specific range
        Assert.Equal(2, await client.SortedSetCountAsync(key, 2.0, 6.0));

        // Test count with exclusive bounds
        Assert.Equal(1, await client.SortedSetCountAsync(key, 2.5, 5.0, Exclude.Start));  // Exclude member2 (2.5), include member3 (5.0)
        Assert.Equal(1, await client.SortedSetCountAsync(key, 2.5, 5.0, Exclude.Stop));   // Include member2 (2.5), exclude member3 (5.0)
        Assert.Equal(0, await client.SortedSetCountAsync(key, 2.5, 5.0, Exclude.Both));   // Exclude both member2 and member3

        // Test count with infinity bounds
        Assert.Equal(4, await client.SortedSetCountAsync(key, double.NegativeInfinity, double.PositiveInfinity));

        // Test count with no matches
        Assert.Equal(0, await client.SortedSetCountAsync(key, 15.0, 20.0));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetRangeByRankAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test on non-existent key
        ValkeyValue[] result = await client.SortedSetRangeByRankAsync(key);
        Assert.Empty(result);

        // Add members with scores
        Assert.True(await client.SortedSetAddAsync(key, "member1", 1.0));
        Assert.True(await client.SortedSetAddAsync(key, "member2", 2.0));
        Assert.True(await client.SortedSetAddAsync(key, "member3", 3.0));
        Assert.True(await client.SortedSetAddAsync(key, "member4", 4.0));

        // Test default range (all elements, ascending)
        result = await client.SortedSetRangeByRankAsync(key);
        Assert.Equal(4, result.Length);
        Assert.Equal("member1", result[0]);
        Assert.Equal("member2", result[1]);
        Assert.Equal("member3", result[2]);
        Assert.Equal("member4", result[3]);

        // Test specific range
        result = await client.SortedSetRangeByRankAsync(key, 1, 2);
        Assert.Equal(2, result.Length);
        Assert.Equal("member2", result[0]);
        Assert.Equal("member3", result[1]);

        // Test descending order
        result = await client.SortedSetRangeByRankAsync(key, 0, 1, Order.Descending);
        Assert.Equal(2, result.Length);
        Assert.Equal("member4", result[0]);
        Assert.Equal("member3", result[1]);

        // Test negative indices
        result = await client.SortedSetRangeByRankAsync(key, -2, -1);
        Assert.Equal(2, result.Length);
        Assert.Equal("member3", result[0]);
        Assert.Equal("member4", result[1]);

        // Test single element range
        result = await client.SortedSetRangeByRankAsync(key, 0, 0);
        Assert.Single(result);
        Assert.Equal("member1", result[0]);

        // Test out of range
        result = await client.SortedSetRangeByRankAsync(key, 10, 20);
        Assert.Empty(result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetRangeByRankWithScoresAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test on non-existent key
        SortedSetEntry[] result = await client.SortedSetRangeByRankWithScoresAsync(key);
        Assert.Empty(result);

        // Add members with scores
        Assert.True(await client.SortedSetAddAsync(key, "member1", 1.5));
        Assert.True(await client.SortedSetAddAsync(key, "member2", 2.5));
        Assert.True(await client.SortedSetAddAsync(key, "member3", 3.5));

        // Test default range (all elements, ascending)
        result = await client.SortedSetRangeByRankWithScoresAsync(key);
        Assert.Equal(3, result.Length);
        Assert.Equal("member1", result[0].Element);
        Assert.Equal(1.5, result[0].Score);
        Assert.Equal("member2", result[1].Element);
        Assert.Equal(2.5, result[1].Score);
        Assert.Equal("member3", result[2].Element);
        Assert.Equal(3.5, result[2].Score);

        // Test specific range
        result = await client.SortedSetRangeByRankWithScoresAsync(key, 0, 1);
        Assert.Equal(2, result.Length);
        Assert.Equal("member1", result[0].Element);
        Assert.Equal(1.5, result[0].Score);
        Assert.Equal("member2", result[1].Element);
        Assert.Equal(2.5, result[1].Score);

        // Test descending order
        result = await client.SortedSetRangeByRankWithScoresAsync(key, 0, 1, Order.Descending);
        Assert.Equal(2, result.Length);
        Assert.Equal("member3", result[0].Element);
        Assert.Equal(3.5, result[0].Score);
        Assert.Equal("member2", result[1].Element);
        Assert.Equal(2.5, result[1].Score);

        // Test single element
        result = await client.SortedSetRangeByRankWithScoresAsync(key, 1, 1);
        Assert.Single(result);
        Assert.Equal("member2", result[0].Element);
        Assert.Equal(2.5, result[0].Score);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetRangeByRank_SpecialScores(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Add members with special scores
        Assert.True(await client.SortedSetAddAsync(key, "neginf", double.NegativeInfinity));
        Assert.True(await client.SortedSetAddAsync(key, "zero", 0.0));
        Assert.True(await client.SortedSetAddAsync(key, "posinf", double.PositiveInfinity));

        // Test range with special scores
        ValkeyValue[] result = await client.SortedSetRangeByRankAsync(key);
        Assert.Equal(3, result.Length);
        Assert.Equal("neginf", result[0]);
        Assert.Equal("zero", result[1]);
        Assert.Equal("posinf", result[2]);

        // Test with scores
        SortedSetEntry[] resultWithScores = await client.SortedSetRangeByRankWithScoresAsync(key);
        Assert.Equal(3, resultWithScores.Length);
        Assert.Equal("neginf", resultWithScores[0].Element);
        Assert.True(double.IsNegativeInfinity(resultWithScores[0].Score));
        Assert.Equal("zero", resultWithScores[1].Element);
        Assert.Equal(0.0, resultWithScores[1].Score);
        Assert.Equal("posinf", resultWithScores[2].Element);
        Assert.True(double.IsPositiveInfinity(resultWithScores[2].Score));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetRangeByScoreWithScoresAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test on non-existent key
        SortedSetEntry[] result = await client.SortedSetRangeByScoreWithScoresAsync(key);
        Assert.Empty(result);

        // Add members with scores
        Assert.True(await client.SortedSetAddAsync(key, "member1", 1.0));
        Assert.True(await client.SortedSetAddAsync(key, "member2", 2.5));
        Assert.True(await client.SortedSetAddAsync(key, "member3", 5.0));
        Assert.True(await client.SortedSetAddAsync(key, "member4", 10.0));

        // Test default range (all elements, ascending)
        result = await client.SortedSetRangeByScoreWithScoresAsync(key);
        Assert.Equal(4, result.Length);
        Assert.Equal("member1", result[0].Element);
        Assert.Equal(1.0, result[0].Score);
        Assert.Equal("member2", result[1].Element);
        Assert.Equal(2.5, result[1].Score);
        Assert.Equal("member3", result[2].Element);
        Assert.Equal(5.0, result[2].Score);
        Assert.Equal("member4", result[3].Element);
        Assert.Equal(10.0, result[3].Score);

        // Test specific score range
        result = await client.SortedSetRangeByScoreWithScoresAsync(key, 2.0, 6.0);
        Assert.Equal(2, result.Length);
        Assert.Equal("member2", result[0].Element);
        Assert.Equal(2.5, result[0].Score);
        Assert.Equal("member3", result[1].Element);
        Assert.Equal(5.0, result[1].Score);

        // Test descending order
        result = await client.SortedSetRangeByScoreWithScoresAsync(key, 2.0, 6.0, order: Order.Descending);
        Assert.Equal(2, result.Length);
        Assert.Equal("member3", result[0].Element);
        Assert.Equal(5.0, result[0].Score);
        Assert.Equal("member2", result[1].Element);
        Assert.Equal(2.5, result[1].Score);

        // Test with exclusions
        result = await client.SortedSetRangeByScoreWithScoresAsync(key, 2.5, 5.0, Exclude.Start);
        Assert.Single(result);
        Assert.Equal("member3", result[0].Element);
        Assert.Equal(5.0, result[0].Score);

        // Test with limit
        result = await client.SortedSetRangeByScoreWithScoresAsync(key, double.NegativeInfinity, double.PositiveInfinity, skip: 1, take: 2);
        Assert.Equal(2, result.Length);
        Assert.Equal("member2", result[0].Element);
        Assert.Equal(2.5, result[0].Score);
        Assert.Equal("member3", result[1].Element);
        Assert.Equal(5.0, result[1].Score);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetRangeByValueAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test on non-existent key
        ValkeyValue[] result = await client.SortedSetRangeByValueAsync(key, "a", "z", Exclude.None, Order.Ascending, 0, -1);
        Assert.Empty(result);

        // Add members with same score for lexicographical ordering
        Assert.True(await client.SortedSetAddAsync(key, "apple", 0.0));
        Assert.True(await client.SortedSetAddAsync(key, "banana", 0.0));
        Assert.True(await client.SortedSetAddAsync(key, "cherry", 0.0));
        Assert.True(await client.SortedSetAddAsync(key, "date", 0.0));

        // Test specific range
        result = await client.SortedSetRangeByValueAsync(key, "b", "d", Exclude.None, Order.Ascending, 0, -1);
        Assert.Equal(2, result.Length);
        Assert.Equal("banana", result[0]);
        Assert.Equal("cherry", result[1]);

        // Test with exclusions
        result = await client.SortedSetRangeByValueAsync(key, "banana", "cherry", Exclude.Start, Order.Ascending, 0, -1);
        Assert.Single(result);
        Assert.Equal("cherry", result[0]);

        // Test with limit
        result = await client.SortedSetRangeByValueAsync(key, "a", "z", Exclude.None, Order.Ascending, 1, 2);
        Assert.Equal(2, result.Length);
        Assert.Equal("banana", result[0]);
        Assert.Equal("cherry", result[1]);

        // Test full range
        result = await client.SortedSetRangeByValueAsync(key, double.NegativeInfinity, double.PositiveInfinity, Exclude.None, Order.Ascending, 0, -1);
        Assert.Equal(4, result.Length);
        Assert.Equal("apple", result[0]);
        Assert.Equal("banana", result[1]);
        Assert.Equal("cherry", result[2]);
        Assert.Equal("date", result[3]);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetRangeByValueWithOrderAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test on non-existent key
        ValkeyValue[] result = await client.SortedSetRangeByValueAsync(key, order: Order.Descending);
        Assert.Empty(result);

        // Add members with same score for lexicographical ordering
        Assert.True(await client.SortedSetAddAsync(key, "apple", 0.0));
        Assert.True(await client.SortedSetAddAsync(key, "banana", 0.0));
        Assert.True(await client.SortedSetAddAsync(key, "cherry", 0.0));
        Assert.True(await client.SortedSetAddAsync(key, "date", 0.0));

        // Test ascending order (default)
        result = await client.SortedSetRangeByValueAsync(key, order: Order.Ascending);
        Assert.Equal(4, result.Length);
        Assert.Equal("apple", result[0]);
        Assert.Equal("banana", result[1]);
        Assert.Equal("cherry", result[2]);
        Assert.Equal("date", result[3]);

        // Test descending order
        result = await client.SortedSetRangeByValueAsync(key, order: Order.Descending);
        Assert.Equal(4, result.Length);
        Assert.Equal("date", result[0]);
        Assert.Equal("cherry", result[1]);
        Assert.Equal("banana", result[2]);
        Assert.Equal("apple", result[3]);

        // Test specific range with descending order
        result = await client.SortedSetRangeByValueAsync(key, "b", "d", order: Order.Descending);
        Assert.Equal(2, result.Length);
        Assert.Equal("cherry", result[0]);
        Assert.Equal("banana", result[1]);

        // Test with limit and descending order
        result = await client.SortedSetRangeByValueAsync(key, order: Order.Descending, skip: 1, take: 2);
        Assert.Equal(2, result.Length);
        Assert.Equal("cherry", result[0]);
        Assert.Equal("banana", result[1]);
    }
}
