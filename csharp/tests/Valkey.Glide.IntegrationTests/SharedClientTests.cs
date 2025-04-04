// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class SharedClientTests
{
    public SharedClientTests(TestConfiguration config)
    {
        Config = config;
    }

    // TODO: investigate and fix tests failing/flaky on MacOS

    public TestConfiguration Config { get; }

    [Theory(DisableDiscoveryEnumeration = true, Skip = "Flaky on MacOS", SkipWhen = nameof(TestConfiguration.IsMacOs), SkipType = typeof(TestConfiguration))]
#if NET8_0_OR_GREATER
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
#else
    [MemberData("TestClients", MemberType = typeof(TestConfiguration))]
#endif
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
    [Theory(DisableDiscoveryEnumeration = true, Skip = "Flaky on MacOS", SkipWhen = nameof(TestConfiguration.IsMacOs), SkipType = typeof(TestConfiguration))]
    [Trait("duration", "long")]
#if NET8_0_OR_GREATER
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
#else
    [MemberData("TestClients", MemberType = typeof(TestConfiguration))]
#endif
    public void ConcurrentOperationsWork(BaseClient client)
    {
        List<Task> operations = new();

        for (int i = 0; i < 1000; ++i)
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

        Task.WaitAll(operations.ToArray());
    }
}
