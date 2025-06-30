// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class SharedCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    internal static async Task GetAndSetValues(BaseClient client, string key, string value)
    {
        Assert.Equal("OK", await client.Set(key, value));
        Assert.Equal(value, (await client.Get(key))!);
    }

    internal static async Task GetAndSetRandomValues(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsLastSet(BaseClient client) =>
        await GetAndSetRandomValues(client);

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetAndSetCanHandleNonASCIIUnicode(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "שלום hello 汉字";
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsNull(BaseClient client) =>
        Assert.Null(await client.Get(Guid.NewGuid().ToString()));

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsEmptyString(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = string.Empty;
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSAdd_SMembers(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        Assert.Equal(2, await client.SetAdd(key, new RedisValue[] { "test1", "test2" }));
        Assert.Equal(1, await client.SetAdd(key, new RedisValue[] { "test3" }));
        Assert.True(await client.SetAdd(key, "test4"));
        Assert.False(await client.SetAdd(key, "test4"));

        var vals = await client.SetMembers(key);
        string s = string.Join(",", vals.OrderByDescending(x => x));
        Assert.Equal("test4,test3,test2,test1", s);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSAdd_numbers(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        Assert.True(await client.SetAdd(key, "a", CommandFlags.FireAndForget));
        Assert.Equal(1, await client.SetAdd(key, new RedisValue[] { "1" }, CommandFlags.FireAndForget));
        Assert.Equal(2, await client.SetAdd(key, new RedisValue[] { "11", "2" }, CommandFlags.FireAndForget));
        Assert.Equal(3, await client.SetAdd(key, new RedisValue[] { "10", "3", "1.5" }, CommandFlags.FireAndForget));
        Assert.Equal(4, await client.SetAdd(key, new RedisValue[] { "2.2", "-1", "s", "t" }, CommandFlags.FireAndForget));

        var vals = await client.SetMembers(key);
        string s = string.Join(",", vals.OrderByDescending(x => x));
        Assert.Equal("t,s,a,11,10,3,2.2,2,1.5,1,-1", s);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSRem(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        RedisValue[] members = { "member1", "member2", "member3" };

        // Test regular remove and remove on non-existent members
        Assert.Equal(3, await client.SetAdd(key, members, CommandFlags.FireAndForget));
        Assert.Equal(2, await client.SetRemove(key, new RedisValue[] { "member1", "member2" }, CommandFlags.FireAndForget));
        Assert.Equal(0, await client.SetRemove(key, new RedisValue[] { "idontexist1", "idontexist2" }, CommandFlags.FireAndForget));

        // Test singular remove
        Assert.True(await client.SetRemove(key, "member3", CommandFlags.FireAndForget));
        Assert.False(await client.SetRemove(key, "member3", CommandFlags.FireAndForget));

        // Re-add members
        Assert.Equal(3, await client.SetAdd(key, members, CommandFlags.FireAndForget));

        // Mix existing and non-existing members
        Assert.Equal(1, await client.SetRemove(key, new RedisValue[] { "member1", "idontexist2" }, CommandFlags.FireAndForget));

        // Remove on non-existent key
        Assert.Equal(0, await client.SetRemove(key + "2", new RedisValue[] { "member2", "member3" }, CommandFlags.FireAndForget));

        // Check leftover members are expected
        var vals = await client.SetMembers(key);
        string s = string.Join(",", vals.OrderByDescending(x => x));
        Assert.Equal("member3,member2", s);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetLength(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        RedisValue[] members = { "member1", "member2", "member3" };

        // Test on non-existent key
        Assert.Equal(0, await client.SetLength(key));

        // Add members and test length
        Assert.Equal(3, await client.SetAdd(key, members));
        Assert.Equal(3, await client.SetLength(key));

        // Remove a member and test length
        Assert.True(await client.SetRemove(key, "member1"));
        Assert.Equal(2, await client.SetLength(key));

        // Remove all members
        Assert.Equal(2, await client.SetRemove(key, new RedisValue[] { "member2", "member3" }));
        Assert.Equal(0, await client.SetLength(key));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetIntersectionLength(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();
        string key3 = "{prefix}-" + Guid.NewGuid().ToString();
        string nonExistentKey = "{prefix}-" + Guid.NewGuid().ToString();

        // Test with non-existent keys
        Assert.Equal(0, await client.SetIntersectionLength(new RedisKey[] { key1, key2 }));

        // Set up test data
        await client.SetAdd(key1, new RedisValue[] { "a", "b", "c", "d" });
        await client.SetAdd(key2, new RedisValue[] { "b", "c", "e", "f" });
        await client.SetAdd(key3, new RedisValue[] { "c", "d", "g", "h" });

        // Test intersection of two sets
        Assert.Equal(2, await client.SetIntersectionLength([key1, key2])); // "b", "c"

        // Test intersection of three sets
        Assert.Equal(1, await client.SetIntersectionLength([key1, key2, key3])); // "c"

        // Test with one non-existent key
        Assert.Equal(0, await client.SetIntersectionLength([key1, nonExistentKey]));

        // Test with limit
        Assert.Equal(1, await client.SetIntersectionLength([key1, key2], 1)); // Should stop at 1

        // Test with limit higher than actual intersection
        Assert.Equal(2, await client.SetIntersectionLength([key1, key2], 5)); // Should return actual count (2)
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetPop(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        RedisValue[] members = { "member1", "member2", "member3", "member4", "member5" };

        // Test on non-existent key
        Assert.True((await client.SetPop(key)).IsNull);

        // Add members to set
        Assert.Equal(5, await client.SetAdd(key, members));

        // Test single pop
        var poppedElement = await client.SetPop(key);
        Assert.True(poppedElement.HasValue);
        Assert.Contains(poppedElement.ToString(), members.Select(m => m.ToString()));

        // Verify the element was removed
        Assert.Equal(4, await client.SetLength(key));

        // Test multiple pop
        var poppedElements = await client.SetPop(key, 2);
        Assert.Equal(2, poppedElements.Length);

        // Verify all popped elements were in the original set
        foreach (var element in poppedElements)
        {
            Assert.Contains(element.ToString(), members.Select(m => m.ToString()));
        }

        // Verify elements were removed
        Assert.Equal(2, await client.SetLength(key));

        // Test pop more than available
        var remainingElements = await client.SetPop(key, 10);
        Assert.Equal(2, remainingElements.Length); // Should only return the 2 remaining elements

        // Verify set is now empty
        Assert.Equal(0, await client.SetLength(key));

        // Test pop from empty set
        Assert.Equal(RedisValue.Null, await client.SetPop(key));
        var emptyPop = await client.SetPop(key, 3);
        Assert.Empty(emptyPop);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetCombine(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();
        string key3 = "{prefix}-" + Guid.NewGuid().ToString();

        // Set up test data
        await client.SetAdd(key1, new RedisValue[] { "a", "b", "c" });
        await client.SetAdd(key2, new RedisValue[] { "b", "c", "d" });
        await client.SetAdd(key3, new RedisValue[] { "c", "d", "e" });

        // Test Union
        var unionResult = await client.SetCombine(SetOperation.Union, key1, key2);
        Assert.Equal(4, unionResult.Length); // a, b, c, d
        var unionSet = unionResult.Select(v => v.ToString()).ToHashSet();
        Assert.Contains("a", unionSet);
        Assert.Contains("b", unionSet);
        Assert.Contains("c", unionSet);
        Assert.Contains("d", unionSet);

        // Test Union with multiple keys
        var unionMultiResult = await client.SetCombine(SetOperation.Union, [key1, key2, key3]);
        Assert.Equal(5, unionMultiResult.Length); // a, b, c, d, e
        var unionMultiSet = unionMultiResult.Select(v => v.ToString()).ToHashSet();
        Assert.Contains("a", unionMultiSet);
        Assert.Contains("b", unionMultiSet);
        Assert.Contains("c", unionMultiSet);
        Assert.Contains("d", unionMultiSet);
        Assert.Contains("e", unionMultiSet);

        // Test Intersection
        var intersectResult = await client.SetCombine(SetOperation.Intersect, key1, key2);
        Assert.Equal(2, intersectResult.Length); // b, c
        var intersectSet = intersectResult.Select(v => v.ToString()).ToHashSet();
        Assert.Contains("b", intersectSet);
        Assert.Contains("c", intersectSet);

        // Test Intersection with multiple keys
        var intersectMultiResult = await client.SetCombine(SetOperation.Intersect, [key1, key2, key3]);
        Assert.Equal(1, intersectMultiResult.Length); // c
        Assert.Equal("c", intersectMultiResult[0].ToString());

        // Test Difference
        var diffResult = await client.SetCombine(SetOperation.Difference, key1, key2);
        Assert.Equal(1, diffResult.Length); // a
        Assert.Equal("a", diffResult[0].ToString());

        // Test with non-existent key
        string nonExistentKey = "{prefix}-" + Guid.NewGuid().ToString();
        var emptyResult = await client.SetCombine(SetOperation.Union, key1, nonExistentKey);
        Assert.Equal(3, emptyResult.Length); // Same as key1: a, b, c
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetCombineAndStore(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();
        string key3 = "{prefix}-" + Guid.NewGuid().ToString();
        string destKey = "{prefix}-" + Guid.NewGuid().ToString();

        // Set up test data
        await client.SetAdd(key1, new RedisValue[] { "a", "b", "c" });
        await client.SetAdd(key2, new RedisValue[] { "b", "c", "d" });
        await client.SetAdd(key3, new RedisValue[] { "c", "d", "e" });

        // Test Union and Store
        var unionCount = await client.SetCombineAndStore(SetOperation.Union, destKey, key1, key2);
        Assert.Equal(4, unionCount); // a, b, c, d
        var storedMembers = await client.SetMembers(destKey);
        Assert.Equal(4, storedMembers.Length);
        var storedSet = storedMembers.Select(v => v.ToString()).ToHashSet();
        Assert.Contains("a", storedSet);
        Assert.Contains("b", storedSet);
        Assert.Contains("c", storedSet);
        Assert.Contains("d", storedSet);

        // Test Union and Store with multiple keys
        string destKey2 = "{prefix}-" + Guid.NewGuid().ToString();
        var unionMultiCount = await client.SetCombineAndStore(SetOperation.Union, destKey2, [key1, key2, key3]);
        Assert.Equal(5, unionMultiCount); // a, b, c, d, e
        Assert.Equal(5, await client.SetLength(destKey2));

        // Test Intersection and Store
        string destKey3 = "{prefix}-" + Guid.NewGuid().ToString();
        var intersectCount = await client.SetCombineAndStore(SetOperation.Intersect, destKey3, key1, key2);
        Assert.Equal(2, intersectCount); // b, c
        var intersectMembers = await client.SetMembers(destKey3);
        var intersectSet = intersectMembers.Select(v => v.ToString()).ToHashSet();
        Assert.Contains("b", intersectSet);
        Assert.Contains("c", intersectSet);

        // Test Difference and Store
        string destKey4 = "{prefix}-" + Guid.NewGuid().ToString();
        var diffCount = await client.SetCombineAndStore(SetOperation.Difference, destKey4, key1, key2);
        Assert.Equal(1, diffCount); // a
        var diffMembers = await client.SetMembers(destKey4);
        Assert.Equal(1, diffMembers.Length);
        Assert.Equal("a", diffMembers[0].ToString());

        // Test overwriting existing destination
        var overwriteCount = await client.SetCombineAndStore(SetOperation.Intersect, destKey4, key2, key3);
        Assert.Equal(2, overwriteCount); // c, d (overwrites previous content)
        Assert.Equal(2, await client.SetLength(destKey4));
    }
}
