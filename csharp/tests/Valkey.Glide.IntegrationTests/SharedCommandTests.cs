// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

namespace Valkey.Glide.IntegrationTests;

public class SharedCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(BatchTestUtils.GetTestClientWithAtomic), MemberType = typeof(BatchTestUtils))]
    internal async Task BatchTest(BatchTestData testData)
    {
        IBatch batch = testData.Client is GlideClient ? new Batch(testData.IsAtomic) : new ClusterBatch(testData.IsAtomic);
        List<TestInfo> expectedInfo = testData.TestDataProvider(batch, testData.IsAtomic);

        object?[] actualResult = testData.Client switch
        {
            GlideClient client => (await client.Exec((Batch)batch, false))!,
            GlideClusterClient client => (await client.Exec((ClusterBatch)batch, false))!,
            _ => throw new NotImplementedException()
        };

        Assert.Equal(expectedInfo.Count, actualResult.Length);
        List<string> failedChecks = [];
        for (int i = 0; i < actualResult.Length; i++)
        {
            try
            {
                if (expectedInfo[i].CheckTypeOnly)
                {
                    Assert.IsType(expectedInfo[i].ExpectedValue!.GetType(), actualResult[i]);
                }
                else
                {
                    // TODO use assertDeepEquals
                    Assert.Equivalent(expectedInfo[i].ExpectedValue, actualResult[i]);
                }
            }
            catch (Exception e)
            {
                failedChecks.Add($"{expectedInfo[i].TestName} failed: {e.Message}");
            }
        }
        Assert.Empty(failedChecks);
    }
}
