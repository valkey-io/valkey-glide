// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;

using Valkey.Glide.IntegrationTests;

using static Valkey.Glide.ConnectionConfiguration;

[assembly: AssemblyFixture(typeof(TestConfiguration))]

namespace Valkey.Glide.IntegrationTests;

public class TestConfiguration : IDisposable
{
    public static List<(string host, ushort port)> STANDALONE_HOSTS { get; internal set; } = [];
    public static List<(string host, ushort port)> CLUSTER_HOSTS { get; internal set; } = [];
    public static Version SERVER_VERSION { get; internal set; } = new();

    public static StandaloneClientConfigurationBuilder DefaultClientConfig() =>
        new StandaloneClientConfigurationBuilder()
            .WithAddress(STANDALONE_HOSTS[0].host, STANDALONE_HOSTS[0].port);

    public static ClusterClientConfigurationBuilder DefaultClusterClientConfig() =>
        new ClusterClientConfigurationBuilder()
            .WithAddress(CLUSTER_HOSTS[0].host, CLUSTER_HOSTS[0].port);

    public static GlideClient DefaultStandaloneClientWithExtraTimeout()
    => GlideClient.CreateClient(DefaultClientConfig().WithRequestTimeout(1000).Build()).GetAwaiter().GetResult();

    public static GlideClusterClient DefaultClusterClientWithExtraTimeout()
        => GlideClusterClient.CreateClient(DefaultClusterClientConfig().WithRequestTimeout(1000).Build()).GetAwaiter().GetResult();

    public static GlideClient DefaultStandaloneClient()
        => GlideClient.CreateClient(DefaultClientConfig().Build()).GetAwaiter().GetResult();

    public static GlideClusterClient DefaultClusterClient()
        => GlideClusterClient.CreateClient(DefaultClusterClientConfig().Build()).GetAwaiter().GetResult();

    public static TheoryData<BaseClient> TestClients
    {
        get
        {
            if (field.Count == 0)
            {
                field = [(BaseClient)DefaultStandaloneClientWithExtraTimeout(), (BaseClient)DefaultClusterClientWithExtraTimeout()];
            }
            return field;
        }

        private set;
    } = [];

    public static void ResetTestClients()
    {
        foreach (TheoryDataRow<BaseClient> data in TestClients)
        {
            data.Data.Dispose();
        }
        TestClients = [];
    }

    public TestConfiguration()
    {
        string? projectDir = Directory.GetCurrentDirectory();
        while (!(Path.GetFileName(projectDir) == "csharp" || projectDir == null))
        {
            projectDir = Path.GetDirectoryName(projectDir);
        }

        if (projectDir == null)
        {
            throw new FileNotFoundException("Can't detect the project dir. Are you running tests from `csharp` directory?");
        }

        _scriptDir = Path.Combine(projectDir, "..", "utils");

        // Stop all if weren't stopped on previous test run
        StopServer(false);

        // Delete dirs if stop failed due to https://github.com/valkey-io/valkey-glide/issues/849
        // Not using `Directory.Exists` before deleting, because another process may delete the dir while IT is running.
        string clusterLogsDir = Path.Combine(_scriptDir, "clusters");
        try
        {
            Directory.Delete(clusterLogsDir, true);
        }
        catch (DirectoryNotFoundException) { }

        // Start cluster
        CLUSTER_HOSTS = StartServer(true);
        // Start standalone
        STANDALONE_HOSTS = StartServer(false);
        // Get redis version
        SERVER_VERSION = GetServerVersion();

        TestConsoleWriteLine($"Cluster hosts = {string.Join(", ", CLUSTER_HOSTS)}");
        TestConsoleWriteLine($"Standalone hosts = {string.Join(", ", STANDALONE_HOSTS)}");
        TestConsoleWriteLine($"Server version = {SERVER_VERSION}");
    }

    ~TestConfiguration() => Dispose();

    public void Dispose()
    {
        ResetTestClients();
        // Stop all
        StopServer(true);
    }

    private readonly string _scriptDir;

    private static void TestConsoleWriteLine(string message) =>
        TestContext.Current.SendDiagnosticMessage(message);

    internal List<(string host, ushort port)> StartServer(bool cluster, bool tls = false, string? name = null)
    {
        string cmd = $"start {(cluster ? "--cluster-mode" : "")} {(tls ? " --tls" : "")} {(name != null ? " --prefix " + name : "")}";
        return ParseHostsFromOutput(RunClusterManager(cmd, false));
    }

    /// <summary>
    /// Stop <b>all</b> instances on the given <paramref name="name"/>.
    /// </summary>
    internal void StopServer(bool keepLogs, string? name = null)
    {
        string cmd = $"stop --prefix {name ?? "cluster"} {(keepLogs ? "--keep-folder" : "")}";
        _ = RunClusterManager(cmd, true);
    }

    private string RunClusterManager(string cmd, bool ignoreExitCode)
    {
        ProcessStartInfo info = new()
        {
            WorkingDirectory = _scriptDir,
            FileName = "python3",
            Arguments = "cluster_manager.py " + cmd,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        Process? script = Process.Start(info);
        script?.WaitForExit();
        string? error = script?.StandardError.ReadToEnd();
        string? output = script?.StandardOutput.ReadToEnd();
        int? exit_code = script?.ExitCode;

        TestConsoleWriteLine($"cluster_manager.py stdout\n====\n{output}\n====\ncluster_manager.py stderr\n====\n{error}\n====\n");

        return !ignoreExitCode && exit_code != 0
            ? throw new ApplicationException($"cluster_manager.py script failed: exit code {exit_code}.")
            : output ?? "";
    }

    private static List<(string host, ushort port)> ParseHostsFromOutput(string output)
    {
        List<(string host, ushort port)> hosts = [];
        foreach (string line in output.Split("\n"))
        {
            if (!line.StartsWith("CLUSTER_NODES="))
            {
                continue;
            }

            string[] addresses = line.Split("=")[1].Split(",");
            foreach (string address in addresses)
            {
                string[] parts = address.Split(":");
                hosts.Add((parts[0], ushort.Parse(parts[1])));
            }
        }
        return hosts;
    }

    private static Version GetServerVersion()
    {
        Exception? err = null;
        if (STANDALONE_HOSTS.Count > 0)
        {
            GlideClient client = DefaultStandaloneClient();
            try
            {
                return TryGetVersion(client);
            }
            catch (Exception e)
            {
                err = e;
            }
        }
        if (CLUSTER_HOSTS.Count > 0)
        {
            GlideClusterClient client = DefaultClusterClient();
            try
            {
                return TryGetVersion(client);
            }
            catch (Exception e)
            {
                if (err is not null)
                {
                    TestConsoleWriteLine(err.ToString());
                }
                TestConsoleWriteLine(e.ToString());
                throw;
            }
        }
        throw new Exception("No servers are given");
    }

    private static Version TryGetVersion(BaseClient client)
    {
        string info = client.GetType() == typeof(GlideClient)
            ? ((GlideClient)client).Info().GetAwaiter().GetResult()
            : ((GlideClusterClient)client).Info(Route.Random).GetAwaiter().GetResult().SingleValue;
        string[] lines = info.Split();
        string line = lines.FirstOrDefault(l => l.Contains("valkey_version")) ?? lines.First(l => l.Contains("redis_version"));
        return new(line.Split(':')[1]);
    }
}
