// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Commands.Options;

namespace Valkey.Glide.IntegrationTests;

public class DBTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestConnections), MemberType = typeof(TestConfiguration))]
    public async Task Basic(ConnectionMultiplexer conn)
    {
        IDatabase db = conn.GetDatabase();
        string key = Guid.NewGuid().ToString();

        Assert.Null(await db.Get(key));
        Assert.Equal("OK", await db.Set(key, "val"));
        Assert.Equal("val", (await db.Get(key))!);

        string info = await db.Info([InfoOptions.Section.CLUSTER]);
        /* TODO after merging with https://github.com/valkey-io/valkey-glide/pull/4310
        bool isCluster = conn.
        Assert.True(isCluster
            ? info.Contains("cluster_enabled:1")
            : info.Contains("cluster_enabled:0"));
        */
        Assert.Contains("cluster_enabled:", info);
    }
}
