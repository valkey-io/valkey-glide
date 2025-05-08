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
}
