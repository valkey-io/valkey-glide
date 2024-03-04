/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

using System.Diagnostics;

// Note: All IT should be in the same namespace
namespace tests.Integration;

[SetUpFixture]
public class IntegrationTestBase
{
    [OneTimeSetUp]
    public void SetUp()
    {
        // Stop all if weren't stopped on previous test run
        StopRedis(false);

        // Delete dirs if stop failed due to https://github.com/aws/glide-for-redis/issues/849
        Directory.Delete(Path.Combine(scriptDir, "clusters"), true);

        // Start cluster
        CLUSTER_PORTS = StartRedis(true);
        // Start standalone
        STANDALONE_PORTS = StartRedis(false);

        // Get redis version
        REDIS_VERSION = GetRedisVersion();

        TestContext.Progress.WriteLine($"Cluster ports = {string.Join(',', CLUSTER_PORTS)}");
        TestContext.Progress.WriteLine($"Standalone ports = {string.Join(',', STANDALONE_PORTS)}");
        TestContext.Progress.WriteLine($"Redis version = {REDIS_VERSION}");
    }

    [OneTimeTearDown]
    public void TearDown()
    {
        // Stop all
        StopRedis(true);
    }

    public static List<uint> STANDALONE_PORTS { get; private set; } = new();
    public static List<uint> CLUSTER_PORTS { get; private set; } = new();
    public static Version REDIS_VERSION { get; private set; } = new();

    private readonly string scriptDir;

    // Nunit requires a public default constructor. These variables would be set in SetUp method.
    public IntegrationTestBase()
    {
        STANDALONE_PORTS = new();
        CLUSTER_PORTS = new();
        REDIS_VERSION = new();

        string path = TestContext.CurrentContext.WorkDirectory;
        if (Path.GetFileName(path) != "csharp")
            throw new FileNotFoundException("`WorkDirectory` is incorrect or not defined. Please ensure the WorkDirectory was set by passing `-- NUnit.WorkDirectory=<path>` to the `dotnet test` command.");

        scriptDir = Path.Combine(path, "..", "utils");
    }


    public List<uint> StartRedis(bool cluster, bool tls = false, string? name = null)
    {
        var cmd = $"start {(cluster ? "--cluster-mode" : "-r 0")} {(tls ? " --tls" : "")} {(name != null ? " --prefix " + name : "")}";
        return ParsePortsFromOutput(RunClusterManager(cmd, false));
    }

    /// <summary>
    /// Stop <b>all</b> instances on the given <paramref name="name"/>.
    /// </summary>
    public void StopRedis(bool keepLogs, string? name = null)
    {
        var cmd = $"stop --prefix {name ?? "redis-cluster"} {(keepLogs ? "--keep-folder" : "")}";
        RunClusterManager(cmd, true);
    }


    private string RunClusterManager(string cmd, bool ignoreExitCode)
    {
        var info = new ProcessStartInfo
        {
            WorkingDirectory = scriptDir,
            FileName = "python3",
            Arguments = "cluster_manager.py " + cmd,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        var script = Process.Start(info);
        script?.WaitForExit();
        var error = script?.StandardError.ReadToEnd();
        var output = script?.StandardOutput.ReadToEnd();
        var exit_code = script?.ExitCode;

        TestContext.Progress.WriteLine($"cluster_manager.py stdout\n====\n{output}\n====\ncluster_manager.py stderr\n====\n{error}\n====\n");

        if (!ignoreExitCode && exit_code != 0)
            throw new ApplicationException($"cluster_manager.py script failed: exit code {exit_code}.");

        return output ?? "";
    }

    private List<uint> ParsePortsFromOutput(string output)
    {
        var ports = new List<uint>();
        foreach (var line in output.Split("\n"))
        {
            if (!line.StartsWith("CLUSTER_NODES="))
                continue;

            var addresses = line.Split("=")[1].Split(",");
            foreach (var address in addresses)
                ports.Add(uint.Parse(address.Split(":")[1]));
        }
        return ports;
    }

    private Version GetRedisVersion()
    {
        var info = new ProcessStartInfo
        {
            FileName = "redis-server",
            Arguments = "-v",
            UseShellExecute = false,
            RedirectStandardOutput = true,
        };
        var proc = Process.Start(info);
        proc?.WaitForExit();
        var output = proc?.StandardOutput.ReadToEnd() ?? "";

        // Redis server v=7.2.3 sha=00000000:0 malloc=jemalloc-5.3.0 bits=64 build=7504b1fedf883f2
        return new Version(output.Split(" ")[2].Split("=")[1]);
    }
}
