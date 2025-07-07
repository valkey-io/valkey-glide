// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class ListCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestLPush_LPop(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        Assert.Equal(2, await client.ListLeftPushAsync(key, ["test1", "test2"]));
        Assert.Equal(3, await client.ListLeftPushAsync(key, ["test3"]));

        ValkeyValue lPopResult1 = await client.ListLeftPopAsync(key);
        Assert.Equal("test3", lPopResult1.ToGlideString());

        ValkeyValue lPopResult2 = await client.ListLeftPopAsync(key);
        Assert.Equal("test2", lPopResult2.ToGlideString());

        ValkeyValue lPopResult3 = await client.ListLeftPopAsync("non-exist-key");
        Assert.Equal(ValkeyValue.Null, lPopResult3);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestLPopWithCount(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        Assert.Equal(4, await client.ListLeftPushAsync(key, ["test1", "test2", "test3", "test4"]));

        ValkeyValue[] lPopResultWithCount = await client.ListLeftPopAsync(key, 2);
        Assert.Equal(["test4", "test3"], lPopResultWithCount.ToGlideStrings());

        ValkeyValue[] lPopResultWithCount2 = await client.ListLeftPopAsync(key, 10);
        Assert.Equal(["test2", "test1"], lPopResultWithCount2.ToGlideStrings());

        ValkeyValue[] lPopResultWithCount3 = await client.ListLeftPopAsync("non-exist-key", 10);
        Assert.Null(lPopResultWithCount3);
    }
}
