// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

using static Valkey.Glide.Commands.Options.InfoOptions;
using static Valkey.Glide.Errors;
using static Valkey.Glide.Route;

using gs = Valkey.Glide.GlideString;
namespace Valkey.Glide.IntegrationTests;

public class ClusterClientTests
{
    [Fact]
    public async Task CustomCommand()
    {
        GlideClusterClient client = TestConfiguration.DefaultClusterClient();
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

    [Fact]
    public async Task CustomCommandWithRandomRoute()
    {
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();
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

    [Fact]
    public async Task CustomCommandWithSingleNodeRoute()
    {
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();

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

    [Fact]
    public async Task CustomCommandWithMultiNodeRoute()
    {
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();
        _ = await client.Set("abc", "abc");
        _ = await client.Set("klm", "klm");
        _ = await client.Set("xyz", "xyz");

        long res = (long)(await client.CustomCommand(["dbsize"], AllPrimaries)).SingleValue!;
        Assert.True(res >= 3);
    }

    [Fact]
    public async Task RetryStrategyIsNotSupportedForTransactions()
    {
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();

        _ = await Assert.ThrowsAsync<RequestException>(async () => _ = await client.Exec(new(true), true, new(retryStrategy: new())));
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task BatchWithSingleNodeRoute(bool isAtomic)
    {
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();

        ClusterBatch batch = new ClusterBatch(isAtomic).Info([Section.REPLICATION]);

        object?[]? res = await client.Exec(batch, true, new(route: new SlotKeyRoute("abc", SlotType.Primary)));
        Assert.Contains("role:master", res![0] as gs);

        res = await client.Exec(batch, true, new(route: new SlotKeyRoute("abc", SlotType.Replica)));
        Assert.Contains("role:slave", res![0] as gs);

        res = await client.Exec(batch, true, new(route: new SlotIdRoute(42, SlotType.Primary)));
        Assert.Contains("role:master", res![0] as gs);

        res = await client.Exec(batch, true, new(route: new SlotIdRoute(42, SlotType.Replica)));
        Assert.Contains("role:slave", res![0] as gs);

        res = await client.Exec(batch, true, new(route: new ByAddressRoute(TestConfiguration.CLUSTER_HOSTS[0].host, TestConfiguration.CLUSTER_HOSTS[0].port)));
        Assert.Contains("# Replication", res![0] as gs);
    }

    [Fact]
    public async Task Info()
    {
        GlideClusterClient client = TestConfiguration.DefaultClusterClient();

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

    [Fact]
    public async Task InfoWithRoute()
    {
        GlideClusterClient client = TestConfiguration.DefaultClusterClient();

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
