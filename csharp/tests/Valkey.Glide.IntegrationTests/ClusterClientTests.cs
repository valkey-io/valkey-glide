// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

using static Valkey.Glide.Errors;
using static Valkey.Glide.Route;

using gs = Valkey.Glide.GlideString;
namespace Valkey.Glide.IntegrationTests;

public class ClusterClientTests
{
    [Fact]
    public async Task CustomCommandWithRandomRoute()
    {
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();
        // if a command isn't routed in 100 tries to different nodes, you are a lucker or have a bug
        SortedSet<string> ports = [];
        foreach (int i in Enumerable.Range(0, 100))
        {
            string res = ((await client.CustomCommand(["info", "server"], Route.Random))! as gs)!;
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

        gs? res = await client.CustomCommand(["info", "replication"], new SlotKeyRoute("abc", SlotType.Primary)) as gs;
        Assert.Contains("role:master", res);

        res = await client.CustomCommand(["info", "replication"], new SlotKeyRoute("abc", SlotType.Replica)) as gs;
        Assert.Contains("role:slave", res);

        res = await client.CustomCommand(["info", "replication"], new SlotIdRoute(42, SlotType.Primary)) as gs;
        Assert.Contains("role:master", res);

        res = await client.CustomCommand(["info", "replication"], new SlotIdRoute(42, SlotType.Replica)) as gs;
        Assert.Contains("role:slave", res);

        res = await client.CustomCommand(["info", "replication"], new ByAddressRoute(TestConfiguration.CLUSTER_HOSTS[0].host, TestConfiguration.CLUSTER_HOSTS[0].port)) as gs;
        Assert.Contains("# Replication", res);
    }

    [Fact]
    public async Task CustomCommandWithMultiNodeRoute()
    {
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();
        _ = await client.Set("abc", "abc");
        _ = await client.Set("klm", "klm");
        _ = await client.Set("xyz", "xyz");

        long res = (long)(await client.CustomCommand(["dbsize"], AllPrimaries))!;
        Assert.True(res >= 3);
    }

    [Fact]
    public async Task RetryStrategyIsNotSupportedForTransactions()
    {
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();

        _ = await Assert.ThrowsAsync<RequestException>(async () => _ = await client.Exec(new(true), new(retryStrategy: new())));
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task BatchWithSingleNodeRoute(bool isAtomic)
    {
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();

        ClusterBatch batch = new ClusterBatch(isAtomic).CustomCommand(["info", "replication"]);

        object?[]? res = await client.Exec(batch, new(route: new SlotKeyRoute("abc", SlotType.Primary)));
        Assert.Contains("role:master", res![0] as gs);

        res = await client.Exec(batch, new(route: new SlotKeyRoute("abc", SlotType.Replica)));
        Assert.Contains("role:slave", res![0] as gs);

        res = await client.Exec(batch, new(route: new SlotIdRoute(42, SlotType.Primary)));
        Assert.Contains("role:master", res![0] as gs);

        res = await client.Exec(batch, new(route: new SlotIdRoute(42, SlotType.Replica)));
        Assert.Contains("role:slave", res![0] as gs);

        res = await client.Exec(batch, new(route: new ByAddressRoute(TestConfiguration.CLUSTER_HOSTS[0].host, TestConfiguration.CLUSTER_HOSTS[0].port)));
        Assert.Contains("# Replication", res![0] as gs);
    }
}
