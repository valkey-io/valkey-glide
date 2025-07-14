// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

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
        var entries = new SortedSetEntry[]
        {
            new("member1", 10.5),
            new("member2", 8.2),
            new("member3", 15.0)
        };

        // Test adding multiple new members
        Assert.Equal(3, await client.SortedSetAddAsync(key, entries));

        // Test adding mix of new and existing members
        var newEntries = new SortedSetEntry[]
        {
            new("member1", 20.0), // Update existing
            new("member4", 12.0)  // Add new
        };
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
        var initialEntries = new SortedSetEntry[]
        {
            new("member1", 10.0),
            new("member2", 8.0)
        };
        Assert.Equal(2, await client.SortedSetAddAsync(key, initialEntries));

        // Try to add with NX (should only add new members)
        var nxEntries = new SortedSetEntry[]
        {
            new("member1", 15.0), // Existing, should not update
            new("member3", 12.0)  // New, should add
        };
        Assert.Equal(1, await client.SortedSetAddAsync(key, nxEntries, SortedSetWhen.NotExists));

        // Update existing members with XX
        var xxEntries = new SortedSetEntry[]
        {
            new("member1", 20.0), // Existing, should update
            new("member4", 5.0)   // New, should not add
        };
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

        var entries = new SortedSetEntry[]
        {
            new("member3", -15.0),
            new("member4", 0.0)
        };
        Assert.Equal(2, await client.SortedSetAddAsync(key, entries));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_SpecialScores(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test with special double values that Redis/Valkey supports
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
        var emptyEntries = Array.Empty<SortedSetEntry>();

        // Adding empty array should return 0 without error
        Assert.Equal(0, await client.SortedSetAddAsync(key, emptyEntries));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSortedSetAdd_ObsoleteOverloads(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test obsolete overload with CommandFlags only
        Assert.True(await client.SortedSetAddAsync(key, "member1", 10.5, CommandFlags.None));

        // Test obsolete overload with When enum
        Assert.False(await client.SortedSetAddAsync(key, "member1", 15.0, When.Exists));

        // Test obsolete array overloads
        var entries = new SortedSetEntry[] { new("member2", 8.0) };
        Assert.Equal(1, await client.SortedSetAddAsync(key, entries, CommandFlags.None));
        Assert.Equal(0, await client.SortedSetAddAsync(key, entries, When.Exists));
    }
}
