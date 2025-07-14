// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

using static Valkey.Glide.Commands.Options.InfoOptions;
using static Valkey.Glide.Errors;
using static Valkey.Glide.Route;

namespace Valkey.Glide.IntegrationTests;

public class ClusterClientTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

#pragma warning disable xUnit1046 // Avoid using TheoryDataRow arguments that are not serializable
    public static IEnumerable<TheoryDataRow<GlideClusterClient, bool>> ClusterClientWithAtomic =>
        TestConfiguration.TestClusterClients.SelectMany(r => new TheoryDataRow<GlideClusterClient, bool>[] { new(r.Data, true), new(r.Data, false) });
#pragma warning restore xUnit1046 // Avoid using TheoryDataRow arguments that are not serializable

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClusterClients), MemberType = typeof(TestConfiguration))]
    public async Task CustomCommand(GlideClusterClient client)
    {
        // command which returns always a single value
        long res = (long)(await client.CustomCommand(["dbsize"])).SingleValue!;
        Assert.True(res >= 0);
        // command which returns a multi value by default
        Dictionary<string, object?> info = (await client.CustomCommand(["info"])).MultiValue;
        foreach (object? nodeInfo in info.Values)
        {
            Assert.Contains("# Server", (nodeInfo as gs)!);
        }
        // command which returns a map even on a single node route
        ClusterValue<object?> config = await client.CustomCommand(["config", "get", "*file"], Route.Random);
        Assert.True((config.SingleValue as Dictionary<gs, object?>)!.Count > 0);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClusterClients), MemberType = typeof(TestConfiguration))]
    public async Task CustomCommandWithRandomRoute(GlideClusterClient client)
    {
        // if a command isn't routed in 100 tries to different nodes, you are a lucker or have a bug
        SortedSet<string> ports = [];
        foreach (int i in Enumerable.Range(0, 100))
        {
            string res = ((await client.CustomCommand(["info", "server"], Route.Random)).SingleValue! as gs)!;
            foreach (string line in res!.Split("\r\n"))
            {
                if (line.Contains("tcp_port"))
                {
                    _ = ports.Add(line);
                    if (ports.Count > 1)
                    {
                        return;
                    }
                    break;
                }
            }
        }
        Assert.Fail($"All 100 commands were sent to: {ports.First()}");
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClusterClients), MemberType = typeof(TestConfiguration))]
    public async Task CustomCommandWithSingleNodeRoute(GlideClusterClient client)
    {
        string res = ((await client.CustomCommand(["info", "replication"], new SlotKeyRoute("abc", SlotType.Primary))).SingleValue! as gs)!;
        Assert.Contains("role:master", res);

        res = ((await client.CustomCommand(["info", "replication"], new SlotKeyRoute("abc", SlotType.Replica))).SingleValue! as gs)!;
        Assert.Contains("role:slave", res);

        res = ((await client.CustomCommand(["info", "replication"], new SlotIdRoute(42, SlotType.Primary))).SingleValue! as gs)!;
        Assert.Contains("role:master", res);

        res = ((await client.CustomCommand(["info", "replication"], new SlotIdRoute(42, SlotType.Replica))).SingleValue! as gs)!;
        Assert.Contains("role:slave", res);

        res = ((await client.CustomCommand(["info", "replication"], new ByAddressRoute(TestConfiguration.CLUSTER_HOSTS[0].host, TestConfiguration.CLUSTER_HOSTS[0].port))).SingleValue! as gs)!;
        Assert.Contains("# Replication", res);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClusterClients), MemberType = typeof(TestConfiguration))]
    public async Task CustomCommandWithMultiNodeRoute(GlideClusterClient client)
    {
        _ = await client.StringSetAsync("abc", "abc");
        _ = await client.StringSetAsync("klm", "klm");
        _ = await client.StringSetAsync("xyz", "xyz");

        long res = (long)(await client.CustomCommand(["dbsize"], AllPrimaries)).SingleValue!;
        Assert.True(res >= 3);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClusterClients), MemberType = typeof(TestConfiguration))]
    public async Task RetryStrategyIsNotSupportedForTransactions(GlideClusterClient client)
        => _ = await Assert.ThrowsAsync<RequestException>(async () => _ = await client.Exec(new(true), true, new(retryStrategy: new())));

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(ClusterClientWithAtomic))]
    public async Task BatchWithSingleNodeRoute(GlideClusterClient client, bool isAtomic)
    {
        ClusterBatch batch = new ClusterBatch(isAtomic).Info([Section.REPLICATION]);

        object?[]? res = await client.Exec(batch, true, new(route: new SlotKeyRoute("abc", SlotType.Primary)));
        Assert.Contains("role:master", res![0] as string);

        res = await client.Exec(batch, true, new(route: new SlotKeyRoute("abc", SlotType.Replica)));
        Assert.Contains("role:slave", res![0] as string);

        res = await client.Exec(batch, true, new(route: new SlotIdRoute(42, SlotType.Primary)));
        Assert.Contains("role:master", res![0] as string);

        res = await client.Exec(batch, true, new(route: new SlotIdRoute(42, SlotType.Replica)));
        Assert.Contains("role:slave", res![0] as string);

        res = await client.Exec(batch, true, new(route: new ByAddressRoute(TestConfiguration.CLUSTER_HOSTS[0].host, TestConfiguration.CLUSTER_HOSTS[0].port)));
        Assert.Contains("# Replication", res![0] as string);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClusterClients), MemberType = typeof(TestConfiguration))]
    public async Task Info(GlideClusterClient client)
    {
        Dictionary<string, string> info = await client.Info();
        foreach (string nodeInfo in info.Values)
        {
            Assert.Multiple([
                () => Assert.Contains("# Server", nodeInfo),
                () => Assert.Contains("# Replication", nodeInfo),
                () => Assert.DoesNotContain("# Latencystats", nodeInfo),
            ]);
        }

        info = await client.Info([Section.REPLICATION]);
        foreach (string nodeInfo in info.Values)
        {
            Assert.Multiple([
                () => Assert.DoesNotContain("# Server", nodeInfo),
                () => Assert.Contains("# Replication", nodeInfo),
                () => Assert.DoesNotContain("# Latencystats", nodeInfo),
            ]);
        }
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClusterClients), MemberType = typeof(TestConfiguration))]
    public async Task InfoWithRoute(GlideClusterClient client)
    {
        ClusterValue<string> info = await client.Info(Route.Random);
        Assert.Multiple([
            () => Assert.Contains("# Server", info.SingleValue),
            () => Assert.Contains("# Replication", info.SingleValue),
            () => Assert.DoesNotContain("# Latencystats", info.SingleValue),
        ]);

        info = await client.Info(AllPrimaries);
        foreach (string nodeInfo in info.MultiValue.Values)
        {
            Assert.Multiple([
                () => Assert.Contains("# Server", nodeInfo),
                () => Assert.Contains("# Replication", nodeInfo),
                () => Assert.DoesNotContain("# Latencystats", nodeInfo),
            ]);
        }

        info = await client.Info([Section.ERRORSTATS], AllNodes);

        foreach (string nodeInfo in info.MultiValue.Values)
        {
            Assert.Multiple([
                () => Assert.DoesNotContain("# Server", nodeInfo),
                () => Assert.Contains("# Errorstats", nodeInfo),
            ]);
        }
    }
}
