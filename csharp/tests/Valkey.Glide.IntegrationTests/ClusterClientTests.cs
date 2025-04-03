// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0


using Valkey.Glide.InterOp.Routing;

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
            string res = ((await client.CustomCommand(["info", "server"], new SingleRandom()))! as GlideString)!;
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

        string res = (await client.CustomCommand(["info", "replication"], new SingleSpecificKeyedNode("abc", ESlotAddress.Master))! as GlideString)!;
        Assert.Contains("role:master", res);

        res = (await client.CustomCommand(["info", "replication"], new SingleSpecificKeyedNode("abc", ESlotAddress.ReplicaRequired))! as GlideString)!;
        Assert.Contains("role:slave", res);

        res = (await client.CustomCommand(["info", "replication"], new SingleSpecificNode(42, ESlotAddress.Master))! as GlideString)!;
        Assert.Contains("role:master", res);

        res = (await client.CustomCommand(["info", "replication"], new SingleSpecificNode(42, ESlotAddress.ReplicaRequired))! as GlideString)!;
        Assert.Contains("role:slave", res);

        res = (await client.CustomCommand(["info", "replication"], new SingleByAddress(TestConfiguration.CLUSTER_HOSTS[0].host, TestConfiguration.CLUSTER_HOSTS[0].port))! as GlideString)!;
        Assert.Contains("# Replication", res);
    }

    [Fact(Skip = "non-string return types are not supported yet")]
    public async Task CustomCommandWithMultiNodeRoute()
    {
        GlideClusterClient client = TestConfiguration.DefaultClusterClient();
        _ = await client.Set("abc", "abc");
        _ = await client.Set("klm", "klm");
        _ = await client.Set("xyz", "xyz");

#pragma warning disable CS8629 // Nullable value type may be null.
        int res = (int)(await client.CustomCommand(["dbsize"]) as int?);
#pragma warning restore CS8629 // Nullable value type may be null.
        TestContext.Current.TestOutputHelper?.WriteLine(res.ToString());
    }
}
