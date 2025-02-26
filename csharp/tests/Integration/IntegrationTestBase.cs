// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;

using Glide;

using Xunit.Runner.Common;
using Xunit.Sdk;

// Note: All IT should be in the same namespace
namespace Tests.Integration;

public class IntegrationTestBase : IDisposable
{
    internal class TestConfiguration
    {
        public static List<(string, uint)> STANDALONE_HOSTS { get; internal set; } = [];
        public static List<(string, uint)> CLUSTER_HOSTS { get; internal set; } = [];
        public static Version SERVER_VERSION { get; internal set; } = new();

        public static AsyncClient DefaultStandaloneClient() => new(STANDALONE_HOSTS[0].Item1, STANDALONE_HOSTS[0].Item2, false);

        private static TheoryData<AsyncClient> s_testClients = [];

        public static TheoryData<AsyncClient> TestClients
        {
            get
            {
                if (s_testClients.Count == 0)
                {
                    s_testClients = [DefaultStandaloneClient()];
                }
                return s_testClients;
            }

            private set => s_testClients = value;
        }

        public static void ResetTestClients() => s_testClients = [];
    }

    private readonly IMessageSink _diagnosticMessageSink;

    public IntegrationTestBase(IMessageSink diagnosticMessageSink)
    {
        _diagnosticMessageSink = diagnosticMessageSink;
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
        string clusterLogsDir = Path.Combine(_scriptDir, "clusters");
        if (Directory.Exists(clusterLogsDir))
        {
            Directory.Delete(clusterLogsDir, true);
        }

        // Start cluster
        TestConfiguration.CLUSTER_HOSTS = StartServer(true);
        // Start standalone
        TestConfiguration.STANDALONE_HOSTS = StartServer(false);
        // Get redis version
        TestConfiguration.SERVER_VERSION = GetServerVersion();

        TestConsoleWriteLine($"Cluster hosts = {string.Join(", ", TestConfiguration.CLUSTER_HOSTS)}");
        TestConsoleWriteLine($"Standalone hosts = {string.Join(", ", TestConfiguration.STANDALONE_HOSTS)}");
        TestConsoleWriteLine($"Redis version = {TestConfiguration.SERVER_VERSION}");
    }

    public void Dispose() =>
        // Stop all
        StopServer(true);

    private readonly string _scriptDir;

    private void TestConsoleWriteLine(string message) =>
        _ = _diagnosticMessageSink.OnMessage(new DiagnosticMessage(message));

    internal List<(string, uint)> StartServer(bool cluster, bool tls = false, string? name = null)
    {
        string cmd = $"start {(cluster ? "--cluster-mode" : "-r 0")} {(tls ? " --tls" : "")} {(name != null ? " --prefix " + name : "")}";
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

    private static List<(string, uint)> ParseHostsFromOutput(string output)
    {
        List<(string, uint)> result = [];
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
                result.Add((parts[0], uint.Parse(parts[1])));
            }
        }
        return result;
    }

    private static Version GetServerVersion()
    {
        ProcessStartInfo info = new()
        {
            FileName = "redis-server",
            Arguments = "-v",
            UseShellExecute = false,
            RedirectStandardOutput = true,
        };
        Process? proc = Process.Start(info);
        proc?.WaitForExit();
        string output = proc?.StandardOutput.ReadToEnd() ?? "";

        // Redis response:
        // Redis server v=7.2.3 sha=00000000:0 malloc=jemalloc-5.3.0 bits=64 build=7504b1fedf883f2
        // Valkey response:
        // Server v=7.2.5 sha=26388270:0 malloc=jemalloc-5.3.0 bits=64 build=ea40bb1576e402d6
        return new Version(output.Split("v=")[1].Split(" ")[0]);
    }
}
