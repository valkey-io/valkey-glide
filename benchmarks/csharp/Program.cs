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

        [Option('h', "host", Required = true, HelpText = "What host to target")]
        public string host { get; set; } = "";

        [Option('C', "clientCount", Required = true, HelpText = "Number of clients to run concurrently")]
        public IEnumerable<int> clientCount { get; set; } = Enumerable.Empty<int>();

        [Option('t', "tls", Default = false, HelpText = "Should benchmark a TLS server")]
        public bool tls { get; set; } = false;
    }

    private const int PORT = 6379;
    private static string getAddress(string host)
    {
        return $"{host}:{PORT}";
    }

    private static string getAddressForStackExchangeRedis(string host, bool useTLS)
    {
        return $"{getAddress(host)},ssl={useTLS}";
    }

    private static string getAddressWithRedisPrefix(string host, bool useTLS)
    {
        var protocol = useTLS ? "rediss" : "redis";
        return $"{protocol}://{getAddress(host)}";
    }
    private const double PROB_GET = 0.8;

    private const double PROB_GET_EXISTING_KEY = 0.8;
    private const int SIZE_GET_KEYSPACE = 3750000; // 3.75 million
    private const int SIZE_SET_KEYSPACE = 3000000; // 3 million

    private static readonly Random randomizer = new();
    private static long started_tasks_counter = 0;
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
        using (FileStream createStream = File.Create(resultsFile))
        {
            JsonSerializer.Serialize(createStream, bench_json_results);
        }
    }

    private static async Task redis_benchmark(
        ClientWrapper[] clients,
        long total_commands,
        string data,
        Dictionary<ChosenAction, ConcurrentBag<double>> action_latencies)
    {
        var stopwatch = new Stopwatch();
        do
        {
            Interlocked.Increment(ref started_tasks_counter);
            var index = (int)(started_tasks_counter % clients.Length);
            var client = clients[index];
            var action = choose_action();
            stopwatch.Start();
            switch (action)
            {
                case ChosenAction.GET_EXISTING:
                    await client.get(generate_key_set());
                    break;
                case ChosenAction.GET_NON_EXISTING:
                    await client.get(generate_key_get());
                    break;
                case ChosenAction.SET:
                    await client.set(generate_key_set(), data);
                    break;
            }
            stopwatch.Stop();
            var latency_list = action_latencies[action];
            latency_list.Add(((double)stopwatch.ElapsedMilliseconds) / 1000);
        } while (started_tasks_counter < total_commands);
    }

    private static async Task<long> create_bench_tasks(
        ClientWrapper[] clients,
        int total_commands,
        string data,
        int num_of_concurrent_tasks,
        Dictionary<ChosenAction, ConcurrentBag<double>> action_latencies
    )
    {
        started_tasks_counter = 0;
        var stopwatch = Stopwatch.StartNew();
        var running_tasks = new List<Task>();
        for (var i = 0; i < num_of_concurrent_tasks; i++)
        {
            running_tasks.Add(
                redis_benchmark(clients, total_commands, data, action_latencies)
            );
        }
        await Task.WhenAll(running_tasks);
        stopwatch.Stop();
        return stopwatch.ElapsedMilliseconds;
    }

    private static Dictionary<string, object> latency_results(
        string prefix,
        ConcurrentBag<double> latencies
    )
    {
        return new Dictionary<string, object>
        {
            {prefix + "_p50_latency", calculate_latency(latencies, 0.5)},
            {prefix + "_p90_latency", calculate_latency(latencies, 0.9)},
            {prefix + "_p99_latency", calculate_latency(latencies, 0.99)},
            {prefix + "_average_latency", Math.Round(latencies.Average(), 3)},
            {prefix + "_std_dev", latencies.StandardDeviation()},
        };
    }

    private static async Task run_clients(
        ClientWrapper[] clients,
        string client_name,
        int total_commands,
        int data_size,
        int num_of_concurrent_tasks
    )
    {
        Console.WriteLine($"Starting {client_name} data size: {data_size} concurrency: {num_of_concurrent_tasks} client count: {clients.Length} {DateTime.UtcNow.ToString("HH:mm:ss")}");
        var action_latencies = new Dictionary<ChosenAction, ConcurrentBag<double>>() {
            {ChosenAction.GET_NON_EXISTING, new()},
            {ChosenAction.GET_EXISTING, new()},
            {ChosenAction.SET, new()},
        };
        var data = generate_value(data_size);
        var elapsed_milliseconds = await create_bench_tasks(
            clients,
            total_commands,
            data,
            num_of_concurrent_tasks,
            action_latencies
        );
        var tps = Math.Round((double)started_tasks_counter / ((double)elapsed_milliseconds / 1000));

        var get_non_existing_latencies = action_latencies[ChosenAction.GET_NON_EXISTING];
        var get_non_existing_latency_results = latency_results("get_non_existing", get_non_existing_latencies);

        var get_existing_latencies = action_latencies[ChosenAction.GET_EXISTING];
        var get_existing_latency_results = latency_results("get_existing", get_existing_latencies);

        var set_latencies = action_latencies[ChosenAction.SET];
        var set_latency_results = latency_results("set", set_latencies);

        var result = new Dictionary<string, object>
        {
            {"client", client_name},
            {"num_of_tasks", num_of_concurrent_tasks},
            {"data_size", data_size},
            {"tps", tps},
            {"clientCount", clients.Length},
            {"is_cluster", "false"}
        };
        result = result
            .Concat(get_existing_latency_results)
            .Concat(get_non_existing_latency_results)
            .Concat(set_latency_results)
            .ToDictionary(pair => pair.Key, pair => pair.Value);
        bench_json_results.Add(result);
    }

    private class ClientWrapper : IDisposable
    {
        internal ClientWrapper(Func<string, Task<string?>> get, Func<string, string, Task> set, Action disposalFunction)
        {
            this.get = get;
            this.set = set;
            this.disposalFunction = disposalFunction;
        }

        public void Dispose()
        {
            this.disposalFunction();
        }

        internal Func<string, Task<string?>> get;
        internal Func<string, string, Task> set;

        private Action disposalFunction;
    }

    private async static Task<ClientWrapper[]> createClients(int clientCount,
        Func<Task<(Func<string, Task<string?>>,
                   Func<string, string, Task>,
                   Action)>> clientCreation)
    {
        var tasks = Enumerable.Range(0, clientCount).Select(async (_) =>
        {
            var tuple = await clientCreation();
            return new ClientWrapper(tuple.Item1, tuple.Item2, tuple.Item3);
        });
        return await Task.WhenAll(tasks);
    }

    private static async Task run_with_parameters(int total_commands,
        int data_size,
        int num_of_concurrent_tasks,
        string clientsToRun,
        string host,
        int clientCount,
        bool useTLS)
    {
        if (clientsToRun == "all" || clientsToRun == "ffi" || clientsToRun == "babushka")
        {
            var clients = await createClients(clientCount, () =>
            {
                var babushka_client = new AsyncClient(getAddressWithRedisPrefix(host, useTLS));
                return Task.FromResult<(Func<string, Task<string?>>, Func<string, string, Task>, Action)>(
                    (async (key) => await babushka_client.GetAsync(key),
                     async (key, value) => await babushka_client.SetAsync(key, value),
                     () => babushka_client.Dispose()));
            });

            await run_clients(
                clients,
                "babushka FFI",
                total_commands,
                data_size,
                num_of_concurrent_tasks
            );
        }


        if (clientsToRun == "all" || clientsToRun == "socket" || clientsToRun == "babushka")
        {
            var clients = await createClients(clientCount, async () =>
                {
                    var babushka_client = await AsyncSocketClient.CreateSocketClient(getAddressWithRedisPrefix(host, useTLS));
                    return (async (key) => await babushka_client.GetAsync(key),
                            async (key, value) => await babushka_client.SetAsync(key, value),
                            () => babushka_client.Dispose());
                });
            await run_clients(
                clients,
                "babushka socket",
                total_commands,
                data_size,
                num_of_concurrent_tasks
            );

            foreach (var client in clients)
            {
                client.Dispose();
            }
        }

        if (clientsToRun == "all")
        {
            var clients = await createClients(clientCount, () =>
                {
                    var connection = ConnectionMultiplexer.Connect(getAddressForStackExchangeRedis(host, useTLS));
                    var db = connection.GetDatabase();
                    return Task.FromResult<(Func<string, Task<string?>>, Func<string, string, Task>, Action)>(
                        (async (key) => await db.StringGetAsync(key),
                         async (key, value) => await db.StringSetAsync(key, value),
                         () => connection.Dispose()));
                });
            await run_clients(
                clients,
                "StackExchange.Redis",
                total_commands,
                data_size,
                num_of_concurrent_tasks
            );

            foreach (var client in clients)
            {
                client.Dispose();
            }
        }
    }

    private static int number_of_iterations(int num_of_concurrent_tasks)
    {
        return Math.min(Math.Max(100000, num_of_concurrent_tasks * 10000), 10000000);
    }

    public static async Task Main(string[] args)
    {
        // Demo - Setting the internal logger to log every log that has a level of info and above, and save the logs to the first.log file.
        Logger.SetLoggerConfig(Level.Info, "first.log");
        CommandLineOptions options = new CommandLineOptions();
        Parser.Default
            .ParseArguments<CommandLineOptions>(args).WithParsed<CommandLineOptions>(parsed => { options = parsed; });

        var product = options.concurrentTasks.SelectMany(concurrentTasks =>
            options.clientCount.Select(clientCount => (concurrentTasks: concurrentTasks, dataSize: options.dataSize, clientCount: clientCount))).Where(tuple => tuple.concurrentTasks >= tuple.clientCount);
        foreach (var (concurrentTasks, dataSize, clientCount) in product)
        {
            await run_with_parameters(number_of_iterations(concurrentTasks), dataSize, concurrentTasks, options.clientsToRun, options.host, clientCount, options.tls);
        }

        print_results(options.resultsFile);
    }
}
