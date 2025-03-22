// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using BenchmarkDotNet.Exporters;
using BenchmarkDotNet.Loggers;
using BenchmarkDotNet.Reports;

namespace Valkey.Glide.Benchmark;

public class Exporter : ExporterBase
{
    public override void ExportToLog(Summary summary, ILogger logger)
    {
        // ToDo: Implement
    }
}
