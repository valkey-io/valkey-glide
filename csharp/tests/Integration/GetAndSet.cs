/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

namespace tests.Integration;

using System.Runtime.InteropServices;

using Glide;

using static tests.Integration.IntegrationTestBase;

public class GetAndSet
{
    private async Task GetAndSetValues(AsyncClient client, string key, string value)
    {
        var setResult = await client.SetAsync(key, value);
        Assert.That(setResult, Is.EqualTo("OK"));
        var result = await client.GetAsync(key);
        Assert.That(result, Is.EqualTo(value));
    }

    private async Task GetAndSetRandomValues(AsyncClient client)
    {
        var key = Guid.NewGuid().ToString();
        var value = Guid.NewGuid().ToString();
        await GetAndSetValues(client, key, value);
    }

    [Test]
    public async Task GetReturnsLastSet()
    {
        using (var client = new AsyncClient("localhost", TestConfiguration.STANDALONE_PORTS[0], false))
        {
            await GetAndSetRandomValues(client);
        }
    }

    [Test]
    public async Task GetAndSetCanHandleNonASCIIUnicode()
    {
        using (var client = new AsyncClient("localhost", TestConfiguration.STANDALONE_PORTS[0], false))
        {
            var key = Guid.NewGuid().ToString();
            var value = "שלום hello 汉字";
            await GetAndSetValues(client, key, value);
        }
    }

    [Test]
    public async Task GetReturnsNull()
    {
        using (var client = new AsyncClient("localhost", TestConfiguration.STANDALONE_PORTS[0], false))
        {
            var result = await client.GetAsync(Guid.NewGuid().ToString());
            Assert.That(result, Is.EqualTo(null));
        }
    }

    [Test]
    public async Task GetReturnsEmptyString()
    {
        using (var client = new AsyncClient("localhost", TestConfiguration.STANDALONE_PORTS[0], false))
        {
            var key = Guid.NewGuid().ToString();
            var value = "";
            await GetAndSetValues(client, key, value);
        }
    }

    [Test]
    public async Task HandleVeryLargeInput()
    {
        // TODO invesitage and fix
        if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
            Assert.Ignore("Flaky on MacOS");
        using (var client = new AsyncClient("localhost", TestConfiguration.STANDALONE_PORTS[0], false))
        {
            var key = Guid.NewGuid().ToString();
            var value = Guid.NewGuid().ToString();
            const int EXPECTED_SIZE = 2 << 23;
            while (value.Length < EXPECTED_SIZE)
            {
                value += value;
            }
            await GetAndSetValues(client, key, value);
        }
    }

    // This test is slow and hardly a unit test, but it caught timing and releasing issues in the past,
    // so it's being kept.
    [Test]
    public void ConcurrentOperationsWork()
    {
        using (var client = new AsyncClient("localhost", TestConfiguration.STANDALONE_PORTS[0], false))
        {
            var operations = new List<Task>();

            for (int i = 0; i < 1000; ++i)
            {
                var index = i;
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
                            var result = await client.GetAsync(Guid.NewGuid().ToString());
                            Assert.That(result, Is.EqualTo(null));
                        }
                    }
                }));
            }

            Task.WaitAll(operations.ToArray());
        }
    }
}
