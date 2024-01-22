package glide.benchmarks.utils;

import java.util.Arrays;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

/** Raw timing results in nanoseconds */
public class LatencyResults {
    // measurements are done in nano-seconds, but it should be converted to seconds later
    static final double SECONDS_TO_NANO = 1e-9;

    public final double avgLatency;
    public final double p50Latency;
    public final double p90Latency;
    public final double p99Latency;
    public final double stdDeviation;
    public final int totalHits;

    public LatencyResults(double[] latencies) {
        avgLatency = SECONDS_TO_NANO * Arrays.stream(latencies).sum() / latencies.length;
        p50Latency = SECONDS_TO_NANO * new Percentile().evaluate(latencies, 50);
        p90Latency = SECONDS_TO_NANO * new Percentile().evaluate(latencies, 90);
        p99Latency = SECONDS_TO_NANO * new Percentile().evaluate(latencies, 99);
        stdDeviation = SECONDS_TO_NANO * new StandardDeviation().evaluate(latencies, avgLatency);
        totalHits = latencies.length;
    }
}
