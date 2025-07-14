// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Commands.Options;

namespace Valkey.Glide.IntegrationTests;

public class DBTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestConnections), MemberType = typeof(TestConfiguration))]
    public async Task Basic(ConnectionMultiplexer conn, bool isCluster)
    {
        IDatabase db = conn.GetDatabase();
        string key = Guid.NewGuid().ToString();

        ValkeyValue result = await db.StringGetAsync(key);
        Assert.True(result.IsNull);
        Assert.True(await db.StringSetAsync(key, "val"));
        ValkeyValue retrievedValue = await db.StringGetAsync(key);
        Assert.Equal("val", retrievedValue.ToString());

        string info = await db.Info([InfoOptions.Section.CLUSTER]);

        Assert.True(isCluster
            ? info.Contains("cluster_enabled:1")
            : info.Contains("cluster_enabled:0"));
    }
}
