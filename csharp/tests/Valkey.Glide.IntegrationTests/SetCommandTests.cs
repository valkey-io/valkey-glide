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
}
