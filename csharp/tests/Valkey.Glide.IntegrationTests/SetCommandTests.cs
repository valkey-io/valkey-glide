// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class SetCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSAdd_SMembers(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        Assert.Equal(2, await client.SetAddAsync(key, ["test1", "test2"]));
        Assert.True(await client.SetAddAsync(key, "test3"));

        ValkeyValue[] vals = await client.SetMembersAsync(key);
        Assert.Equal(3, vals.Length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSAdd_numbers(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        Assert.True(await client.SetAddAsync(key, "1"));
        Assert.Equal(2, await client.SetAddAsync(key, ["2", "3"]));

        ValkeyValue[] vals = await client.SetMembersAsync(key);
        Assert.Equal(3, vals.Length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSRem(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        ValkeyValue[] members = ["member1", "member2", "member3"];

        Assert.Equal(3, await client.SetAddAsync(key, members));
        Assert.Equal(2, await client.SetRemoveAsync(key, ["member1", "member2"]));
        Assert.True(await client.SetRemoveAsync(key, "member3"));

        Assert.Equal(0, await client.SetLengthAsync(key));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetLengthAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        ValkeyValue[] members = ["member1", "member2", "member3"];

        // Test on non-existent key
        Assert.Equal(0, await client.SetLengthAsync(key));

        // Add members and test length
        Assert.Equal(3, await client.SetAddAsync(key, members));
        Assert.Equal(3, await client.SetLengthAsync(key));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetIntersectionLengthAsync(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "SetIntersectionLength is supported since 7.0.0"
        );
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();

        // Test with non-existent keys
        Assert.Equal(0, await client.SetIntersectionLengthAsync([key1, key2]));

        // Set up test data
        _ = await client.SetAddAsync(key1, ["a", "b", "c", "d"]);
        _ = await client.SetAddAsync(key2, ["b", "c", "e", "f"]);

        // Test intersection of two sets
        Assert.Equal(2, await client.SetIntersectionLengthAsync([key1, key2])); // "b", "c"

        // Test with limit
        Assert.Equal(1, await client.SetIntersectionLengthAsync([key1, key2], 1)); // Should stop at 1
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetPopAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        ValkeyValue[] members = ["member1", "member2", "member3", "member4", "member5"];

        // Test on non-existent key
        Assert.True((await client.SetPopAsync(key)).IsNull);

        // Add members to set
        Assert.Equal(5, await client.SetAddAsync(key, members));

        // Test single pop
        ValkeyValue poppedElement = await client.SetPopAsync(key);
        Assert.True(poppedElement.HasValue);

        // Verify the element was removed
        Assert.Equal(4, await client.SetLengthAsync(key));

        // Test multiple pop
        ValkeyValue[] poppedElements = await client.SetPopAsync(key, 2);
        Assert.Equal(2, poppedElements.Length);

        // Verify elements were removed
        Assert.Equal(2, await client.SetLengthAsync(key));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetUnionAsync(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();

        _ = await client.SetAddAsync(key1, ["a", "b"]);
        _ = await client.SetAddAsync(key2, ["b", "c"]);

        ValkeyValue[] result = await client.SetUnionAsync(key1, key2);
        Assert.Equal(3, result.Length); // a, b, c
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetIntersectAsync(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();

        _ = await client.SetAddAsync(key1, ["a", "b", "c"]);
        _ = await client.SetAddAsync(key2, ["b", "c", "d"]);

        ValkeyValue[] result = await client.SetIntersectAsync(key1, key2);
        Assert.Equal(2, result.Length); // b, c
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetDifferenceAsync(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();

        _ = await client.SetAddAsync(key1, ["a", "b", "c"]);
        _ = await client.SetAddAsync(key2, ["b", "c", "d"]);

        ValkeyValue[] result = await client.SetDifferenceAsync(key1, key2);
        ValkeyValue singleResult = Assert.Single(result); // a
        Assert.Equal("a", singleResult.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetUnionStoreAsync(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();
        string destKey = "{prefix}-" + Guid.NewGuid().ToString();

        _ = await client.SetAddAsync(key1, ["a", "b"]);
        _ = await client.SetAddAsync(key2, ["b", "c"]);

        long count = await client.SetUnionStoreAsync(destKey, key1, key2);
        Assert.Equal(3, count); // a, b, c
        Assert.Equal(3, await client.SetLengthAsync(destKey));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetIntersectStoreAsync(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();
        string destKey = "{prefix}-" + Guid.NewGuid().ToString();

        _ = await client.SetAddAsync(key1, ["a", "b", "c"]);
        _ = await client.SetAddAsync(key2, ["b", "c", "d"]);

        long count = await client.SetIntersectStoreAsync(destKey, key1, key2);
        Assert.Equal(2, count); // b, c
        Assert.Equal(2, await client.SetLengthAsync(destKey));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetDifferenceStoreAsync(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();
        string destKey = "{prefix}-" + Guid.NewGuid().ToString();

        _ = await client.SetAddAsync(key1, ["a", "b", "c"]);
        _ = await client.SetAddAsync(key2, ["b", "c", "d"]);

        long count = await client.SetDifferenceStoreAsync(destKey, key1, key2);
        Assert.Equal(1, count); // a
        Assert.Equal(1, await client.SetLengthAsync(destKey));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetContainsAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        ValkeyValue[] members = ["member1", "member2", "member3"];

        // Test on non-existent key
        Assert.False(await client.SetContainsAsync(key, "member1"));

        // Add members to set
        Assert.Equal(3, await client.SetAddAsync(key, members));

        // Test single member check
        Assert.True(await client.SetContainsAsync(key, "member1"));
        Assert.False(await client.SetContainsAsync(key, "nonexistent"));

        // Test multiple member check
        bool[] results = await client.SetContainsAsync(key, ["member1", "member2", "nonexistent"]);
        Assert.Equal([true, true, false], results);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetRandomMemberAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        ValkeyValue[] members = ["member1", "member2", "member3", "member4", "member5"];

        // Test on non-existent key
        Assert.Equal(ValkeyValue.Null, await client.SetRandomMemberAsync(key));

        // Add members to set
        Assert.Equal(5, await client.SetAddAsync(key, members));

        // Test single random member
        ValkeyValue randomMember = await client.SetRandomMemberAsync(key);
        Assert.True(randomMember.HasValue);
        Assert.Contains(randomMember.ToString(), members.Select(m => m.ToString()));

        // Test multiple random members (positive count - distinct)
        ValkeyValue[] randomMembers = await client.SetRandomMembersAsync(key, 3);
        Assert.Equal(3, randomMembers.Length);
        Assert.Equal(3, randomMembers.Distinct().Count()); // Should be distinct

        // Test multiple random members (negative count - allows duplicates)
        ValkeyValue[] randomMembersWithDuplicates = await client.SetRandomMembersAsync(key, -3);
        Assert.Equal(3, randomMembersWithDuplicates.Length);

        // Test count larger than set size
        ValkeyValue[] allMembers = await client.SetRandomMembersAsync(key, 10);
        Assert.Equal(5, allMembers.Length); // Should return all 5 members
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetMoveAsync(BaseClient client)
    {
        string sourceKey = "{prefix}-" + Guid.NewGuid().ToString();
        string destKey = "{prefix}-" + Guid.NewGuid().ToString();

        // Test move from non-existent source
        Assert.False(await client.SetMoveAsync(sourceKey, destKey, "member1"));

        // Set up test data
        _ = await client.SetAddAsync(sourceKey, ["member1", "member2", "member3"]);
        _ = await client.SetAddAsync(destKey, ["member4", "member5"]);

        // Test successful move
        Assert.True(await client.SetMoveAsync(sourceKey, destKey, "member1"));
        Assert.Equal(2, await client.SetLengthAsync(sourceKey)); // member1 removed
        Assert.Equal(3, await client.SetLengthAsync(destKey)); // member1 added

        // Verify member1 is in destination and not in source
        Assert.False(await client.SetContainsAsync(sourceKey, "member1"));
        Assert.True(await client.SetContainsAsync(destKey, "member1"));

        // Test move of non-existent member
        Assert.False(await client.SetMoveAsync(sourceKey, destKey, "nonexistent"));

        // Test move when member already exists in destination
        _ = await client.SetAddAsync(sourceKey, "member4"); // Add member4 to source
        Assert.True(await client.SetMoveAsync(sourceKey, destKey, "member4")); // Should still return true
        Assert.Equal(2, await client.SetLengthAsync(sourceKey)); // member4 removed from source
        Assert.Equal(3, await client.SetLengthAsync(destKey)); // destination size unchanged (member4 already existed)
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetScanAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        ValkeyValue[] members = ["member1", "member2", "test1", "test2", "item1", "item2"];

        // Test scan on non-existent key
        List<ValkeyValue> emptyResults = [];
        await foreach (var value in client.SetScanAsync(key))
        {
            emptyResults.Add(value);
        }
        Assert.Empty(emptyResults);

        // Add members to set
        Assert.Equal(6, await client.SetAddAsync(key, members));

        // Test scan all members
        List<ValkeyValue> allResults = [];
        await foreach (var value in client.SetScanAsync(key))
        {
            allResults.Add(value);
        }
        Assert.Equal(6, allResults.Count);
        Assert.True(members.All(m => allResults.Any(r => r.ToString() == m.ToString())));

        // Test scan with pattern
        List<ValkeyValue> patternResults = [];
        await foreach (var value in client.SetScanAsync(key, "test*"))
        {
            patternResults.Add(value);
        }
        Assert.Equal(2, patternResults.Count);
        Assert.All(patternResults, r => Assert.StartsWith("test", r.ToString()));

        // Test scan with small page size
        List<ValkeyValue> smallPageResults = [];
        await foreach (var value in client.SetScanAsync(key, pageSize: 2))
        {
            smallPageResults.Add(value);
        }
        Assert.Equal(6, smallPageResults.Count); // Should still get all results
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetScanAsync_LargeDataset(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Create 25000 members with mixed patterns
        ValkeyValue[] members = [.. Enumerable.Range(0, 25000).Select(i => (ValkeyValue)$"member{i}")];
        await client.SetAddAsync(key, members);

        // Test 1: Scan all members with default settings
        List<ValkeyValue> allScanned = [];
        await foreach (var member in client.SetScanAsync(key))
        {
            allScanned.Add(member);
        }
        Assert.Equal(25000, allScanned.Count);

        // Test 2: Scan with pattern matching (should find members 1000-1999)
        List<ValkeyValue> patternScanned = [];
        await foreach (var member in client.SetScanAsync(key, "member1*"))
        {
            Assert.StartsWith("member1", member);
            patternScanned.Add(member);
        }
        Assert.Equal(11111, patternScanned.Count);  // At least member1, member10-19, member100-199, etc.

        // Test 3: Scan with small page size to test pagination
        List<ValkeyValue> smallPageScanned = [];
        await foreach (var member in client.SetScanAsync(key, pageSize: 100))
        {
            smallPageScanned.Add(member);
        }
        Assert.Equal(25000, smallPageScanned.Count);

        // Test 4: Use pageOffset to skip first 500 results per pagination
        List<ValkeyValue> offsetResults = [];
        await foreach (var member in client.SetScanAsync(key, pageSize: 1000, pageOffset: 500))
        {
            offsetResults.Add(member);
        }
        Assert.Equal(12500, offsetResults.Count);

        Assert.Equal(25000, await client.SetLengthAsync(key));
    }
}
