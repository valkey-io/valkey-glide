// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using BenchmarkDotNet.Analysers;
using BenchmarkDotNet.Columns;
using BenchmarkDotNet.Configs;
using BenchmarkDotNet.Exporters;
using BenchmarkDotNet.Exporters.Csv;
using BenchmarkDotNet.Jobs;
using BenchmarkDotNet.Loggers;
using BenchmarkDotNet.Reports;
using BenchmarkDotNet.Validators;

namespace Valkey.Glide.Benchmark;

public class Config : ManualConfig
{
    public Config()
    {
        // Add default config things
        foreach (var it in DefaultExporters()) AddExporter(it);
        foreach (var it in DefaultAnalysers()) AddAnalyser(it);
        foreach (var it in DefaultLoggers()) AddLogger(it);
        foreach (var it in DefaultValidators()) AddValidator(it);
        foreach (var it in DefaultColumnProviders()) AddColumnProvider(it);

        AddExporter(new Exporter());

        var solutionDir = Path.GetDirectoryName(Path.GetDirectoryName(Path.GetDirectoryName(
                              Path.GetDirectoryName(
                                  Path.GetDirectoryName(Path.GetDirectoryName(typeof(Program).Assembly.Location))))))
                          ?? throw new NullReferenceException("Failed to get solution directory");
        AddJob(Job.Default.WithArguments([new MsBuildArgument($"/p:SolutionDir={solutionDir}")]));
    }

    public IEnumerable<IColumnProvider> DefaultColumnProviders() => BenchmarkDotNet.Columns.DefaultColumnProviders.Instance;
    private IEnumerable<IExporter> DefaultExporters()
    {
        // Now that we can specify exporters on the cmd line (e.g. "exporters=html,stackoverflow"),
        // we should have less enabled by default and then users can turn on the ones they want
        yield return CsvExporter.Default;
        yield return MarkdownExporter.GitHub;
        yield return HtmlExporter.Default;
    }

    public IEnumerable<ILogger> DefaultLoggers()
    {
        if (LinqPadLogger.IsAvailable)
            yield return LinqPadLogger.Instance;
        else
            yield return ConsoleLogger.Default;
    }

    public IEnumerable<IAnalyser> DefaultAnalysers()
    {
        yield return EnvironmentAnalyser.Default;
        yield return OutliersAnalyser.Default;
        yield return MinIterationTimeAnalyser.Default;
        yield return MultimodalDistributionAnalyzer.Default;
        yield return RuntimeErrorAnalyser.Default;
        yield return ZeroMeasurementAnalyser.Default;
        yield return BaselineCustomAnalyzer.Default;
        yield return HideColumnsAnalyser.Default;
    }

    public IEnumerable<IValidator> DefaultValidators()
    {
        yield return BaselineValidator.FailOnError;
        yield return SetupCleanupValidator.FailOnError;
#if !DEBUG
        yield return JitOptimizationsValidator.FailOnError;
#endif
        yield return RunModeValidator.FailOnError;
        yield return GenericBenchmarksValidator.DontFailOnError;
        yield return DeferredExecutionValidator.FailOnError;
        yield return ParamsAllValuesValidator.FailOnError;
        yield return ParamsValidator.FailOnError;
    }
}

public class Exporter : ExporterBase
{
    public override void ExportToLog(Summary summary, ILogger logger)
    {
        // ToDo: Implement
    }
}
