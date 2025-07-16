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
        SortedSetEntry[] emptyEntries = Array.Empty<SortedSetEntry>();

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
        SortedSetEntry[] entries = [new("member2", 8.0)];
        Assert.Equal(1, await client.SortedSetAddAsync(key, entries, CommandFlags.None));
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

        // Test removing empty array
        Assert.Equal(0, await client.SortedSetRemoveAsync(key, []));

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
}
