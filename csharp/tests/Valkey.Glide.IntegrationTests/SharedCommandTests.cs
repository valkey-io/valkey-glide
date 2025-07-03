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

        Assert.Equal(2, await client.SetAddAsync(key, new ValkeyValue[] { "test1", "test2" }));
        Assert.True(await client.SetAddAsync(key, "test3"));

        var vals = await client.SetMembersAsync(key);
        Assert.Equal(3, vals.Length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSAdd_numbers(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        Assert.True(await client.SetAddAsync(key, "1"));
        Assert.Equal(2, await client.SetAddAsync(key, new ValkeyValue[] { "2", "3" }));

        var vals = await client.SetMembersAsync(key);
        Assert.Equal(3, vals.Length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSRem(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        ValkeyValue[] members = { "member1", "member2", "member3" };

        Assert.Equal(3, await client.SetAddAsync(key, members));
        Assert.Equal(2, await client.SetRemoveAsync(key, new ValkeyValue[] { "member1", "member2" }));
        Assert.True(await client.SetRemoveAsync(key, "member3"));

        Assert.Equal(0, await client.SetLengthAsync(key));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetLengthAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        ValkeyValue[] members = { "member1", "member2", "member3" };

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
            TestConfiguration.SERVER_VERSION.CompareTo(new Version("7.0.0")) < 0,
            "SetIntersectionLength is supported since 7.0.0"
        );
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();

        // Test with non-existent keys
        Assert.Equal(0, await client.SetIntersectionLengthAsync(new ValkeyKey[] { key1, key2 }));

        // Set up test data
        await client.SetAddAsync(key1, new ValkeyValue[] { "a", "b", "c", "d" });
        await client.SetAddAsync(key2, new ValkeyValue[] { "b", "c", "e", "f" });

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
        ValkeyValue[] members = { "member1", "member2", "member3", "member4", "member5" };

        // Test on non-existent key
        Assert.True((await client.SetPopAsync(key)).IsNull);

        // Add members to set
        Assert.Equal(5, await client.SetAddAsync(key, members));

        // Test single pop
        var poppedElement = await client.SetPopAsync(key);
        Assert.True(poppedElement.HasValue);

        // Verify the element was removed
        Assert.Equal(4, await client.SetLengthAsync(key));

        // Test multiple pop
        var poppedElements = await client.SetPopAsync(key, 2);
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

        await client.SetAddAsync(key1, ["a", "b"]);
        await client.SetAddAsync(key2, ["b", "c"]);

        var result = await client.SetUnionAsync(key1, key2);
        Assert.Equal(3, result.Length); // a, b, c
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetIntersectAsync(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();

        await client.SetAddAsync(key1, ["a", "b", "c"]);
        await client.SetAddAsync(key2, ["b", "c", "d"]);

        var result = await client.SetIntersectAsync(key1, key2);
        Assert.Equal(2, result.Length); // b, c
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetDifferenceAsync(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();

        await client.SetAddAsync(key1, ["a", "b", "c"]);
        await client.SetAddAsync(key2, ["b", "c", "d"]);

        var result = await client.SetDifferenceAsync(key1, key2);
        Assert.Equal(1, result.Length); // a
        Assert.Equal("a", result[0].ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestSetUnionStoreAsync(BaseClient client)
    {
        string key1 = "{prefix}-" + Guid.NewGuid().ToString();
        string key2 = "{prefix}-" + Guid.NewGuid().ToString();
        string destKey = "{prefix}-" + Guid.NewGuid().ToString();

        await client.SetAddAsync(key1, ["a", "b"]);
        await client.SetAddAsync(key2, ["b", "c"]);

        var count = await client.SetUnionStoreAsync(destKey, key1, key2);
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

        await client.SetAddAsync(key1, ["a", "b", "c"]);
        await client.SetAddAsync(key2, ["b", "c", "d"]);

        var count = await client.SetIntersectStoreAsync(destKey, key1, key2);
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

        await client.SetAddAsync(key1, ["a", "b", "c"]);
        await client.SetAddAsync(key2, ["b", "c", "d"]);

        var count = await client.SetDifferenceStoreAsync(destKey, key1, key2);
        Assert.Equal(1, count); // a
        Assert.Equal(1, await client.SetLengthAsync(destKey));
    }
}
