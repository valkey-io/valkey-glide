package javabushka.client;

import javabushka.client.LatencyResults;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Collections;

public class Benchmarking {
    static final int SIZE_GET_KEYSPACE = 3750000;
    static final int SIZE_SET_KEYSPACE = 3000000;

    public static String generateKeyGet() {
        int range = SIZE_GET_KEYSPACE - SIZE_SET_KEYSPACE;
        return Math.floor(Math.random() * range + SIZE_SET_KEYSPACE + 1) + "";
    }
   
    public static String generateKeySet() {
        return (Math.floor(Math.random() * SIZE_SET_KEYSPACE) + 1) + "";
    }

    public interface Operation {
        void go();
    }

    public static ArrayList<Long> getLatencies(int iterations, Operation op) {
        ArrayList<Long> latencies = new ArrayList<Long>();
        for (int i = 0; i<iterations; i++) {
            long before = System.nanoTime();
            op.go();
            long after = System.nanoTime();
            latencies.add(after - before);
        }
        return latencies;
    }

    // Assumption: latencies is sorted in ascending order
    private static Long percentile(ArrayList<Long> latencies, int percentile) {
        return latencies.get((int) Math.ceil((percentile / 100.0) * latencies.size()));
    }

    private static double stdDeviation(ArrayList<Long> latencies, Double avgLatency) {
        double stdDeviation = latencies.stream()
            .mapToDouble(Long::doubleValue)
            .reduce(0.0, (stdDev, latency) -> stdDev + Math.pow(latency - avgLatency, 2));
        return Math.sqrt(stdDeviation / latencies.size());
    }

    // This has the side-effect of sorting the latencies ArrayList
    public static LatencyResults calculateResults(ArrayList<Long> latencies) {
        Double avgLatency = latencies
            .stream()
            .collect(Collectors.summingLong(Long::longValue)) / Double.valueOf(latencies.size());

        Collections.sort(latencies);
        return new LatencyResults(
            avgLatency,
            percentile(latencies, 50),
            percentile(latencies, 90),
            percentile(latencies, 99),
            stdDeviation(latencies, avgLatency)
        );
    }

    public static void printResults(String operation, LatencyResults results) {
        System.out.println(
            "Avg. time in ms per " + operation + ": " + results.avgLatency / 1000000.0
        );
        System.out.println(operation + " p50 latency in ms: " + results.p50Latency / 1000000.0);
        System.out.println(operation + " p90 latency in ms: " + results.p90Latency / 1000000.0);
        System.out.println(operation + " p99 latency in ms: " + results.p99Latency / 1000000.0);
        System.out.println(operation + " std dev in ms: " + results.stdDeviation / 1000000.0);
    }

}