// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

using static Valkey.Glide.Errors;
using static Valkey.Glide.Pipeline.Options;

using TimeoutException = Valkey.Glide.Errors.TimeoutException;

namespace Valkey.Glide.IntegrationTests;

// TODO: even though collections aren't executed in parallel, tests in a collection still parallelized
//       better to run tests in the named collections sequentially
[Collection(typeof(SharedBatchTests))]
[CollectionDefinition(DisableParallelization = true)]
public class SharedBatchTests
{
#pragma warning disable xUnit1047 // Avoid using TheoryDataRow arguments that might not be serializable
    public static IEnumerable<TheoryDataRow<BaseClient, bool>> GetTestClientWithAtomic =>
        TestConfiguration.TestClients.SelectMany(r => new TheoryDataRow<BaseClient, bool>[] { new(r.Data, true), new(r.Data, false) });
#pragma warning restore xUnit1047 // Avoid using TheoryDataRow arguments that might not be serializable

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(GetTestClientWithAtomic))]
    public async Task BatchTimeout(BaseClient client, bool isAtomic)
    {
        bool isCluster = client is GlideClusterClient;
        IBatch batch = isCluster ? new ClusterBatch(isAtomic) : new Batch(isAtomic);
        _ = batch.CustomCommand(["DEBUG", "sleep", "0.5"]);
        BaseBatchOptions options = isCluster ? new ClusterBatchOptions(timeout: 100) : new BatchOptions(timeout: 100);

        // Expect a timeout exception on short timeout
        _ = await Assert.ThrowsAsync<TimeoutException>(() => isCluster
                ? ((GlideClusterClient)client).Exec((ClusterBatch)batch, true, (ClusterBatchOptions)options)
                : ((GlideClient)client).Exec((Batch)batch, true, (BatchOptions)options));

        // Wait for server to wake up
        Thread.Sleep(TimeSpan.FromSeconds(1));

        // Retry with a longer timeout and expect [OK]
        options = isCluster ? new ClusterBatchOptions(timeout: 1000, route: Route.Random) : new BatchOptions(timeout: 1000);
        object?[]? res = isCluster
            ? await ((GlideClusterClient)client).Exec((ClusterBatch)batch, true, (ClusterBatchOptions)options)
            : await ((GlideClient)client).Exec((Batch)batch, true, (BatchOptions)options);
        Assert.Equal(["OK"], res);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(GetTestClientWithAtomic))]
    public async Task BatchRaiseOnError(BaseClient client, bool isAtomic)
    {
        bool isCluster = client is GlideClusterClient;
        string key1 = "{BatchRaiseOnError}" + Guid.NewGuid();
        string key2 = "{BatchRaiseOnError}" + Guid.NewGuid();

        IBatch batch = isCluster ? new ClusterBatch(isAtomic) : new Batch(isAtomic);
        // TODO replace custom command
        _ = batch.Set(key1, "hello").CustomCommand(["lpop", key1]).CustomCommand(["del", key1]).CustomCommand(["rename", key1, key2]);

        object?[] res = isCluster
            ? (await ((GlideClusterClient)client).Exec((ClusterBatch)batch, false))!
            : (await ((GlideClient)client).Exec((Batch)batch, false))!;

        // Exceptions aren't raised, but stored in the result set
        Assert.Multiple(
            () => Assert.Equal(4, res.Length),
            () => Assert.Equal("OK", res[0]),
            () => Assert.Equal(1L, (long)res[2]!),
            () => Assert.IsType<RequestException>(res[1]),
            () => Assert.IsType<RequestException>(res[3]),
            () => Assert.Contains("wrong kind of value", (res[1] as RequestException)!.Message),
            () => Assert.Contains("no such key", (res[3] as RequestException)!.Message)
        );

        // First exception is raised, all data lost
        Exception err = await Assert.ThrowsAsync<RequestException>(async () => _ = isCluster
                ? await ((GlideClusterClient)client).Exec((ClusterBatch)batch, true)
                : await ((GlideClient)client).Exec((Batch)batch, true));
        Assert.Contains("wrong kind of value", err.Message);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(GetTestClientWithAtomic))]
    public async Task BatchSetCommands(BaseClient client, bool isAtomic)
    {
        bool isCluster = client is GlideClusterClient;
        string key1 = "{BatchSetCommands}" + Guid.NewGuid();
        string key2 = "{BatchSetCommands}" + Guid.NewGuid();
        string destKey = "{BatchSetCommands}" + Guid.NewGuid();

        IBatch batch = isCluster ? new ClusterBatch(isAtomic) : new Batch(isAtomic);

        _ = batch.SetAdd(key1, "member1");
        _ = batch.SetAdd(key1, new ValkeyValue[] { "member2", "member3" });
        _ = batch.SetLength(key1);
        _ = batch.SetMembers(key1);
        _ = batch.SetRemove(key1, "member1");
        _ = batch.SetPop(key1);
        _ = batch.SetAdd(key2, new ValkeyValue[] { "member2", "member4" });
        _ = batch.SetUnion(key1, key2);
        _ = batch.SetIntersect(key1, key2);
        _ = batch.SetDifference(key1, key2);
        _ = batch.SetUnionStore(destKey, key1, key2);
        _ = batch.SetIntersectStore(destKey, key1, key2);
        _ = batch.SetDifferenceStore(destKey, key1, key2);

        object?[] res = isCluster
            ? (await ((GlideClusterClient)client).Exec((ClusterBatch)batch, true))!
            : (await ((GlideClient)client).Exec((Batch)batch, true))!;

        Assert.Equal(13, res.Length);
        Assert.True((bool)res[0]!); // SetAdd single
        Assert.Equal(2L, (long)res[1]!); // SetAdd multiple
        Assert.Equal(3L, (long)res[2]!); // SetLength
        Assert.Equal(3, ((ValkeyValue[])res[3]!).Length); // SetMembers
        Assert.True((bool)res[4]!); // SetRemove
        Assert.True(((ValkeyValue)res[5]!).HasValue); // SetPop
        Assert.Equal(2L, (long)res[6]!); // SetAdd key2
        Assert.True(((ValkeyValue[])res[7]!).Length > 0); // SetUnion
        Assert.True(((ValkeyValue[])res[8]!).Length >= 0); // SetIntersect
        Assert.True(((ValkeyValue[])res[9]!).Length >= 0); // SetDifference
        Assert.True((long)res[10]! >= 0); // SetUnionStore
        Assert.True((long)res[11]! >= 0); // SetIntersectStore
        Assert.True((long)res[12]! >= 0); // SetDifferenceStore
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(GetTestClientWithAtomic))]
    public async Task BatchSetIntersectionLength(BaseClient client, bool isAtomic)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION.CompareTo(new Version("7.0.0")) < 0,
            "SetIntersectionLength is supported since 7.0.0"
        );

        bool isCluster = client is GlideClusterClient;
        string key1 = "{BatchSetCommands}" + Guid.NewGuid();
        string key2 = "{BatchSetCommands}" + Guid.NewGuid();

        await client.SetAddAsync(key1, new ValkeyValue[] { "a", "b" });
        await client.SetAddAsync(key2, new ValkeyValue[] { "b", "c" });

        IBatch batch = isCluster ? new ClusterBatch(isAtomic) : new Batch(isAtomic);
        _ = batch.SetIntersectionLength(new ValkeyKey[] { key1, key2 });

        object?[] res = isCluster
            ? (await ((GlideClusterClient)client).Exec((ClusterBatch)batch, true))!
            : (await ((GlideClient)client).Exec((Batch)batch, true))!;

        Assert.Equal(1, res.Length);
        Assert.Equal(1L, (long)res[0]!); // Intersection length
    }
}
