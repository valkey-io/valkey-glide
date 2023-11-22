package javababushka.benchmarks.utils;

import lombok.AllArgsConstructor;

/** Raw timing results in nanoseconds */
@AllArgsConstructor
public class LatencyResults {
  public final double avgLatency;
  public final double p50Latency;
  public final double p90Latency;
  public final double p99Latency;
  public final double stdDeviation;
  public final int totalHits;
}
