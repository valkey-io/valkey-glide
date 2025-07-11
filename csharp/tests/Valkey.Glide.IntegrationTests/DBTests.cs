// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Commands.Options;

namespace Valkey.Glide.IntegrationTests;

public class DBTests
{
    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task Basic(bool isCluster)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

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
