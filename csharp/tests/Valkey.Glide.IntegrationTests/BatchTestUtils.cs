// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

namespace Valkey.Glide.IntegrationTests;

internal class BatchTestUtils
{
    public static List<TestInfo> CreateStringTest(IBatch batch, bool isAtomic)
    {
        List<TestInfo> testData = [];
        string prefix = isAtomic ? "{stringKey}-" : "";
        string key1 = $"{prefix}1-{Guid.NewGuid()}";
        string key2 = $"{prefix}2-{Guid.NewGuid()}";
        string nonExistingKey = $"{prefix}nonexisting-{Guid.NewGuid()}";

        string value1 = $"value-1-{Guid.NewGuid()}";
        string value2 = "test-value";

        // Use IBatch interface directly - no casting needed
        _ = batch.StringSet(key1, value1);
        testData.Add(new(true, "StringSet(key1, value1)"));
        _ = batch.StringSet(key2, value2);
        testData.Add(new(true, "StringSet(key2, value2)"));
        _ = batch.StringGet(key1);
        testData.Add(new(new gs(value1), "StringGet(key1)"));
        _ = batch.StringGet(key2);
        testData.Add(new(new gs(value2), "StringGet(key2)"));
        _ = batch.StringLength(key1);
        testData.Add(new((long)value1.Length, "StringLength(key1)"));
        _ = batch.StringLength(key2);
        testData.Add(new((long)value2.Length, "StringLength(key2)"));
        _ = batch.StringLength(nonExistingKey);
        testData.Add(new(0L, "StringLength(nonExistingKey)"));

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

    public static List<TestInfo> CreateListTest(IBatch batch, bool isAtomic)
    {
        List<TestInfo> testData = [];
        string prefix = isAtomic ? "{listKey}-" : "";
        string key1 = $"{prefix}1-{Guid.NewGuid()}";
        string key2 = $"{prefix}2-{Guid.NewGuid()}";
        string key3 = $"{prefix}3-{Guid.NewGuid()}";

        string value1 = $"value-1-{Guid.NewGuid()}";
        string value2 = $"value-2-{Guid.NewGuid()}";
        string value3 = $"value-3-{Guid.NewGuid()}";
        string value4 = $"value-4-{Guid.NewGuid()}";

        _ = batch.ListLeftPush(key1, [value1, value2]);
        testData.Add(new(2L, "ListLeftPush(key1, [value1, value2])"));

        _ = batch.ListLeftPush(key1, [value3]);
        testData.Add(new(3L, "ListLeftPush(key1, [value3])"));

        _ = batch.ListLeftPop(key1);
        testData.Add(new(new ValkeyValue(value3), "ListLeftPop(key1)"));

        _ = batch.ListLeftPop(key1);
        testData.Add(new(new ValkeyValue(value2), "ListLeftPop(key1) second"));

        _ = batch.ListLeftPush(key2, [value1, value2, value3, value4]);
        testData.Add(new(4L, "ListLeftPush(key2, [value1, value2, value3, value4])"));

        _ = batch.ListLeftPop(key2, 2);
        testData.Add(new(ValkeyValue.EmptyArray, "ListLeftPop(key2, 2)", true));

        _ = batch.ListLeftPop(key2, 10);
        testData.Add(new(Array.Empty<ValkeyValue>(), "ListLeftPop(key2, 10)", true));

        _ = batch.ListLeftPop(key3);
        // TODO: switch expected back to `ValkeyValue.Null` after converter fix
        testData.Add(new(null, "ListLeftPop(key3) non-existent"));

        _ = batch.ListLeftPop(key3, 5);
        testData.Add(new(null, "ListLeftPop(key3, 5) non-existent"));

        return testData;
    }

    public static TheoryData<BatchTestData> GetTestClientWithAtomic =>
        [.. TestConfiguration.TestClients.SelectMany(r => new[] { true, false }.SelectMany(isAtomic =>
            new BatchTestData[] {
                new("String commands", r.Data, CreateStringTest, isAtomic),
                new("Set commands", r.Data, CreateSetTest, isAtomic),
                new("List commands", r.Data, CreateListTest, isAtomic),
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
