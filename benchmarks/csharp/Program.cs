using System.Diagnostics;
using System.Text.Json;
using System.Text.Json.Serialization;
using babushka;
using CommandLine;
using StackExchange.Redis;

public static class MainClass
{
    public class CommandLineOptions
    {
        [Option('r', "resultsFile", Required = true, HelpText = "Set the file to which the JSON results are written.")]
        public string resultsFile { get; set; }
    }

    private const string HOST = "localhost";
    private const int PORT = 6379;
    private static readonly string ADDRESS = $"{HOST}:{PORT}";
    private static readonly string ADDRESS_WITH_REDIS_PREFIX = $"redis://{ADDRESS}";
    private const double PROB_GET = 0.8;
    private const int SIZE_GET_KEYSPACE = 3750000; // 3.75 million
    private const int SIZE_SET_KEYSPACE = 3000000; // 3 million
    private static readonly Dictionary<string, List<double>> get_latency = new();
    private static readonly Dictionary<string, List<double>> set_latency = new();
    private static readonly Random randomizer = new();
    private static long counter = 0;
    private static readonly List<string> bench_str_results = new();
    private static readonly List<Dictionary<string, object>> bench_json_results = new();

    private static string generate_value(int size)
    {
        return new string('0', size);
    }

    private static string generate_key_set()
    {
        return (randomizer.Next(SIZE_SET_KEYSPACE) + 1).ToString();
    }
    private static string generate_key_get()
    {
        return (randomizer.Next(SIZE_GET_KEYSPACE) + 1).ToString();
    }

    private static bool should_get()
    {
        return randomizer.NextDouble() < PROB_GET;
    }

    /// copied from https://stackoverflow.com/questions/8137391/percentile-calculation
    private static double Percentile(double[] sequence, double excelPercentile)
    {
        Array.Sort(sequence);
        int N = sequence.Length;
        double n = (N - 1) * excelPercentile + 1;
        if (n == 1d) return sequence[0];
        else if (n == N) return sequence[N - 1];
        else
        {
            int k = (int)n;
            double d = n - k;
            return sequence[k - 1] + d * (sequence[k] - sequence[k - 1]);
        }
    }

    private static double calculate_latency(IEnumerable<double> latency_list, double percentile_point)
    {
        return Math.Round(Percentile(latency_list.ToArray(), percentile_point), 2);
    }

    private static void print_results(string resultsFile)
    {
        foreach (var res in bench_str_results.OrderBy(str => str))
        {
            Console.WriteLine(res);
        }
        using (FileStream createStream = File.Create(resultsFile))
        {
            JsonSerializer.Serialize(createStream, bench_json_results);
        }
    }

    private static async Task redis_benchmark(
        Func<string, Task<string>> get,
        Func<string, string, Task> set,
        string client_name,
        long total_commands,
        string data)
    {
        var stopwatch = new Stopwatch();
        do
        {
            var use_get = should_get();
            stopwatch.Start();
            if (use_get)
            {
                await get(generate_key_get());
            }
            else
            {
                await set(generate_key_set(), data);
            }
            stopwatch.Stop();
            var latency_list = (use_get ? get_latency : set_latency)[client_name];
            latency_list.Add(((double)stopwatch.ElapsedMilliseconds) / 1000);
        } while (Interlocked.Increment(ref counter) < total_commands);
    }

    private static async Task<long> create_bench_tasks(
        Func<string, Task<string>> get,
        Func<string, string, Task> set,
        string client_name,
        int total_commands,
        string data,
        int num_of_concurrent_tasks
    )
    {
        counter = 0;
        get_latency[client_name] = new();
        set_latency[client_name] = new();
        var stopwatch = Stopwatch.StartNew();
        var running_tasks = new List<Task>();
        for (var i = 0; i < num_of_concurrent_tasks; i++)
        {
            running_tasks.Add(
                redis_benchmark(get, set, client_name, total_commands, data)
            );
        }
        await Task.WhenAll(running_tasks);
        stopwatch.Stop();
        return stopwatch.ElapsedMilliseconds;
    }

    private static async Task run_client(
        Func<string, Task<string>> get,
        Func<string, string, Task> set,
        string client_name,
        int total_commands,
        int data_size,
        int num_of_concurrent_tasks
)
    {
        var data = generate_value(data_size);
        var ellapsed_milliseconds = await create_bench_tasks(
            get, set,
            client_name,
            total_commands,
            data,
            num_of_concurrent_tasks
        );
        var tps = Math.Round((double)counter / ((double)ellapsed_milliseconds / 1000));
        var get_50 = calculate_latency(get_latency[client_name], 0.5);
        var get_90 = calculate_latency(get_latency[client_name], 0.9);
        var get_99 = calculate_latency(get_latency[client_name], 0.99);
        var set_50 = calculate_latency(set_latency[client_name], 0.5);
        var set_90 = calculate_latency(set_latency[client_name], 0.9);
        var set_99 = calculate_latency(set_latency[client_name], 0.99);
        var result = new Dictionary<string, object>
        {
                    {"client", client_name},
                    {"num_of_tasks", num_of_concurrent_tasks},
                    {"data_size", data_size},
                    {"tps", tps},
                    {"latency", new Dictionary<string, object>
        {
                    {"get_50", get_50},
                    {"get_90", get_90},
                    {"get_99", get_99},
                    {"set_50", set_50},
                    {"set_90", set_90},
                    {"set_99", set_99},
        }},
        };
        bench_json_results.Add(result);
        bench_str_results.Add(
            $"client: {client_name}, concurrent_tasks: {num_of_concurrent_tasks}, data_size: {data_size}, TPS: {tps}, get_p50: {get_50}, get_p90: {get_90}, get_p99: {get_99}, set_p50: {set_50}, set_p90: {set_90}, set_p99: {set_99}"
        );
    }

    private static async Task run_with_parameters(int total_commands,
        int data_size,
        int num_of_concurrent_tasks)
    {
        var babushka_client = new AsyncClient(ADDRESS_WITH_REDIS_PREFIX);
        await run_client(
            async (key) => await babushka_client.GetAsync(key),
            async (key, value) => await babushka_client.SetAsync(key, value),
            "babushka FFI",
            total_commands,
            num_of_concurrent_tasks,
            data_size
        );

        using (var connection = ConnectionMultiplexer.Connect(ADDRESS))
        {
            var db = connection.GetDatabase();
            await run_client(
                async (key) => (await db.StringGetAsync(key)).ToString(),
                (key, value) => db.StringSetAsync(key, value),
                "StackExchange.Redis",
                total_commands,
                num_of_concurrent_tasks,
                data_size
            );
        }
    }

    public static async Task Main(string[] args)
    {
        CommandLineOptions options = null;
        Parser.Default
            .ParseArguments<CommandLineOptions>(args).WithParsed<CommandLineOptions>(parsed => { options = parsed; });

        await run_with_parameters(100000, 1, 100);
        await run_with_parameters(100000, 10, 100);
        await run_with_parameters(1000000, 100, 100);
        await run_with_parameters(5000000, 1000, 100);
        await run_with_parameters(100000, 1, 4000);
        await run_with_parameters(100000, 10, 4000);
        await run_with_parameters(1000000, 100, 4000);
        await run_with_parameters(5000000, 1000, 4000);

        print_results(options.resultsFile);
    }
}
