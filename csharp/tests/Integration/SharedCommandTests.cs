// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Tests.Integration;
public class SharedCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    internal static async Task GetAndSetValues(AsyncClient client, string key, string value)
    {
        Assert.Equal("OK", await client.SetAsync(key, value));
        Assert.Equal(value, await client.GetAsync(key));
    }

    internal static async Task GetAndSetRandomValues(AsyncClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsLastSet(AsyncClient client) =>
        await GetAndSetRandomValues(client);

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetAndSetCanHandleNonASCIIUnicode(AsyncClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "שלום hello 汉字";
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsNull(AsyncClient client) =>
        Assert.Null(await client.GetAsync(Guid.NewGuid().ToString()));

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsEmptyString(AsyncClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = string.Empty;
        await GetAndSetValues(client, key, value);
    }
}
