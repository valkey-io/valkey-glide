using System.Collections.Concurrent;
using System.Diagnostics;
using System.Text.Json;
using System.Text.Json.Serialization;
using babushka;
using CommandLine;
using LinqStatistics;
using StackExchange.Redis;

public static class MainClass
{
    private enum ChosenAction { GET_NON_EXISTING, GET_EXISTING, SET };

    public class CommandLineOptions
    {
        [Option('r', "resultsFile", Required = true, HelpText = "Set the file to which the JSON results are written.")]
        public string resultsFile { get; set; } = "";

        [Option('d', "dataSize", Required = true, HelpText = "The size of the sent data in bytes.")]
        public int dataSize { get; set; } = -1;

        [Option('c', "concurrentTasks", Required = true, HelpText = "The number of concurrent operations to perform.")]
        public IEnumerable<int> concurrentTasks { get; set; } = Enumerable.Empty<int>();

        [Option('l', "clients", Required = true, HelpText = "Which clients should run")]
        public string clientsToRun { get; set; } = "";
    }

    private const string HOST = "localhost";
    private const int PORT = 6379;
    private static readonly string ADDRESS = $"{HOST}:{PORT}";
    private static readonly string ADDRESS_WITH_REDIS_PREFIX = $"redis://{ADDRESS}";
    private const double PROB_GET = 0.8;

    private const double PROB_GET_EXISTING_KEY = 0.8;
    private const int SIZE_GET_KEYSPACE = 3750000; // 3.75 million
    private const int SIZE_SET_KEYSPACE = 3000000; // 3 million

    private static readonly Dictionary<ChosenAction, Dictionary<string, ConcurrentBag<double>>> actions_latencies = new() {
        {ChosenAction.GET_NON_EXISTING, new()},
        {ChosenAction.GET_EXISTING, new()},
        {ChosenAction.SET, new()},
    };
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
        return (randomizer.Next(SIZE_SET_KEYSPACE, SIZE_GET_KEYSPACE) + 1).ToString();
    }

    private static ChosenAction choose_action()
    {
        if (randomizer.NextDouble() > PROB_GET)
        {
            return ChosenAction.SET;
        }
        if (randomizer.NextDouble() > PROB_GET_EXISTING_KEY)
        {
            return ChosenAction.GET_NON_EXISTING;
        }
        return ChosenAction.GET_EXISTING;
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
            var action = choose_action();
            stopwatch.Start();
            switch (action)
            {
                case ChosenAction.GET_EXISTING:
                    await get(generate_key_set());
                    break;
                case ChosenAction.GET_NON_EXISTING:
                    await get(generate_key_get());
                    break;
                case ChosenAction.SET:
                    await set(generate_key_set(), data);
                    break;
            }
            stopwatch.Stop();
            var latency_list = actions_latencies[action][client_name];
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
        actions_latencies[ChosenAction.GET_NON_EXISTING][client_name] = new();
        actions_latencies[ChosenAction.GET_EXISTING][client_name] = new();
        actions_latencies[ChosenAction.SET][client_name] = new();
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

        var get_nonexisting_latency = actions_latencies[ChosenAction.GET_NON_EXISTING];
        var get_nonexisting_50 = calculate_latency(get_nonexisting_latency[client_name], 0.5);
        var get_nonexisting_90 = calculate_latency(get_nonexisting_latency[client_name], 0.9);
        var get_nonexisting_99 = calculate_latency(get_nonexisting_latency[client_name], 0.99);
        var get_nonexisting_std_dev = get_nonexisting_latency[client_name].StandardDeviation();

        var get_existing_latency = actions_latencies[ChosenAction.GET_EXISTING];
        var get_existing_50 = calculate_latency(get_existing_latency[client_name], 0.5);
        var get_existing_90 = calculate_latency(get_existing_latency[client_name], 0.9);
        var get_existing_99 = calculate_latency(get_existing_latency[client_name], 0.99);
        var get_existing_std_dev = get_existing_latency[client_name].StandardDeviation();

        var set_latency = actions_latencies[ChosenAction.SET];
        var set_50 = calculate_latency(set_latency[client_name], 0.5);
        var set_90 = calculate_latency(set_latency[client_name], 0.9);
        var set_99 = calculate_latency(set_latency[client_name], 0.99);
        var set_std_dev = set_latency[client_name].StandardDeviation();

        var result = new Dictionary<string, object>
        {
                    {"client", client_name},
                    {"num_of_tasks", num_of_concurrent_tasks},
                    {"data_size", data_size},
                    {"tps", tps},
                    {"get_non_existing_p50_latency", get_nonexisting_50},
                    {"get_non_existing_p90_latency", get_nonexisting_90},
                    {"get_non_existing_p99_latency", get_nonexisting_99},
                    {"get_non_existing_std_dev", get_nonexisting_std_dev},
                    {"get_existing_p50_latency", get_existing_50},
                    {"get_existing_p90_latency", get_existing_90},
                    {"get_existing_p99_latency", get_existing_99},
                    {"get_existing_std_dev", get_existing_std_dev},
                    {"set_p50_latency", set_50},
                    {"set_p90_latency", set_90},
                    {"set_p99_latency", set_99},
                    {"set_std_dev", set_std_dev},
        };
        bench_json_results.Add(result);
        bench_str_results.Add(
            $"client: {client_name}, concurrent_tasks: {num_of_concurrent_tasks}, data_size: {data_size}, TPS: {tps}, " +
            $"get_non_existing_p50: {get_nonexisting_50}, get_non_existing_p90: {get_nonexisting_90}, get_non_existing_p99: {get_nonexisting_99}, get_non_existing_std_dev: {get_nonexisting_std_dev}, " +
            $"get_existing_p50: {get_existing_50}, get_existing_p90: {get_existing_90}, get_existing_p99: {get_existing_99}, get_existing_std_dev: {get_existing_std_dev}, " +
            $"set_p50: {set_50}, set_p90: {set_90}, set_p99: {set_99}, set_std_dev: {set_std_dev}"
        );
    }

    private static async Task run_with_parameters(int total_commands,
        int data_size,
        int num_of_concurrent_tasks,
        string clientsToRun)
    {
        if (clientsToRun == "all" || clientsToRun == "ffi" || clientsToRun == "babushka")
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
        }

        if (clientsToRun == "all")
        {
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
    }

    private static int number_of_iterations(int num_of_concurrent_tasks)
    {
        return Math.Max(100000, num_of_concurrent_tasks * 10000);
    }

    public static async Task Main(string[] args)
    {
        CommandLineOptions options = new CommandLineOptions();
        Parser.Default
            .ParseArguments<CommandLineOptions>(args).WithParsed<CommandLineOptions>(parsed => { options = parsed; });

        var product = options.concurrentTasks.Select(concurrentTasks => (concurrentTasks, options.dataSize));
        foreach (var (concurrentTasks, dataSize) in product)
        {
            await run_with_parameters(number_of_iterations(concurrentTasks), dataSize, concurrentTasks, options.clientsToRun);
        }

        print_results(options.resultsFile);
    }
}
