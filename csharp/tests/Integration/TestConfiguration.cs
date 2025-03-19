// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;
using System.Runtime.InteropServices;

using static Glide.ConnectionConfiguration;

[assembly: AssemblyFixture(typeof(Tests.Integration.TestConfiguration))]

namespace Tests.Integration;

public class TestConfiguration// : IDisposable
{
    public static bool IsMacOs => RuntimeInformation.IsOSPlatform(OSPlatform.OSX);

    public static List<(string host, ushort port)> STANDALONE_HOSTS { get; internal set; } = [];
    public static List<(string host, ushort port)> CLUSTER_HOSTS { get; internal set; } = [];
    public static Version SERVER_VERSION { get; internal set; } = new();

    public static StandaloneClientConfigurationBuilder DefaultClientConfig() =>
        new StandaloneClientConfigurationBuilder()
            .WithAddress(STANDALONE_HOSTS[0].host, STANDALONE_HOSTS[0].port);

    public static ClusterClientConfigurationBuilder DefaultClusterClientConfig() =>
        new ClusterClientConfigurationBuilder()
            .WithAddress(CLUSTER_HOSTS[0].host, CLUSTER_HOSTS[0].port)
            .WithRequestTimeout(10000);

    public static GlideClient DefaultStandaloneClient() => new(DefaultClientConfig().Build());
    public static GlideClusterClient DefaultClusterClient() => new(DefaultClusterClientConfig().Build());

    private static TheoryData<BaseClient> s_testClients = [];

    public static TheoryData<BaseClient> TestClients
    {
        get
        {
            if (s_testClients.Count == 0)
            {
                s_testClients = [(BaseClient)DefaultStandaloneClient(), (BaseClient)DefaultClusterClient()];
            }
            return s_testClients;
        }

        private set => s_testClients = value;
    }

    public static void ResetTestClients() => s_testClients = [];

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
        //StopServer(false);

        // Delete dirs if stop failed due to https://github.com/valkey-io/valkey-glide/issues/849
        // Not using `Directory.Exists` before deleting, because another process may delete the dir while IT is running.
        string clusterLogsDir = Path.Combine(_scriptDir, "clusters");
        try
        {
            Directory.Delete(clusterLogsDir, true);
        }
        catch (DirectoryNotFoundException) { }

        // Start cluster
        CLUSTER_HOSTS = [(host: "localhost", port: 7000)];// StartServer(true);
        // Start standalone
        STANDALONE_HOSTS = [(host: "localhost", port: 6379)]; //StartServer(false);
        // Get redis version
        SERVER_VERSION = new("8.0.0");// GetServerVersion();

        TestConsoleWriteLine($"Cluster hosts = {string.Join(", ", CLUSTER_HOSTS)}");
        TestConsoleWriteLine($"Standalone hosts = {string.Join(", ", STANDALONE_HOSTS)}");
        TestConsoleWriteLine($"Server version = {SERVER_VERSION}");
    }

    //~TestConfiguration() => Dispose();

    //public void Dispose() =>
    //    // Stop all
    //    StopServer(true);

    private readonly string _scriptDir;

    private void TestConsoleWriteLine(string message) =>
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

    //private static Version GetServerVersion()
    //{
    //    ProcessStartInfo info = new()
    //    {
    //        FileName = "redis-server",
    //        Arguments = "-v",
    //        UseShellExecute = false,
    //        RedirectStandardOutput = true,
    //    };
    //    Process? proc = Process.Start(info);
    //    proc?.WaitForExit();
    //    string output = proc?.StandardOutput.ReadToEnd() ?? "";

    //    // Redis response:
    //    // Redis server v=7.2.3 sha=00000000:0 malloc=jemalloc-5.3.0 bits=64 build=7504b1fedf883f2
    //    // Valkey response:
    //    // Server v=7.2.5 sha=26388270:0 malloc=jemalloc-5.3.0 bits=64 build=ea40bb1576e402d6
    //    return new Version(output.Split("v=")[1].Split(" ")[0]);
    //}
}
