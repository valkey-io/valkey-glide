package javabushka.client.utils;

// Raw timing results in nanoseconds
public class LatencyResults {
    public final double avgLatency;
    public final long p50Latency;
    public final long p90Latency;
    public final long p99Latency;
    public final double stdDeviation;

    public LatencyResults(
        double avgLatency,
        long p50Latency,
        long p90Latency,
        long p99Latency,
        double stdDeviation
    ) {
        this.avgLatency = avgLatency;
        this.p50Latency = p50Latency;
        this.p90Latency = p90Latency;
        this.p99Latency = p99Latency;
        this.stdDeviation = stdDeviation;
    }
}
