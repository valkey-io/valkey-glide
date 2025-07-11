// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

using gs = Valkey.Glide.GlideString;

namespace Valkey.Glide.IntegrationTests;

internal class BatchTestUtils
{
    public static List<TestInfo> CreateStringTest(IBatch batch, bool isAtomic)
    {
        List<TestInfo> testData = [];
        string prefix = isAtomic ? "{stringKey}-" : "";
        string key1 = $"{prefix}1-{Guid.NewGuid()}";

        string value1 = $"value-1-{Guid.NewGuid()}";

        _ = batch.Set(key1, value1);
        testData.Add(new("OK", "Set(key1, value1)"));
        _ = batch.Get(key1);
        testData.Add(new(new gs(value1), "Get(key1)"));

        return testData;
    }

    public static List<TestInfo> CreateSetTest(IBatch batch, bool isAtomic)
    {
        List<TestInfo> testData = [];
        string prefix = "{setKey}-";
        string atomicPrefix = isAtomic ? prefix : "";
        string key1 = $"{atomicPrefix}1-{Guid.NewGuid()}";
        string key2 = $"{atomicPrefix}2-{Guid.NewGuid()}";
        string key3 = $"{atomicPrefix}3-{Guid.NewGuid()}";
        string key4 = $"{atomicPrefix}4-{Guid.NewGuid()}";
        string key5 = $"{atomicPrefix}5-{Guid.NewGuid()}";
        string destKey = $"{atomicPrefix}dest-{Guid.NewGuid()}";

        _ = batch.SetAdd(key1, "a");
        testData.Add(new(true, "SetAdd(key1, a)"));

        _ = batch.SetAdd(key1, ["b", "c"]);
        testData.Add(new(2L, "SetAdd(key1, [b, c])"));

        _ = batch.SetLength(key1);
        testData.Add(new(3L, "SetLength(key1)"));

        _ = batch.SetMembers(key1);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetMembers(key1)", true));

        _ = batch.SetRemove(key1, "a");
        testData.Add(new(true, "SetRemove(key1, a)"));

        _ = batch.SetRemove(key1, "x");
        testData.Add(new(false, "SetRemove(key1, x)"));

        _ = batch.SetAdd(key1, ["d", "e"]);
        testData.Add(new(2L, "SetAdd(key1, [d, e])"));

        _ = batch.SetRemove(key1, ["b", "d", "nonexistent"]);
        testData.Add(new(2L, "SetRemove(key1, [b, d, nonexistent])"));

        _ = batch.SetLength(key1);
        testData.Add(new(2L, "SetLength(key1) after multiple remove"));

        _ = batch.SetAdd(key2, "c");
        testData.Add(new(true, "SetAdd(key2, c)"));

        _ = batch.SetAdd(key3, "z");
        testData.Add(new(true, "SetAdd(key3, z)"));

        _ = batch.SetAdd(key4, ["x", "y"]);
        testData.Add(new(2L, "SetAdd(key4, [x, y])"));

        _ = batch.SetAdd(key5, ["c", "f"]);
        testData.Add(new(2L, "SetAdd(key5, [c, f])"));

        // Add data to prefixed keys for multi-key operations
        _ = batch.SetAdd(prefix + key1, ["c", "e"]);
        testData.Add(new(2L, "SetAdd(prefix+key1, [c, e])"));

        _ = batch.SetAdd(prefix + key2, ["c"]);
        testData.Add(new(1L, "SetAdd(prefix+key2, [c])"));

        _ = batch.SetAdd(prefix + key5, ["c", "f"]);
        testData.Add(new(2L, "SetAdd(prefix+key5, [c, f])"));

        // Multi-key operations: always use prefix to ensure same hash slot
        _ = batch.SetUnion(prefix + key1, prefix + key2);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetUnion(prefix+key1, prefix+key2)", true));

        _ = batch.SetUnion([prefix + key1, prefix + key2, prefix + key5]);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetUnion([prefix+key1, prefix+key2, prefix+key5])", true));

        _ = batch.SetIntersect(prefix + key1, prefix + key2);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetIntersect(prefix+key1, prefix+key2)", true));

        _ = batch.SetIntersect([prefix + key1, prefix + key2]);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetIntersect([prefix+key1, prefix+key2])", true));

        _ = batch.SetDifference(prefix + key1, prefix + key2);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetDifference(prefix+key1, prefix+key2)", true));

        _ = batch.SetDifference([prefix + key1, prefix + key2]);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetDifference([prefix+key1, prefix+key2])", true));

        if (TestConfiguration.SERVER_VERSION >= new Version("7.0.0"))
        {
            _ = batch.SetIntersectionLength([prefix + key1, prefix + key2]);
            testData.Add(new(1L, "SetIntersectionLength([prefix+key1, prefix+key2])"));

            _ = batch.SetIntersectionLength([prefix + key1, prefix + key2], 5);
            testData.Add(new(1L, "SetIntersectionLength([prefix+key1, prefix+key2], limit=5)"));

            _ = batch.SetIntersectionLength([prefix + key1, prefix + key5], 1);
            testData.Add(new(1L, "SetIntersectionLength([prefix+key1, prefix+key5], limit=1)"));
        }

        _ = batch.SetUnionStore(prefix + destKey, prefix + key1, prefix + key2);
        testData.Add(new(2L, "SetUnionStore(prefix+destKey, prefix+key1, prefix+key2)"));

        _ = batch.SetUnionStore(prefix + destKey, [prefix + key1, prefix + key2]);
        testData.Add(new(2L, "SetUnionStore(prefix+destKey, [prefix+key1, prefix+key2])"));

        _ = batch.SetIntersectStore(prefix + destKey, prefix + key1, prefix + key2);
        testData.Add(new(1L, "SetIntersectStore(prefix+destKey, prefix+key1, prefix+key2)"));

        _ = batch.SetIntersectStore(prefix + destKey, [prefix + key1, prefix + key2]);
        testData.Add(new(1L, "SetIntersectStore(prefix+destKey, [prefix+key1, prefix+key2])"));

        _ = batch.SetDifferenceStore(prefix + destKey, prefix + key1, prefix + key2);
        testData.Add(new(1L, "SetDifferenceStore(prefix+destKey, prefix+key1, prefix+key2)"));

        _ = batch.SetDifferenceStore(prefix + destKey, [prefix + key1, prefix + key2]);
        testData.Add(new(1L, "SetDifferenceStore(prefix+destKey, [prefix+key1, prefix+key2])"));

        _ = batch.SetPop(key3);
        testData.Add(new(new gs("z"), "SetPop(key3)"));

        _ = batch.SetLength(key3);
        testData.Add(new(0L, "SetLength(key3) after pop"));

        _ = batch.SetPop(key4, 1);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetPop(key4, 1)", true));

        _ = batch.SetLength(key4);
        testData.Add(new(1L, "SetLength(key4) after pop with count"));

        return testData;
    }

