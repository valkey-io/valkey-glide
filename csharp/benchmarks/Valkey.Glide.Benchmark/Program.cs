// See https://aka.ms/new-console-template for more information


using BenchmarkDotNet.Reports;
using BenchmarkDotNet.Running;
using Valkey.Glide.Benchmark;

Summary? summary = BenchmarkRunner.Run<BlockingVsTask>();
