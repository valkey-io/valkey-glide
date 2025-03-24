// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using BenchmarkDotNet.Configs;
using BenchmarkDotNet.Exporters;
using BenchmarkDotNet.Jobs;

namespace Valkey.Glide.Benchmark;

public class Config : ManualConfig
{
    public Config()
    {
        // Configure using default config
        Add(DefaultConfig.Instance);

        // Add exporters
        AddExporter(new Exporter());
        AddExporter(new RPlotExporter());

        // Add SolutionDir variable
        var solutionDir = Path.GetDirectoryName(Path.GetDirectoryName(Path.GetDirectoryName(
                              Path.GetDirectoryName(
                                  Path.GetDirectoryName(Path.GetDirectoryName(typeof(Program).Assembly.Location))))))
                          ?? throw new NullReferenceException("Failed to get solution directory");
        AddJob(Job.Default.WithArguments([new MsBuildArgument($"/p:SolutionDir={solutionDir}")]));
    }

}
