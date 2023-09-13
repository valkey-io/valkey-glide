package javabushka.client;

import javabushka.client.LatencyResults;
import javabushka.client.ChosenAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Collections;

public class Benchmarking {
    static final double PROB_GET = 0.8;
    static final double PROB_GET_EXISTING_KEY = 0.8;
    static final int SIZE_GET_KEYSPACE = 3750000;
    static final int SIZE_SET_KEYSPACE = 3000000;

    private static ChosenAction chooseAction() {
        if (Math.random() > PROB_GET) {
            return ChosenAction.SET;
        }
        if (Math.random() > PROB_GET_EXISTING_KEY) {
            return ChosenAction.GET_NON_EXISTING;
        }
        return ChosenAction.GET_EXISTING;
    }

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

    public static Map<ChosenAction, ArrayList<Long>> getLatencies(int iterations, Map<ChosenAction, Operation> actions) {
        Map<ChosenAction, ArrayList<Long>> latencies = new HashMap<ChosenAction, ArrayList<Long>>();
        for (ChosenAction action : actions.keySet()) {
            latencies.put(action, new ArrayList<Long>());
        }
        
        for (int i = 0; i<iterations; i++) {
            ChosenAction action = chooseAction();
            Operation op = actions.get(action);
            ArrayList<Long> actionLatencies = latencies.get(action);
            addLatency(op, actionLatencies);
        }

        return latencies;
    }

    private static void addLatency(Operation op, ArrayList<Long> latencies) {
        long before = System.nanoTime();
        op.go();
        long after = System.nanoTime();
        latencies.add(after - before);
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

    // This has the side-effect of sorting each latencies ArrayList
    public static Map<ChosenAction, LatencyResults> calculateResults(Map<ChosenAction, ArrayList<Long>> actionLatencies) {
        Map<ChosenAction, LatencyResults> results = new HashMap<ChosenAction, LatencyResults>();

        for (Map.Entry<ChosenAction, ArrayList<Long>> entry : actionLatencies.entrySet()) {
            ChosenAction action = entry.getKey();
            ArrayList<Long> latencies = entry.getValue();

            Double avgLatency = latencies
                .stream()
                .collect(Collectors.summingLong(Long::longValue)) / Double.valueOf(latencies.size());

            Collections.sort(latencies);
            results.put(action, new LatencyResults(
                avgLatency,
                percentile(latencies, 50),
                percentile(latencies, 90),
                percentile(latencies, 99),
                stdDeviation(latencies, avgLatency)
            ));
        }

        return results;
    }

    public static void printResults(Map<ChosenAction, LatencyResults> resultsMap) {
        for (Map.Entry<ChosenAction, LatencyResults> entry : resultsMap.entrySet()) {
            ChosenAction action = entry.getKey();
            LatencyResults results = entry.getValue();
            System.out.println(
                "Avg. time in ms per " + action + ": " + results.avgLatency / 1000000.0
            );
            System.out.println(action + " p50 latency in ms: " + results.p50Latency / 1000000.0);
            System.out.println(action + " p90 latency in ms: " + results.p90Latency / 1000000.0);
            System.out.println(action + " p99 latency in ms: " + results.p99Latency / 1000000.0);
            System.out.println(action + " std dev in ms: " + results.stdDeviation / 1000000.0);
        }
    }
}
