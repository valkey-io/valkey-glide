// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

using static Valkey.Glide.Pipeline.Options;
using static Valkey.Glide.Route;

using gs = Valkey.Glide.GlideString;
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
            string res = ((await client.CustomCommand(["info", "server"], Route.Random))! as GlideString)!;
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

        string res = (await client.CustomCommand(["info", "replication"], new SlotKeyRoute("abc", SlotType.Primary))! as GlideString)!;
        Assert.Contains("role:master", res);

        res = (await client.CustomCommand(["info", "replication"], new SlotKeyRoute("abc", SlotType.Replica))! as GlideString)!;
        Assert.Contains("role:slave", res);

        res = (await client.CustomCommand(["info", "replication"], new SlotIdRoute(42, SlotType.Primary))! as GlideString)!;
        Assert.Contains("role:master", res);

        res = (await client.CustomCommand(["info", "replication"], new SlotIdRoute(42, SlotType.Replica))! as GlideString)!;
        Assert.Contains("role:slave", res);

        res = (await client.CustomCommand(["info", "replication"], new ByAddressRoute(TestConfiguration.CLUSTER_HOSTS[0].host, TestConfiguration.CLUSTER_HOSTS[0].port))! as GlideString)!;
        Assert.Contains("# Replication", res);
    }

    [Fact]
    public async Task CustomCommandWithMultiNodeRoute()
    {
        GlideClusterClient client = TestConfiguration.DefaultClusterClient();
        _ = await client.Set("abc", "abc");
        _ = await client.Set("klm", "klm");
        _ = await client.Set("xyz", "xyz");

        long res = (long)(await client.CustomCommand(["dbsize"], AllPrimaries))!;
        Assert.True(res >= 3);
    }


    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task BatchTimeout(bool isAtomic)
    {
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();

        ClusterBatch batch = new ClusterBatch(isAtomic).CustomCommand(["DEBUG", "sleep", "0.5"]);
        ClusterBatchOptions options = new(timeout: 100);

        // Expect a timeout exception on short timeout
        _ = await Assert.ThrowsAsync<Exception>(() => client.Exec(batch, options));
        // TODO TimeoutException from #3411 https://github.com/valkey-io/valkey-glide/pull/3411

        // Retry with a longer timeout and expect [null]
        options = new(timeout: 1000);
        object[] res = (await client.Exec(batch, options))!;
        Assert.Equal([new gs("OK")], res); // TODO changed to "OK" in #3589 https://github.com/valkey-io/valkey-glide/pull/3589
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task BatchRaiseOnError(bool isAtomic)
    {
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();
        string key1 = "{BatchRaiseOnError}-1-" + Guid.NewGuid();
        string key2 = "{BatchRaiseOnError}-2-" + Guid.NewGuid();

        ClusterBatch batch = new ClusterBatch(isAtomic).Set(key1, "hello").CustomCommand(["lpop", key1]).CustomCommand(["del", key1]).CustomCommand(["rename", key1, key2]);
        ClusterBatchOptions options = new(raiseOnError: false);

        object[] res = (await client.Exec(batch, options))!;
        // Exceptions aren't raised, but stored in the result set
        Assert.Multiple(
            () => Assert.Equal(4, res.Length),
            () => Assert.Equal(new gs("OK"), res[0]), // TODO changed to "OK" in #3589 https://github.com/valkey-io/valkey-glide/pull/3589
            () => Assert.Equal(1L, (long)res[2]),
            () => Assert.IsType<Exception>(res[1]), // TODO RequestException from #3411 https://github.com/valkey-io/valkey-glide/pull/3411
            () => Assert.IsType<Exception>(res[3]),
            () => Assert.Contains("wrong kind of value", ((Exception)res[1]).Message),
            () => Assert.Contains("no such key", ((Exception)res[3]).Message)
        );

        // First exception is raised, all data lost

        options = new(raiseOnError: true);
        Exception err = await Assert.ThrowsAsync<Exception>(async () =>
        {
            // TODO RequestException from #3411 https://github.com/valkey-io/valkey-glide/pull/3411
            await client.Exec(batch, options);
            GC.KeepAlive(client); // TODO?
        });
        Assert.Contains("wrong kind of value", err.Message);
        GC.KeepAlive(client);
    }
}
