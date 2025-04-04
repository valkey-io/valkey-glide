// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class SharedCommandTests
{
    public SharedCommandTests(TestConfiguration config)
    {
        Config = config;
    }

    public TestConfiguration Config { get; }

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
#if NET8_0_OR_GREATER
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
#else
    [MemberData("TestClients", MemberType = typeof(TestConfiguration))]
#endif
    public async Task GetReturnsLastSet(BaseClient client) =>
        await GetAndSetRandomValues(client);

    [Theory(DisableDiscoveryEnumeration = true)]
#if NET8_0_OR_GREATER
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
#else
    [MemberData("TestClients", MemberType = typeof(TestConfiguration))]
#endif
    public async Task GetAndSetCanHandleNonASCIIUnicode(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "שלום hello 汉字";
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
#if NET8_0_OR_GREATER
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
#else
    [MemberData("TestClients", MemberType = typeof(TestConfiguration))]
#endif
    public async Task GetReturnsNull(BaseClient client) =>
        Assert.Null(await client.Get(Guid.NewGuid().ToString()));

    [Theory(DisableDiscoveryEnumeration = true)]
#if NET8_0_OR_GREATER
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
#else
    [MemberData("TestClients", MemberType = typeof(TestConfiguration))]
#endif
    public async Task GetReturnsEmptyString(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = string.Empty;
        await GetAndSetValues(client, key, value);
    }
}
