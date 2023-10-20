package javababushka.benchmarks.utils;

// Raw timing results in nanoseconds
public class LatencyResults {
  public final double avgLatency;
  public final double p50Latency;
  public final double p90Latency;
  public final double p99Latency;
  public final double stdDeviation;

  public LatencyResults(
      double avgLatency,
      double p50Latency,
      double p90Latency,
      double p99Latency,
      double stdDeviation) {
    this.avgLatency = avgLatency;
    this.p50Latency = p50Latency;
    this.p90Latency = p90Latency;
    this.p99Latency = p99Latency;
    this.stdDeviation = stdDeviation;
  }
}
