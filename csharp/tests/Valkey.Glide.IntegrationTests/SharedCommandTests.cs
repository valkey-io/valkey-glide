// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

using static Valkey.Glide.Pipeline.Options;

using gs = Valkey.Glide.GlideString;
namespace Valkey.Glide.IntegrationTests;

public class SharedCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

#pragma warning disable xUnit1047 // Avoid using TheoryDataRow arguments that might not be serializable
    public static IEnumerable<TheoryDataRow<BaseClient, bool>> GetTestClientWithAtomic =>
        TestConfiguration.TestClients.SelectMany(r => new TheoryDataRow<BaseClient, bool>[] { new(r.Data, true), new(r.Data, false) });
#pragma warning restore xUnit1047 // Avoid using TheoryDataRow arguments that might not be serializable

    internal static async Task GetAndSetValues(BaseClient client, string key, string value)
    {
        Assert.Equal("OK", await client.Set(key, value));
        Assert.Equal(value, (await client.Get(key))!);
    }

    internal static async Task GetAndSetRandomValues(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsLastSet(BaseClient client) =>
        await GetAndSetRandomValues(client);

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetAndSetCanHandleNonASCIIUnicode(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "שלום hello 汉字";
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsNull(BaseClient client) =>
        Assert.Null(await client.Get(Guid.NewGuid().ToString()));

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsEmptyString(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = string.Empty;
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(GetTestClientWithAtomic))]
    public async Task BatchRaiseOnError(BaseClient client, bool isAtomic)
    {
        bool isCluster = client is GlideClusterClient;
        string key1 = "{BatchRaiseOnError}" + Guid.NewGuid();
        string key2 = "{BatchRaiseOnError}" + Guid.NewGuid();

        //Batch batch = new Batch(isAtomic).Set(key1, "hello").CustomCommand(["lpop", key1]).CustomCommand(["del", key1]).CustomCommand(["rename", key1, key2]);
        //ClusterBatch clusterBatch = new ClusterBatch(isAtomic).Set(key1, "hello").CustomCommand(["lpop", key1]).CustomCommand(["del", key1]).CustomCommand(["rename", key1, key2]); ;

        IBatch batch = isCluster ? new ClusterBatch(isAtomic) : new Batch(isAtomic);
        batch.Set(key1, "hello").CustomCommand(["lpop", key1]).CustomCommand(["del", key1]).CustomCommand(["rename", key1, key2]);
        BaseBatchOptions options = isCluster ? new ClusterBatchOptions(raiseOnError: false) : new BatchOptions(raiseOnError: false);

        object[] res;

        if (isCluster)
        {
            res = (await (client as GlideClusterClient).Exec(batch as ClusterBatch, options as ClusterBatchOptions))!;
        }
        else
        {
            res = (await (client as GlideClient).Exec(batch as Batch, options as BatchOptions))!;
        }

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

        options = isCluster ? new ClusterBatchOptions(raiseOnError: true) : new BatchOptions(raiseOnError: true);
        Exception err = await Assert.ThrowsAsync<Exception>(async () =>
        {
            // TODO RequestException from #3411 https://github.com/valkey-io/valkey-glide/pull/3411
            _ = isCluster
                ? await (client as GlideClusterClient).Exec(batch as ClusterBatch, options as ClusterBatchOptions)
                : await (client as GlideClient).Exec(batch as Batch, options as BatchOptions);
            //GC.KeepAlive(client); // TODO?
        });
        Assert.Contains("wrong kind of value", err.Message);
        //GC.KeepAlive(client);
    }
}
