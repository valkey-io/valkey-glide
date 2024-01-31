/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

namespace tests;

using Glide;

// TODO - need to start a new redis server for each test?
public class AsyncClientTests
{
    [OneTimeSetUp]
    public void Setup()
    {
        Glide.Logger.SetLoggerConfig(Glide.Level.Info);
    }

    private async Task GetAndSetRandomValues(AsyncClient client)
    {
        var key = Guid.NewGuid().ToString();
        var value = Guid.NewGuid().ToString();
        await client.SetAsync(key, value);
        var result = await client.GetAsync(key);
        Assert.That(result, Is.EqualTo(value));
    }

    [Test]
    public async Task GetReturnsLastSet()
    {
        using (var client = new AsyncClient("localhost", 6379, false))
        {
            await GetAndSetRandomValues(client);
        }
    }

    [Test]
    public async Task GetAndSetCanHandleNonASCIIUnicode()
    {
        using (var client = new AsyncClient("localhost", 6379, false))
        {
            var key = Guid.NewGuid().ToString();
            var value = "שלום hello 汉字";
            await client.SetAsync(key, value);
            var result = await client.GetAsync(key);
            Assert.That(result, Is.EqualTo(value));
        }
    }

    [Test]
    public async Task GetReturnsNull()
    {
        using (var client = new AsyncClient("localhost", 6379, false))
        {
            var result = await client.GetAsync(Guid.NewGuid().ToString());
            Assert.That(result, Is.EqualTo(null));
        }
    }

    [Test]
    public async Task GetReturnsEmptyString()
    {
        using (var client = new AsyncClient("localhost", 6379, false))
        {
            var key = Guid.NewGuid().ToString();
            var value = "";
            await client.SetAsync(key, value);
            var result = await client.GetAsync(key);
            Assert.That(result, Is.EqualTo(value));
        }
    }

    [Test]
    public async Task HandleVeryLargeInput()
    {
        using (var client = new AsyncClient("localhost", 6379, false))
        {
            var key = Guid.NewGuid().ToString();
            var value = Guid.NewGuid().ToString();
            const int EXPECTED_SIZE = 2 << 23;
            while (value.Length < EXPECTED_SIZE)
            {
                value += value;
            }
            await client.SetAsync(key, value);
            var result = await client.GetAsync(key);
            Assert.That(result, Is.EqualTo(value));
        }
    }

    // This test is slow and hardly a unit test, but it caught timing and releasing issues in the past,
    // so it's being kept.
    [Test]
    public void ConcurrentOperationsWork()
    {
        using (var client = new AsyncClient("localhost", 6379, false))
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
