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

    // TODO: FIX
    // public static List<TestInfo> CreateSetTest(IBatch batch, bool isAtomic)
    // {
    //     List<TestInfo> testData = [];
    //     string prefix = isAtomic ? "{setKey}-" : "";
    //     string key1 = $"{prefix}1";
    //     string key2 = $"{prefix}2";
    //     string key3 = $"{prefix}3";
    //     string key4 = $"{prefix}4";
    //     string key5 = $"{prefix}5";
    //     string destKey = $"{prefix}dest";

    //     _ = batch.SetAdd(key1, "a");
    //     testData.Add(new(true, "SetAdd(key1, a)"));

    //     _ = batch.SetAdd(key1, ["b", "c"]);
    //     testData.Add(new(2L, "SetAdd(key1, [b, c])"));

    //     _ = batch.SetLength(key1);
    //     testData.Add(new(3L, "SetLength(key1)"));

    //     _ = batch.SetMembers(key1);
    //     testData.Add(new(new ValkeyValue[0], "SetMembers(key1)", true));

    //     _ = batch.SetRemove(key1, "a");
    //     testData.Add(new(true, "SetRemove(key1, a)"));

    //     _ = batch.SetRemove(key1, "x");
    //     testData.Add(new(false, "SetRemove(key1, x)"));

    //     _ = batch.SetAdd(key1, ["d", "e"]);
    //     testData.Add(new(2L, "SetAdd(key1, [d, e])"));

    //     _ = batch.SetRemove(key1, ["b", "d", "nonexistent"]);
    //     testData.Add(new(2L, "SetRemove(key1, [b, d, nonexistent])"));

    //     _ = batch.SetLength(key1);
    //     testData.Add(new(2L, "SetLength(key1) after multiple remove"));

    //     _ = batch.SetAdd(key2, "c");
    //     testData.Add(new(true, "SetAdd(key2, c)"));

    //     _ = batch.SetAdd(key3, "z");
    //     testData.Add(new(true, "SetAdd(key3, z)"));

    //     _ = batch.SetAdd(key4, ["x", "y"]);
    //     testData.Add(new(2L, "SetAdd(key4, [x, y])"));

    //     _ = batch.SetAdd(key5, ["c", "f"]);
    //     testData.Add(new(2L, "SetAdd(key5, [c, f])"));

    //     _ = batch.SetUnion(key1, key2);
    //     testData.Add(new(new ValkeyValue[0], "SetUnion(key1, key2)", true));

    //     _ = batch.SetUnion([key1, key2, key5]);
    //     testData.Add(new(new ValkeyValue[0], "SetUnion([key1, key2, key5])", true));

    //     _ = batch.SetIntersect(key1, key2);
    //     testData.Add(new(new ValkeyValue[0], "SetIntersect(key1, key2)", true));

    //     _ = batch.SetIntersect([key1, key2]);
    //     testData.Add(new(new ValkeyValue[0], "SetIntersect([key1, key2])", true));

    //     _ = batch.SetDifference(key1, key2);
    //     testData.Add(new(new ValkeyValue[0], "SetDifference(key1, key2)", true));

    //     _ = batch.SetDifference([key1, key2]);
    //     testData.Add(new(new ValkeyValue[0], "SetDifference([key1, key2])", true));

    //     if (TestConfiguration.SERVER_VERSION.CompareTo(new Version("7.0.0")) >= 0)
    //     {
    //         _ = batch.SetIntersectionLength([key1, key2]);
    //         testData.Add(new(1L, "SetIntersectionLength([key1, key2])"));

    //         _ = batch.SetIntersectionLength([key1, key2], 5);
    //         testData.Add(new(1L, "SetIntersectionLength([key1, key2], limit=5)"));

    //         _ = batch.SetIntersectionLength([key1, key5], 1);
    //         testData.Add(new(1L, "SetIntersectionLength([key1, key5], limit=1)"));
    //     }

    //     _ = batch.SetUnionStore(destKey, key1, key2);
    //     testData.Add(new(2L, "SetUnionStore(destKey, key1, key2)"));

    //     _ = batch.SetUnionStore(destKey, [key1, key2]);
    //     testData.Add(new(2L, "SetUnionStore(destKey, [key1, key2])"));

    //     _ = batch.SetIntersectStore(destKey, key1, key2);
    //     testData.Add(new(1L, "SetIntersectStore(destKey, key1, key2)"));

    //     _ = batch.SetIntersectStore(destKey, [key1, key2]);
    //     testData.Add(new(1L, "SetIntersectStore(destKey, [key1, key2])"));

    //     _ = batch.SetDifferenceStore(destKey, key1, key2);
    //     testData.Add(new(1L, "SetDifferenceStore(destKey, key1, key2)"));

    //     _ = batch.SetDifferenceStore(destKey, [key1, key2]);
    //     testData.Add(new(1L, "SetDifferenceStore(destKey, [key1, key2])"));

    //     _ = batch.SetPop(key3);
    //     testData.Add(new(new gs("z"), "SetPop(key3)"));

    //     _ = batch.SetLength(key3);
    //     testData.Add(new(0L, "SetLength(key3) after pop"));

    //     _ = batch.SetPop(key4, 1);
    //     testData.Add(new(new ValkeyValue[0], "SetPop(key4, 1)", true));

    //     _ = batch.SetLength(key4);
    //     testData.Add(new(1L, "SetLength(key4) after pop with count"));

    //     return testData;
    // }

    public static TheoryData<BatchTestData> GetTestClientWithAtomic =>
        [.. TestConfiguration.TestClients.SelectMany(r => new[] { true, false }.SelectMany(isAtomic =>
            new BatchTestData[] {
                new("String commands", r.Data, CreateStringTest, isAtomic),
                // new("Set commands", r.Data, CreateSetTest, isAtomic),
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
