// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Commands.Options.InfoOptions;
using static Valkey.Glide.Route;

namespace Valkey.Glide.IntegrationTests;

public class ClusterClientTests
{
    [Fact]
    public async Task CustomCommandWithRandomRoute()
    {
        GlideClusterClient client = TestConfiguration.DefaultClusterClient();
        // if a command isn't routed in 100 tries to different nodes, you are a lucker or have a bug
        SortedSet<string> ports = [];
        foreach (int i in Enumerable.Range(0, 100))
        {
            string res = ((await client.CustomCommand(["info", "server"], Route.Random)).SingleValue! as GlideString)!;
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
        GlideClusterClient client = TestConfiguration.DefaultClusterClient();

        string res = ((await client.CustomCommand(["info", "replication"], new SlotKeyRoute("abc", SlotType.Primary))).SingleValue! as GlideString)!;
        Assert.Contains("role:master", res);

        res = ((await client.CustomCommand(["info", "replication"], new SlotKeyRoute("abc", SlotType.Replica))).SingleValue! as GlideString)!;
        Assert.Contains("role:slave", res);

        res = ((await client.CustomCommand(["info", "replication"], new SlotIdRoute(42, SlotType.Primary))).SingleValue! as GlideString)!;
        Assert.Contains("role:master", res);

        res = ((await client.CustomCommand(["info", "replication"], new SlotIdRoute(42, SlotType.Replica))).SingleValue! as GlideString)!;
        Assert.Contains("role:slave", res);

        res = ((await client.CustomCommand(["info", "replication"], new ByAddressRoute(TestConfiguration.CLUSTER_HOSTS[0].host, TestConfiguration.CLUSTER_HOSTS[0].port))).SingleValue! as GlideString)!;
        Assert.Contains("# Replication", res);
    }

    [Fact]
    public async Task CustomCommandWithMultiNodeRoute()
    {
        GlideClusterClient client = TestConfiguration.DefaultClusterClient();
        _ = await client.Set("abc", "abc");
        _ = await client.Set("klm", "klm");
        _ = await client.Set("xyz", "xyz");

        long res = (long)(await client.CustomCommand(["dbsize"], AllPrimaries)).SingleValue!;
        Assert.True(res >= 3);
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
