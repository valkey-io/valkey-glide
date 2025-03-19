// See https://aka.ms/new-console-template for more information


using BenchmarkDotNet.Configs;
using BenchmarkDotNet.Jobs;
using BenchmarkDotNet.Reports;
using BenchmarkDotNet.Running;
using Valkey.Glide.Benchmark;

var config = DefaultConfig.Instance;
var solutionDir = Path.GetDirectoryName(Path.GetDirectoryName(Path.GetDirectoryName(
    Path.GetDirectoryName(Path.GetDirectoryName(Path.GetDirectoryName(typeof(Program).Assembly.Location))))))
    ?? throw new NullReferenceException("Failed to get solution directory");
config = config.AddJob(Job.Default.WithArguments([new MsBuildArgument($"/p:SolutionDir={solutionDir}")]));
Summary? summary = BenchmarkRunner.Run<BlockingVsTask>(config);
