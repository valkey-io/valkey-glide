// See https://aka.ms/new-console-template for more information


using BenchmarkDotNet.Configs;
using BenchmarkDotNet.Reports;
using BenchmarkDotNet.Running;

namespace Valkey.Glide.Benchmark;

internal class Program
{
    public static void Main(string[] args)
    {
        // _ = BenchmarkRunner.Run<NativeClient.Commands>(new Config());
        _ = BenchmarkRunner.Run<GlideClient.Commands>(new Config());
    }
}
