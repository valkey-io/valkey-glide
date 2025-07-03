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

    public static TheoryData<BatchTestData> GetTestClientWithAtomic =>
        [.. TestConfiguration.TestClients.SelectMany(r => new[] { true, false }.SelectMany(isAtomic =>
            new BatchTestData[] {
                new("String commands", r.Data, CreateStringTest, isAtomic),
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
