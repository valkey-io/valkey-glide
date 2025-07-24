// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Errors;

namespace Valkey.Glide.IntegrationTests;

public class BatchTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    /// <summary>
    /// Permute N bools and return their 2^N combinations all x all
    /// </summary>
    public static IEnumerable<IEnumerable<bool>> PermuteBools(int num)
    {
        int max = 1 << num;
        return Enumerable.Range(0, max).Select(comb =>
        {
            List<bool> l = [];
            for (int i = 1; i < max; i <<= 1)
            {
                l.Add((i & comb) == 0);
            }

            return l;
        });
    }

    public static IEnumerable<object[]> PermuteTestConnectionsAndBool(int numBools)
    {
        IEnumerable<IEnumerable<bool>> bools = PermuteBools(numBools);
        IEnumerable<ConnectionMultiplexer> conns = TestConfiguration.TestConnections.Select(r => r.Data.Item1);
        return conns.SelectMany(c => bools.Select(r => r.Cast<object>().Prepend(c).ToArray()));
    }

#pragma warning disable xUnit1042 // https://xunit.net/xunit.analyzers/rules/xUnit1042

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestConnections), MemberType = typeof(TestConfiguration))]
    public async Task BasicBatch(ConnectionMultiplexer conn, bool _)
    {
        IDatabase db = conn.GetDatabase();
        IBatch batch = db.CreateBatch();
        string key = Guid.NewGuid().ToString();
        Task<bool> t1 = batch.StringSetAsync(key, "val");
        Task<ValkeyValue> t2 = batch.StringGetAsync(key);
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
        Assert.True(await t1);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestConnections), MemberType = typeof(TestConfiguration))]
    public async Task BasicTransaction(ConnectionMultiplexer conn, bool _)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        Task<bool> t1 = transaction.StringSetAsync(key, "val");
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key);
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
        Assert.True(await t1);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClusterConnections), MemberType = typeof(TestConfiguration))]
    public async Task BatchWithCommandException(ConnectionMultiplexer conn)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        Task<ValkeyValue> t1 = transaction.StringGetAsync(key);
        Task<object?> t3 = transaction.CustomCommand(["ping", "pong", "pang"]);

        Assert.True(transaction.Execute());
        Assert.True((await t1).IsNull);
        _ = await Assert.ThrowsAsync<RequestException>(async () => await t3);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClusterConnections), MemberType = typeof(TestConfiguration))]
    public void TransactionWithCrossSlot(ConnectionMultiplexer conn)
    {
        ITransaction transaction = conn.GetDatabase().CreateTransaction();
        Task<ValkeyValue> t1 = transaction.StringGetAsync(Guid.NewGuid().ToString());
        Task<ValkeyValue> t2 = transaction.StringGetAsync(Guid.NewGuid().ToString());

        RequestException ex = Assert.Throws<RequestException>(() => transaction.Execute());
        Assert.Contains("CrossSlot", ex.Message);
        // in SER, commands' futures are never resolved if transaction failed, so we do the same
        Assert.False(t1.Wait(100));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClusterConnections), MemberType = typeof(TestConfiguration))]
    public async Task BatchWithCrossSlot(ConnectionMultiplexer conn)
    {
        IBatch batch = conn.GetDatabase().CreateBatch();
        Task<ValkeyValue> t1 = batch.StringGetAsync(Guid.NewGuid().ToString());
        Task<ValkeyValue> t2 = batch.StringGetAsync(Guid.NewGuid().ToString());

        batch.Execute();
        Assert.True((await t1).IsNull);
        Assert.True((await t2).IsNull);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 1)]
    public async Task TransactionWithMultipleConditions(ConnectionMultiplexer conn, bool conditionPass)
    {
        ITransaction transaction = conn.GetDatabase().CreateTransaction();

        Task<ValkeyValue> t1 = transaction.StringGetAsync(Guid.NewGuid().ToString());
        // conditions which always pass/fail
        ConditionResult c1 = transaction.AddCondition(Condition.KeyNotExists(Guid.NewGuid().ToString()));
        ConditionResult c2 = transaction.AddCondition(conditionPass
            ? Condition.KeyNotExists(Guid.NewGuid().ToString())
            : Condition.KeyExists(Guid.NewGuid().ToString()));

        Assert.Equal(conditionPass, transaction.Execute());
        if (conditionPass)
        {
            Assert.True((await t1).IsNull);
        }
        else
        {
            _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t1);
        }

        Assert.True(c1.WasSatisfied);
        Assert.Equal(conditionPass, c2.WasSatisfied);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 1)]
    public async Task ReusedBatchIsntSubmitted(ConnectionMultiplexer conn, bool isBatch)
    {
        IDatabase db = conn.GetDatabase();
        IBatch batch = isBatch ? db.CreateBatch() : db.CreateTransaction();
        string key = Guid.NewGuid().ToString();

        Task<ValkeyValue> t1 = batch.StringGetAsync(key);

        // first time - key does not exist
        batch.Execute();
        Assert.True((await t1).IsNull);

        // setting a key
        Assert.True(await db.StringSetAsync(key, "val"));
        Assert.Equal("val", await db.StringGetAsync(key));

        // resubmitting a batch does nothing - it is not re-sent to server
        batch.Execute();
        Assert.True((await t1).IsNull);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 1)]
    public void EmptyBatchIsntSubmitted(ConnectionMultiplexer conn, bool isBatch)
    {
        // note: there is no easy way to ensure that nothing was actually sent over the wire.
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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 3)]
    public async Task TransactionKeyExistsCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        Assert.True(await db.StringSetAsync(isConditionPositive == expectTranResult ? key : key3, "val"));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.KeyExists(key)
                : Condition.KeyExists(key3)
            : conditionShouldPass
                ? Condition.KeyNotExists(key)
                : Condition.KeyNotExists(key3);
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 3)]
    public async Task TransactionStringEqualCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        Assert.True(await db.StringSetAsync(key, isConditionPositive == expectTranResult ? "val" : "_"));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.StringEqual(key, "val")
                : Condition.StringEqual(key, "_")
            : conditionShouldPass
                ? Condition.StringNotEqual(key, "val")
                : Condition.StringNotEqual(key, "_");
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 3)]
    public async Task TransactionHashEqualCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        Assert.Equal(1L, await db.CustomCommand(["HSET", key, "f", isConditionPositive == expectTranResult ? "val" : "_"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.HashEqual(key, "f", "val")
                : Condition.HashEqual(key, "f", "_")
            : conditionShouldPass
                ? Condition.HashNotEqual(key, "f", "val")
                : Condition.HashNotEqual(key, "f", "_");
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClusterConnections), MemberType = typeof(TestConfiguration))]
    public async Task TransactionConditionWithWrongKeyType(ConnectionMultiplexer conn)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        // Condition checks hash value, while key stores a string
        Assert.True(await db.StringSetAsync(key, "f"));
        Condition condition = Condition.HashEqual(key, "f", "val");
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

        Assert.False(transaction.Execute());
        Assert.False(c.WasSatisfied);
        _ = await Assert.ThrowsAsync<TaskCanceledException>(async () => await t2);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionHashExistsCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        if (isConditionPositive == expectTranResult)
        {
            Assert.Equal(1L, await db.CustomCommand(["HSET", key, "f", "val"]));
        }

        ConditionResult c = transaction.AddCondition(isConditionPositive ? Condition.HashExists(key, "f") : Condition.HashNotExists(key, "f"));
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 3)]
    public async Task TransactionSetContainsCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        Assert.Equal(1L, await db.CustomCommand(["SADD", key, isConditionPositive == expectTranResult ? "val" : "_"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.SetContains(key, "val")
                : Condition.SetContains(key, "_")
            : conditionShouldPass
                ? Condition.SetNotContains(key, "val")
                : Condition.SetNotContains(key, "_");
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 3)]
    public async Task TransactionSortedSetContainsCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        Assert.Equal(1L, await db.CustomCommand(["ZADD", key, "1", isConditionPositive == expectTranResult ? "val" : "_"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.SortedSetContains(key, "val")
                : Condition.SortedSetContains(key, "_")
            : conditionShouldPass
                ? Condition.SortedSetNotContains(key, "val")
                : Condition.SortedSetNotContains(key, "_");
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 3)]
    public async Task TransactionSortedSetEqualCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        Assert.Equal(1L, await db.CustomCommand(["ZADD", key, isConditionPositive == expectTranResult ? "1" : "2", "val"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.SortedSetEqual(key, "val", 1)
                : Condition.SortedSetEqual(key, "val", 2)
            : conditionShouldPass
                ? Condition.SortedSetNotEqual(key, "val", 1)
                : Condition.SortedSetNotEqual(key, "val", 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionStringLengthEqualCondition(ConnectionMultiplexer conn, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        Assert.True(await db.StringSetAsync(key, expectTranResult ? "val" : "va"));

        Condition condition = conditionShouldPass
                ? Condition.StringLengthEqual(key, 3)
                : Condition.StringLengthEqual(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionStringLengthCompareCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        Assert.True(await db.StringSetAsync(key, expectTranResult == isConditionPositive ? "value" : "val"));

        Condition condition = isConditionPositive
            ? Condition.StringLengthGreaterThan(key, 4)
            : Condition.StringLengthLessThan(key, 4);
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionHashLengthEqualCondition(ConnectionMultiplexer conn, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

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
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionHashLengthCompareCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

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
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionSetLengthEqualCondition(ConnectionMultiplexer conn, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

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
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionSetLengthCompareCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

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
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionSortedSetLengthEqualCondition(ConnectionMultiplexer conn, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

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
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionSortedSetLengthCompareCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

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
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionListLengthEqualCondition(ConnectionMultiplexer conn, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

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
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionListLengthCompareCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

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
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionListIndexExistsCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

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
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionListIndexEqualCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

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
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionStreamLengthEqualCondition(ConnectionMultiplexer conn, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        Assert.NotNull(await db.CustomCommand(["XADD", key, "*", "f", "v"]));
        if (!expectTranResult)
        {
            Assert.NotNull(await db.CustomCommand(["XADD", key, "*", "f", "v"]));
        }

        Condition condition = conditionShouldPass
                ? Condition.StreamLengthEqual(key, 1)
                : Condition.StreamLengthEqual(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 2)]
    public async Task TransactionStreamLengthCompareCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

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
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 3)]
    public async Task TransactionSortedSetScoreExistsCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        Assert.Equal(1L, await db.CustomCommand(["ZADD", key, isConditionPositive == expectTranResult ? "1" : "2", "val"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.SortedSetScoreExists(key, 1)
                : Condition.SortedSetScoreExists(key, 2)
            : conditionShouldPass
                ? Condition.SortedSetScoreNotExists(key, 1)
                : Condition.SortedSetScoreNotExists(key, 2);
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(PermuteTestConnectionsAndBool), 3)]
    public async Task TransactionSortedSetScoreCountExistsCondition(ConnectionMultiplexer conn, bool isConditionPositive, bool conditionShouldPass, bool expectTranResult)
    {
        IDatabase db = conn.GetDatabase();
        ITransaction transaction = db.CreateTransaction();
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        Assert.True(await db.StringSetAsync(key2, "val"));

        Assert.Equal(2L, await db.CustomCommand(["ZADD", key, isConditionPositive == expectTranResult ? "1" : "2", "val", "1", "va"]));

        Condition condition = isConditionPositive
            ? conditionShouldPass
                ? Condition.SortedSetScoreExists(key, 1, 2)
                : Condition.SortedSetScoreExists(key, 1, 1)
            : conditionShouldPass
                ? Condition.SortedSetScoreNotExists(key, 1, 2)
                : Condition.SortedSetScoreNotExists(key, 2, 1);
        ConditionResult c = transaction.AddCondition(condition);
        Task<ValkeyValue> t2 = transaction.StringGetAsync(key2);

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
#pragma warning restore xUnit1042 // https://xunit.net/xunit.analyzers/rules/xUnit1042

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
