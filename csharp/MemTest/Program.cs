using Glide;
//*
using static Glide.ConnectionConfiguration;
//*/
namespace MemTest;

internal class Program
{
    static async Task Main(string[] args)
    {
        Console.WriteLine("Hello, World!");
        //*
        var config = new StandaloneClientConfigurationBuilder()
            .WithAddress("localhost", 6379)
            .WithAddress("hoho", 8080)
            .WithAddress("asdf")
            .WithAuthentication(null, "pwd")
            .With(Protocol.RESP3)
            .With(TlsMode.SecureTls)
            .WithRequestTimeout(23400)
            .With(ReadFrom.PreferReplica)
            .WithDataBaseId(42)
            .WithConnectionRetryStrategy(1, 2, 3)
            .WithClientName("pewpew")
            .Build();

        config = new StandaloneClientConfigurationBuilder()
            .WithAddress("localhost", 6379)
            .Build();
        //*/
        using (AsyncClient client = new(config))
        //using (AsyncClient client = new("localhost", 7000, false))
        {
            var key = Guid.NewGuid().ToString();
            var value = Guid.NewGuid().ToString();
            //await client.SetAsync(key, value);
            //var result = await client.GetAsync(key);
            //Console.WriteLine($"GetAsync returns {result}");
            try
            {
                var result = await client.Custom(["ping"]);
                Console.WriteLine($"ping returns {result}");
                result = await client.Custom(["ping", "pong", "pang"]);
                Console.WriteLine($"ping returns {result}");
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
        }

        Console.WriteLine("Hello, World!");
    }


}