    public static List<TestInfo> CreateConnectionManagementTest(IBatch batch, bool isAtomic)
    {
        List<TestInfo> testData = [];

        _ = batch.Ping();
        testData.Add(new(TimeSpan.Zero, "Ping()", true));

        ValkeyValue pingMessage = "Hello Valkey!";
        _ = batch.Ping(pingMessage);
        testData.Add(new(TimeSpan.Zero, "Ping(message)", true));

        ValkeyValue echoMessage = "Echo test message";
        _ = batch.Echo(echoMessage);
        testData.Add(new((gs)echoMessage, "Echo(message)"));

        _ = batch.Echo("");
        testData.Add(new(new gs(""), "Echo(empty)"));

        return testData;
    }

    public static TheoryData<BatchTestData> GetTestClientWithAtomic =>
        [.. TestConfiguration.TestClients.SelectMany(r => new[] { true, false }.SelectMany(isAtomic =>
            new BatchTestData[] {
                new("String commands", r.Data, CreateStringTest, isAtomic),
                new("Set commands", r.Data, CreateSetTest, isAtomic),
                new("Connection Management commands", r.Data, CreateConnectionManagementTest, isAtomic),
            }))];
}

internal delegate List<TestInfo> BatchTestDataProvider(IBatch batch, bool isAtomic);

internal record BatchTestData(string TestName, BaseClient Client, BatchTestDataProvider TestDataProvider, bool IsAtomic)
{
    public string TestName = TestName;
    public BaseClient Client = Client;
    public BatchTestDataProvider TestDataProvider = TestDataProvider;
    public bool IsAtomic = IsAtomic;

    public override string? ToString() => $"{TestName} {Client} IsAtomic = {IsAtomic}";
}

internal record TestInfo(object? Expected, string TestName, bool CheckTypeOnly = false)
{
    public object? ExpectedValue = Expected;
    public string TestName = TestName;
    public bool CheckTypeOnly = CheckTypeOnly;
}
