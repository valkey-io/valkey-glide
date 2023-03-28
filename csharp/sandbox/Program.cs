using System.Collections.Concurrent;
using System.Diagnostics;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
using babushka;
using CommandLine;
using LinqStatistics;
using StackExchange.Redis;

public static class MainClass
{
    private static readonly Random randomizer = new();
   
    private static async Task GetAndSetRandomValues(IAsyncSocketClient client)
    {
        var key = (randomizer.Next(375000) + 1).ToString();
        var value = new string('0', 40);
        Console.WriteLine($"GetAndSetRandomValues - Set {key}={value}");
        await client.SetAsync(key, value);
        var result = await client.GetAsync(key);
        Console.WriteLine($"GetAndSetRandomValues - value {value} result {result} ");

        var key2 = (randomizer.Next(375000) + 1).ToString();
        var value2 = "777";
        Console.WriteLine($"GetAndSetRandomValues - Set {key2}={value2}");
        await client.SetAsync(key2, value2);
        var newValue = await client.IncrAsync(key2);
        var result2 = await client.GetAsync(key2);
        Console.WriteLine($"GetAndSetRandomValues - value {newValue} result {result2} ");
        var result3 = await client.MGetAsync(new string[] { key, key2 });
         Console.WriteLine($"GetAndSetRandomValues - Mget result {result3} ");
    }

    public static async Task Main(string[] args)
    {
        using (var client = await AsyncSocketClient.CreateSocketClient("localhost", 6379, false))
        {
            await GetAndSetRandomValues(client);
        }
    }
}
