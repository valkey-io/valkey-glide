// See https://aka.ms/new-console-template for more information


using BenchmarkDotNet.Running;
using Valkey.Glide.Benchmark;

var summary = BenchmarkRunner.Run<BlockingVsTask>();
