// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class BatchTests
{
    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task BasicBatch(bool isCluster)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        IBatch batch = db.CreateBatch();
        string key = Guid.NewGuid().ToString();
        Task<string> t1 = batch.Set(key, "val");
        Task<gs?> t2 = batch.Get(key);
        Task<object?> t3 = batch.CustomCommand(["time"]); // This cmd is queued
        Task<object?> t4 = db.CustomCommand(["time"]); // This cmd is send

        // wait for t3 for 100ms, expect to time out (batch is queued, not sent yet)
        Assert.False(t3.Wait(100));

        batch.Execute();
        // tasks could be awaited in any order
        DateTime dt3 = ParseTimeResponse(await t3);
        DateTime dt4 = ParseTimeResponse(await t4);
        Assert.True(dt3 > dt4);
        Assert.Equal("val", await t2);
        Assert.Equal("OK", await t1);
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task BasicTrans(bool isCluster)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        Task<string> t1 = transaction.Set(key, "val");
        Task<gs?> t2 = transaction.Get(key);
        Task<object?> t3 = transaction.CustomCommand(["time"]); // This cmd is queued
        Task<object?> t4 = db.CustomCommand(["time"]); // This cmd is send

        // wait for t3 for 100ms, expect to time out (transaction is queued, not sent yet)
        Assert.False(t3.Wait(100));

        Assert.True(transaction.Execute());
        // tasks could be awaited in any order
        DateTime dt3 = ParseTimeResponse(await t3);
        DateTime dt4 = ParseTimeResponse(await t4);
        Assert.True(dt3 > dt4);
        Assert.Equal("val", await t2);
        Assert.Equal("OK", await t1);
    }

    private DateTime ParseTimeResponse(object? res)
    {
        object[] arr = (object[])res!;
        return DateTime.UnixEpoch.AddSeconds(double.Parse((arr[0] as gs)!))
#if NET8_0_OR_GREATER
            .AddMicroseconds(double.Parse((arr[1] as gs)!));
#else
            .AddMilliseconds(double.Parse((arr[1] as gs)!) / 1000);
#endif
    }
}
