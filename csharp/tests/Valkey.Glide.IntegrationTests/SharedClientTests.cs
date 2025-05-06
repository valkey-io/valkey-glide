// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

[Collection(typeof(SharedClientTests))]
[CollectionDefinition(DisableParallelization = true)]
public class SharedClientTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task HandleVeryLargeInput(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        const int expectedSize = 2 << 23;

        while (value.Length < expectedSize)
        {
            value += value;
        }
        await SharedCommandTests.GetAndSetValues(client, key, value);
    }

    // This test is slow, but it caught timing and releasing issues in the past,
    // so it's being kept.
    [Theory(DisableDiscoveryEnumeration = true)]
    [Trait("duration", "long")]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public void ConcurrentOperationsWork(BaseClient client)
    {
        List<Task> operations = [];

        for (int i = 0; i < 100; ++i)
        {
            int index = i;
            operations.Add(Task.Run(async () =>
            {
                for (int i = 0; i < 1000; ++i)
                {
                    if ((i + index) % 2 == 0)
                    {
                        await SharedCommandTests.GetAndSetRandomValues(client);
                    }
                    else
                    {
                        Assert.Null(await client.Get(Guid.NewGuid().ToString()));
                    }
                }
            }));
        }

        Task.WaitAll([.. operations]);
    }
}
