// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Errors;

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
        Task<object?> t4 = db.CustomCommand(["time"]); // This cmd is sent

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
    public async Task BasicTransaction(bool isCluster)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        Task<string> t1 = transaction.Set(key, "val");
        Task<gs?> t2 = transaction.Get(key);
        Task<object?> t3 = transaction.CustomCommand(["time"]); // This cmd is queued
        Task<object?> t4 = db.CustomCommand(["time"]); // This cmd is sent

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

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task BatchWithCommandException(bool isCluster)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        Task<gs?> t1 = transaction.Get(key);
        Task<object?> t3 = transaction.CustomCommand(["ping", "pong", "pang"]);

        Assert.True(transaction.Execute());
        Assert.Null(await t1);
        _ = await Assert.ThrowsAsync<RequestException>(async () => await t3);
    }

    // TODO parametrize
    [Fact]
    public async Task TransactionWithCrossSlot()
    {
        (string host, ushort port) = TestConfiguration.CLUSTER_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        ITransaction transaction = conn.GetDatabase().CreateTransaction();
        Task<gs?> t1 = transaction.Get(Guid.NewGuid().ToString());
        Task<gs?> t2 = transaction.Get(Guid.NewGuid().ToString());

        RequestException ex = Assert.Throws<RequestException>(() => transaction.Execute());
        Assert.Contains("CrossSlot", ex.Message);
        // in SER, commands' futures are never resolved if transactoin failed, so we do the same
        Assert.False(t1.Wait(100));
    }

    // TODO parametrize
    [Fact]
    public async Task BatchWithCrossSlot()
    {
        (string host, ushort port) = TestConfiguration.CLUSTER_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IBatch batch = conn.GetDatabase().CreateBatch();
        Task<gs?> t1 = batch.Get(Guid.NewGuid().ToString());
        Task<gs?> t2 = batch.Get(Guid.NewGuid().ToString());

        batch.Execute();
        Assert.Null(await t1);
        Assert.Null(await t2);
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true)]
    [InlineData(true, false)]
    [InlineData(false, true)]
    [InlineData(false, false)]
    public async Task TransactionWithMultipleConditions(bool isCluster, bool conditionPass)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        ITransaction transaction = conn.GetDatabase().CreateTransaction();

        Task<gs?> t1 = transaction.Get(Guid.NewGuid().ToString());
        // conditions which always pass/fail
        ConditionResult c1 = transaction.AddCondition(Condition.KeyNotExists(Guid.NewGuid().ToString()));
        ConditionResult c2 = transaction.AddCondition(conditionPass
            ? Condition.KeyNotExists(Guid.NewGuid().ToString())
            : Condition.KeyExists(Guid.NewGuid().ToString()));

        Assert.Equal(conditionPass, transaction.Execute());
        if (conditionPass)
        {
            Assert.Null(await t1);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t1);
        }

        Assert.True(c1.WasSatisfied);
        Assert.Equal(conditionPass, c2.WasSatisfied);
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true)]
    [InlineData(true, false)]
    [InlineData(false, true)]
    [InlineData(false, false)]
    public async Task ReusedBatchIsntSubmitted(bool isCluster, bool isBatch)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        IBatch batch = isBatch ? db.CreateBatch() : db.CreateTransaction();
        string key = Guid.NewGuid().ToString();

        Task<gs?> t1 = batch.Get(key);

        // first time - key does not exist
        batch.Execute();
        Assert.Null(await t1);

        // setting a key
        Assert.Equal("OK", await db.Set(key, "val"));
        Assert.Equal("val", await db.Get(key));

        // resubmitting a batch does nothing - it is not re-sent to server
        batch.Execute();
        Assert.Null(await t1);
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true)]
    [InlineData(true, false)]
    [InlineData(false, true)]
    [InlineData(false, false)]
    public async Task EmptyBatchIsntSubmitted(bool isCluster, bool isBatch)
    {
        // note: there is no an easy wayt to ensure that nothing was actually sent over the wire.
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();

        if (isBatch)
        {
            db.CreateBatch().Execute();
        }
        else
        {
            Assert.True(db.CreateTransaction().Execute());
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true, true)]
    [InlineData(true, true, true, false)]
    [InlineData(true, true, false, true)]
    [InlineData(true, true, false, false)]
    [InlineData(true, false, true, true)]
    [InlineData(true, false, true, false)]
    [InlineData(true, false, false, true)]
    [InlineData(true, false, false, false)]
    [InlineData(false, true, true, true)]
    [InlineData(false, true, true, false)]
    [InlineData(false, true, false, true)]
    [InlineData(false, true, false, false)]
    [InlineData(false, false, true, true)]
    [InlineData(false, false, true, false)]
    [InlineData(false, false, false, true)]
    [InlineData(false, false, false, false)]
    public async Task TransactionKeyExistsCondition(bool isCluster, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.Equal("OK", await db.Set(isConditionPositive == expectTranResult ? key : key3, "val"));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.KeyExists(key)
                : Condition.KeyExists(key3)
            : conditionShouldPass
                ? Condition.KeyNotExists(key)
                : Condition.KeyNotExists(key3);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true, true)]
    [InlineData(true, true, true, false)]
    [InlineData(true, true, false, true)]
    [InlineData(true, true, false, false)]
    [InlineData(true, false, true, true)]
    [InlineData(true, false, true, false)]
    [InlineData(true, false, false, true)]
    [InlineData(true, false, false, false)]
    [InlineData(false, true, true, true)]
    [InlineData(false, true, true, false)]
    [InlineData(false, true, false, true)]
    [InlineData(false, true, false, false)]
    [InlineData(false, false, true, true)]
    [InlineData(false, false, true, false)]
    [InlineData(false, false, false, true)]
    [InlineData(false, false, false, false)]
    public async Task TransactionStringEqualCondition(bool isCluster, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.Equal("OK", await db.Set(key, isConditionPositive == expectTranResult ? "val" : "_"));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.StringEqual(key, "val")
                : Condition.StringEqual(key, "_")
            : conditionShouldPass
                ? Condition.StringNotEqual(key, "val")
                : Condition.StringNotEqual(key, "_");
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true, true)]
    [InlineData(true, true, true, false)]
    [InlineData(true, true, false, true)]
    [InlineData(true, true, false, false)]
    [InlineData(true, false, true, true)]
    [InlineData(true, false, true, false)]
    [InlineData(true, false, false, true)]
    [InlineData(true, false, false, false)]
    [InlineData(false, true, true, true)]
    [InlineData(false, true, true, false)]
    [InlineData(false, true, false, true)]
    [InlineData(false, true, false, false)]
    [InlineData(false, false, true, true)]
    [InlineData(false, false, true, false)]
    [InlineData(false, false, false, true)]
    [InlineData(false, false, false, false)]
    public async Task TransactionHashEqualCondition(bool isCluster, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.Equal(1L, await db.CustomCommand(["HSET", key, "f", isConditionPositive == expectTranResult ? "val" : "_"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.HashEqual(key, "f", "val")
                : Condition.HashEqual(key, "f", "_")
            : conditionShouldPass
                ? Condition.HashNotEqual(key, "f", "val")
                : Condition.HashNotEqual(key, "f", "_");
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task TransactionConditionWithWrongKeyType(bool isCluster)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        // Condition checks hash value, while key stores a string
        Assert.Equal("OK", await db.Set(key, "f"));
        Condition condition = Condition.HashEqual(key, "f", "val");
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.False(transaction.Execute());
        Assert.False(c.WasSatisfied);
        _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
    }

    // TODO parametrize
    // TODO 4 params?
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionHashExistsCondition(bool isCluster, bool isConditionPositive, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        if (isConditionPositive == expectTranResult)
        {
            Assert.Equal(1L, await db.CustomCommand(["HSET", key, "f", "val"]));
        }

        ConditionResult c = transaction.AddCondition(isConditionPositive ? Condition.HashExists(key, "f") : Condition.HashNotExists(key, "f"));
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult, transaction.Execute());
        Assert.Equal(expectTranResult, c.WasSatisfied);
        if (expectTranResult)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true, true)]
    [InlineData(true, true, true, false)]
    [InlineData(true, true, false, true)]
    [InlineData(true, true, false, false)]
    [InlineData(true, false, true, true)]
    [InlineData(true, false, true, false)]
    [InlineData(true, false, false, true)]
    [InlineData(true, false, false, false)]
    [InlineData(false, true, true, true)]
    [InlineData(false, true, true, false)]
    [InlineData(false, true, false, true)]
    [InlineData(false, true, false, false)]
    [InlineData(false, false, true, true)]
    [InlineData(false, false, true, false)]
    [InlineData(false, false, false, true)]
    [InlineData(false, false, false, false)]
    public async Task TransactionSetContainsCondition(bool isCluster, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.Equal(1L, await db.CustomCommand(["SADD", key, isConditionPositive == expectTranResult ? "val" : "_"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.SetContains(key, "val")
                : Condition.SetContains(key, "_")
            : conditionShouldPass
                ? Condition.SetNotContains(key, "val")
                : Condition.SetNotContains(key, "_");
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true, true)]
    [InlineData(true, true, true, false)]
    [InlineData(true, true, false, true)]
    [InlineData(true, true, false, false)]
    [InlineData(true, false, true, true)]
    [InlineData(true, false, true, false)]
    [InlineData(true, false, false, true)]
    [InlineData(true, false, false, false)]
    [InlineData(false, true, true, true)]
    [InlineData(false, true, true, false)]
    [InlineData(false, true, false, true)]
    [InlineData(false, true, false, false)]
    [InlineData(false, false, true, true)]
    [InlineData(false, false, true, false)]
    [InlineData(false, false, false, true)]
    [InlineData(false, false, false, false)]
    public async Task TransactionSortedSetContainsCondition(bool isCluster, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.Equal(1L, await db.CustomCommand(["ZADD", key, "1", isConditionPositive == expectTranResult ? "val" : "_"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.SortedSetContains(key, "val")
                : Condition.SortedSetContains(key, "_")
            : conditionShouldPass
                ? Condition.SortedSetNotContains(key, "val")
                : Condition.SortedSetNotContains(key, "_");
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true, true)]
    [InlineData(true, true, true, false)]
    [InlineData(true, true, false, true)]
    [InlineData(true, true, false, false)]
    [InlineData(true, false, true, true)]
    [InlineData(true, false, true, false)]
    [InlineData(true, false, false, true)]
    [InlineData(true, false, false, false)]
    [InlineData(false, true, true, true)]
    [InlineData(false, true, true, false)]
    [InlineData(false, true, false, true)]
    [InlineData(false, true, false, false)]
    [InlineData(false, false, true, true)]
    [InlineData(false, false, true, false)]
    [InlineData(false, false, false, true)]
    [InlineData(false, false, false, false)]
    public async Task TransactionSortedSetEqualCondition(bool isCluster, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.Equal(1L, await db.CustomCommand(["ZADD", key, isConditionPositive == expectTranResult ? "1" : "2", "val"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.SortedSetEqual(key, "val", 1)
                : Condition.SortedSetEqual(key, "val", 2)
            : conditionShouldPass
                ? Condition.SortedSetNotEqual(key, "val", 1)
                : Condition.SortedSetNotEqual(key, "val", 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionStringLengthEqualCondition(bool isCluster, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.Equal("OK", await db.Set(key, expectTranResult ? "val" : "va"));

        Condition condition = conditionShouldPass
                ? Condition.StringLengthEqual(key, 3)
                : Condition.StringLengthEqual(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionStringLengthCompareCondition(bool isCluster, bool isConditionPositive, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.Equal("OK", await db.Set(key, expectTranResult == isConditionPositive ? "value" : "val"));

        Condition condition = isConditionPositive
            ? Condition.StringLengthGreaterThan(key, 4)
            : Condition.StringLengthLessThan(key, 4);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult, transaction.Execute());
        Assert.Equal(expectTranResult, c.WasSatisfied);
        if (expectTranResult)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionHashLengthEqualCondition(bool isCluster, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        if (expectTranResult)
        {
            Assert.Equal(1L, await db.CustomCommand(["HSET", key, "f1", "v1"]));
        }
        else
        {
            Assert.Equal(2L, await db.CustomCommand(["HSET", key, "f1", "v1", "f2", "v2"]));
        }

        Condition condition = conditionShouldPass
                ? Condition.HashLengthEqual(key, 1)
                : Condition.HashLengthEqual(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionHashLengthCompareCondition(bool isCluster, bool isConditionPositive, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        if (expectTranResult == isConditionPositive)
        {
            Assert.Equal(3L, await db.CustomCommand(["HSET", key, "f1", "v1", "f2", "v2", "f3", "v3"]));
        }
        else
        {
            Assert.Equal(1L, await db.CustomCommand(["HSET", key, "f1", "v1"]));
        }

        Condition condition = isConditionPositive
            ? Condition.HashLengthGreaterThan(key, 2)
            : Condition.HashLengthLessThan(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult, transaction.Execute());
        Assert.Equal(expectTranResult, c.WasSatisfied);
        if (expectTranResult)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionSetLengthEqualCondition(bool isCluster, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        if (expectTranResult)
        {
            Assert.Equal(1L, await db.CustomCommand(["SADD", key, "f1"]));
        }
        else
        {
            Assert.Equal(2L, await db.CustomCommand(["SADD", key, "f1", "f2"]));
        }

        Condition condition = conditionShouldPass
                ? Condition.SetLengthEqual(key, 1)
                : Condition.SetLengthEqual(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionSetLengthCompareCondition(bool isCluster, bool isConditionPositive, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        if (expectTranResult == isConditionPositive)
        {
            Assert.Equal(3L, await db.CustomCommand(["SADD", key, "f1", "f2", "f3"]));
        }
        else
        {
            Assert.Equal(1L, await db.CustomCommand(["SADD", key, "f1"]));
        }

        Condition condition = isConditionPositive
            ? Condition.SetLengthGreaterThan(key, 2)
            : Condition.SetLengthLessThan(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult, transaction.Execute());
        Assert.Equal(expectTranResult, c.WasSatisfied);
        if (expectTranResult)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionSortedSetLengthEqualCondition(bool isCluster, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        if (expectTranResult)
        {
            Assert.Equal(1L, await db.CustomCommand(["ZADD", key, "1", "f1"]));
        }
        else
        {
            Assert.Equal(2L, await db.CustomCommand(["ZADD", key, "1", "f1", "2", "f2"]));
        }

        Condition condition = conditionShouldPass
                ? Condition.SortedSetLengthEqual(key, 1)
                : Condition.SortedSetLengthEqual(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionSortedSetLengthCompareCondition(bool isCluster, bool isConditionPositive, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        if (expectTranResult == isConditionPositive)
        {
            Assert.Equal(3L, await db.CustomCommand(["ZADD", key, "1", "f1", "2", "f2", "3", "f3"]));
        }
        else
        {
            Assert.Equal(1L, await db.CustomCommand(["ZADD", key, "1", "f1"]));
        }

        Condition condition = isConditionPositive
            ? Condition.SortedSetLengthGreaterThan(key, 2)
            : Condition.SortedSetLengthLessThan(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult, transaction.Execute());
        Assert.Equal(expectTranResult, c.WasSatisfied);
        if (expectTranResult)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionListLengthEqualCondition(bool isCluster, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        if (expectTranResult)
        {
            Assert.Equal(1L, await db.CustomCommand(["LPUSH", key, "f1"]));
        }
        else
        {
            Assert.Equal(2L, await db.CustomCommand(["LPUSH", key, "f1", "f2"]));
        }

        Condition condition = conditionShouldPass
                ? Condition.ListLengthEqual(key, 1)
                : Condition.ListLengthEqual(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionListLengthCompareCondition(bool isCluster, bool isConditionPositive, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        if (expectTranResult == isConditionPositive)
        {
            Assert.Equal(3L, await db.CustomCommand(["LPUSH", key, "f1", "f2", "f3"]));
        }
        else
        {
            Assert.Equal(1L, await db.CustomCommand(["LPUSH", key, "f1"]));
        }

        Condition condition = isConditionPositive
            ? Condition.ListLengthGreaterThan(key, 2)
            : Condition.ListLengthLessThan(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult, transaction.Execute());
        Assert.Equal(expectTranResult, c.WasSatisfied);
        if (expectTranResult)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionListIndexExistsCondition(bool isCluster, bool isConditionPositive, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        if (expectTranResult == isConditionPositive)
        {
            Assert.Equal(3L, await db.CustomCommand(["LPUSH", key, "f1", "f2", "f3"]));
        }
        else
        {
            Assert.Equal(1L, await db.CustomCommand(["LPUSH", key, "f1"]));
        }

        Condition condition = isConditionPositive
            ? Condition.ListIndexExists(key, 2)
            : Condition.ListIndexNotExists(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult, transaction.Execute());
        Assert.Equal(expectTranResult, c.WasSatisfied);
        if (expectTranResult)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionListIndexEqualCondition(bool isCluster, bool isConditionPositive, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        if (expectTranResult == isConditionPositive)
        {
            Assert.Equal(1L, await db.CustomCommand(["LPUSH", key, "f1"]));
        }
        else
        {
            Assert.Equal(3L, await db.CustomCommand(["LPUSH", key, "f1", "f2", "f3"]));
        }

        Condition condition = isConditionPositive
            ? Condition.ListIndexEqual(key, 0, "f1")
            : Condition.ListIndexNotEqual(key, 0, "f1");
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        bool res = transaction.Execute();
        Assert.Equal(expectTranResult, res);
        Assert.Equal(expectTranResult, c.WasSatisfied);
        if (expectTranResult)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionStreamLengthEqualCondition(bool isCluster, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.NotNull(await db.CustomCommand(["XADD", key, "*", "f", "v"]));
        if (!expectTranResult)
        {
            Assert.NotNull(await db.CustomCommand(["XADD", key, "*", "f", "v"]));
        }

        Condition condition = conditionShouldPass
                ? Condition.StreamLengthEqual(key, 1)
                : Condition.StreamLengthEqual(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true)]
    [InlineData(true, true, false)]
    [InlineData(true, false, true)]
    [InlineData(true, false, false)]
    [InlineData(false, true, true)]
    [InlineData(false, true, false)]
    [InlineData(false, false, true)]
    [InlineData(false, false, false)]
    public async Task TransactionStreamLengthCompareCondition(bool isCluster, bool isConditionPositive, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.NotNull(await db.CustomCommand(["XADD", key, "*", "f", "v"]));
        if (expectTranResult == isConditionPositive)
        {
            Assert.NotNull(await db.CustomCommand(["XADD", key, "*", "f", "v"]));
            Assert.NotNull(await db.CustomCommand(["XADD", key, "*", "f", "v"]));
        }

        Condition condition = isConditionPositive
            ? Condition.StreamLengthGreaterThan(key, 2)
            : Condition.StreamLengthLessThan(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult, transaction.Execute());
        Assert.Equal(expectTranResult, c.WasSatisfied);
        if (expectTranResult)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true, true)]
    [InlineData(true, true, true, false)]
    [InlineData(true, true, false, true)]
    [InlineData(true, true, false, false)]
    [InlineData(true, false, true, true)]
    [InlineData(true, false, true, false)]
    [InlineData(true, false, false, true)]
    [InlineData(true, false, false, false)]
    [InlineData(false, true, true, true)]
    [InlineData(false, true, true, false)]
    [InlineData(false, true, false, true)]
    [InlineData(false, true, false, false)]
    [InlineData(false, false, true, true)]
    [InlineData(false, false, true, false)]
    [InlineData(false, false, false, true)]
    [InlineData(false, false, false, false)]
    public async Task TransactionSortedSetScoreExistsCondition(bool isCluster, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.Equal(1L, await db.CustomCommand(["ZADD", key, isConditionPositive == expectTranResult ? "1" : "2", "val"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.SortedSetScoreExists(key, 1)
                : Condition.SortedSetScoreExists(key, 2)
            : conditionShouldPass
                ? Condition.SortedSetScoreNotExists(key, 1)
                : Condition.SortedSetScoreNotExists(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
    }

    // TODO parametrize
    [Theory]
    [InlineData(true, true, true, true)]
    [InlineData(true, true, true, false)]
    [InlineData(true, true, false, true)]
    [InlineData(true, true, false, false)]
    [InlineData(true, false, true, true)]
    [InlineData(true, false, true, false)]
    [InlineData(true, false, false, true)]
    [InlineData(true, false, false, false)]
    [InlineData(false, true, true, true)]
    [InlineData(false, true, true, false)]
    [InlineData(false, true, false, true)]
    [InlineData(false, true, false, false)]
    [InlineData(false, false, true, true)]
    [InlineData(false, false, true, false)]
    [InlineData(false, false, false, true)]
    [InlineData(false, false, false, false)]
    public async Task TransactionSortedSetScoreCountExistsCondition(bool isCluster, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        (string host, ushort port) = isCluster ? TestConfiguration.CLUSTER_HOSTS[0] : TestConfiguration.STANDALONE_HOSTS[0];

        ConnectionMultiplexer conn = await ConnectionMultiplexer.ConnectAsync(host, port);

        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.Equal("OK", await db.Set(key2, "val"));

        Assert.Equal(2L, await db.CustomCommand(["ZADD", key, isConditionPositive == expectTranResult ? "1" : "2", "val", "1", "va"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.SortedSetScoreExists(key, 1, 2)
                : Condition.SortedSetScoreExists(key, 1, 1)
            : conditionShouldPass
                ? Condition.SortedSetScoreNotExists(key, 1, 2)
                : Condition.SortedSetScoreNotExists(key, 2, 1);
        ConditionResult c = transaction.AddCondition(condition);
        Task<gs?> t2 = transaction.Get(key2);

        Assert.Equal(expectTranResult == conditionShouldPass, transaction.Execute());
        Assert.Equal(expectTranResult == conditionShouldPass, c.WasSatisfied);
        if (expectTranResult == conditionShouldPass)
        {
            Assert.Equal("val", await t2);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
        }
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
