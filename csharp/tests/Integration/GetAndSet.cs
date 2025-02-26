// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Runtime.InteropServices;

using Glide;

using static Tests.Integration.IntegrationTestBase;

namespace Tests.Integration;
public class GetAndSet : IClassFixture<IntegrationTestBase>
{
    private async Task GetAndSetValues(AsyncClient client, string key, string value)
    {
        Assert.Equal("OK", await client.SetAsync(key, value));
        Assert.Equal(value, await client.GetAsync(key));
    }

    private async Task GetAndSetRandomValues(AsyncClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsLastSet(AsyncClient client) =>
        await GetAndSetRandomValues(client);

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetAndSetCanHandleNonASCIIUnicode(AsyncClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "שלום hello 汉字";
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsNull(AsyncClient client) =>
        Assert.Null(await client.GetAsync(Guid.NewGuid().ToString()));

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsEmptyString(AsyncClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = string.Empty;
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task HandleVeryLargeInput(AsyncClient client)
    {
        // TODO invesitage and fix
        if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
        {
            //"Flaky on MacOS"
            return;
        }

        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        const int expectedSize = 2 << 23;

        while (value.Length < expectedSize)
        {
            value += value;
        }
        await GetAndSetValues(client, key, value);
    }

    // This test is slow, but it caught timing and releasing issues in the past,
    // so it's being kept.
    [Theory(DisableDiscoveryEnumeration = true), Trait("duration", "long")]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public void ConcurrentOperationsWork(AsyncClient client)
    {
        // TODO investigate and fix
        if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
        {
            return;
        }

        List<Task> operations = [];

        for (int i = 0; i < 1000; ++i)
        {
            int index = i;
            operations.Add(Task.Run(async () =>
            {
                for (int i = 0; i < 1000; ++i)
                {
                    if ((i + index) % 2 == 0)
                    {
                        await GetAndSetRandomValues(client);
                    }
                    else
                    {
                        Assert.Null(await client.GetAsync(Guid.NewGuid().ToString()));
                    }
                }
            }));
        }

        Task.WaitAll([.. operations]);
    }
}
