namespace tests;

using babushka;

// TODO - need to start a new redis server for each test?
public class AsyncSocketClientTests
{
    [OneTimeSetUp]
    public void Setup()
    {
        babushka.Logger.SetConfig(babushka.Level.Info);
    }
    static Random randomizer = new();

    private async Task GetAndSetRandomValues(AsyncSocketClient client)
    {
        var key = (randomizer.Next(375000) + 1).ToString();
        var value = new string('0', 4500);
        await client.SetAsync(key, value);
        var result = await client.GetAsync(key);
        Assert.That(result, Is.EqualTo(value));
    }

    [Test, Timeout(200)]
    public async Task GetReturnsLastSet()
    {
        using (var client = await AsyncSocketClient.CreateSocketClient("redis://localhost:6379"))
        {
            await GetAndSetRandomValues(client);
        }
    }

    [Test, Timeout(200)]
    public async Task GetAndSetCanHandleNonASCIIUnicode()
    {
        using (var client = await AsyncSocketClient.CreateSocketClient("redis://localhost:6379"))
        {
            var key = Guid.NewGuid().ToString();
            var value = "שלום hello 汉字";
            await client.SetAsync(key, value);
            var result = await client.GetAsync(key);
            Assert.That(result, Is.EqualTo(value));
        }
    }

    [Test, Timeout(200)]
    public async Task GetReturnsNull()
    {
        using (var client = await AsyncSocketClient.CreateSocketClient("redis://localhost:6379"))
        {
            var result = await client.GetAsync(Guid.NewGuid().ToString());
            Assert.That(result, Is.EqualTo(null));
        }
    }

    [Test, Timeout(200)]
    public async Task GetReturnsEmptyString()
    {
        using (var client = await AsyncSocketClient.CreateSocketClient("redis://localhost:6379"))
        {
            var key = Guid.NewGuid().ToString();
            var value = "";
            await client.SetAsync(key, value);
            var result = await client.GetAsync(key);
            Assert.That(result, Is.EqualTo(value));
        }
    }

    [Test, Timeout(20000)]
    public async Task HandleVeryLargeInput()
    {
        using (var client = await AsyncSocketClient.CreateSocketClient("redis://localhost:6379"))
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
    [Test, Timeout(5000)]
    public async Task ConcurrentOperationsWork()
    {
        using (var client = await AsyncSocketClient.CreateSocketClient("redis://localhost:6379"))
        {
            var operations = new List<Task>();

            for (int i = 0; i < 100; ++i)
            {
                var index = i;
                operations.Add(Task.Run(async () =>
                {
                    for (int i = 0; i < 100; ++i)
                    {
                        if ((i + index) % 5 == 0)
                        {
                            var result = await client.GetAsync(Guid.NewGuid().ToString());
                            Assert.That(result, Is.EqualTo(null));
                        }
                        else
                        {
                            await GetAndSetRandomValues(client);
                        }
                    }
                }));
            }

            Task.WaitAll(operations.ToArray());
        }
    }
}
