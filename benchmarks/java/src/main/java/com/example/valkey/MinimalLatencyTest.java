package com.example.valkey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MinimalLatencyTest {
    private final RedisClient client;
    private final String clientName;
    private final int totalOperations;
    
    public MinimalLatencyTest(RedisClient client, String clientName, int totalOperations) {
        this.client = client;
        this.clientName = clientName;
        this.totalOperations = totalOperations;
    }
    
    public LatencyResults runTest() throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("Minimal Latency Test: " + clientName);
        System.out.println("Operations: " + totalOperations + " (50% SET, 50% GET)");
        System.out.println("=".repeat(60));
        
        // Connect
        client.connect();
        client.ping();
        
        List<Long> setLatencies = new ArrayList<>();
        List<Long> getLatencies = new ArrayList<>();
        
        try {
            // Perform SET operations
            System.out.println("Performing " + (totalOperations/2) + " SET operations...");
            for (int i = 0; i < totalOperations/2; i++) {
                String key = "test_key_" + i;
                String value = "test_value_" + i + "_data";
                
                long startTime = System.nanoTime();
                client.set(key, value);
                long endTime = System.nanoTime();
                
                setLatencies.add((endTime - startTime) / 1_000); // Convert to microseconds
            }
            
            // Perform GET operations
            System.out.println("Performing " + (totalOperations/2) + " GET operations...");
            for (int i = 0; i < totalOperations/2; i++) {
                String key = "test_key_" + i;
                
                long startTime = System.nanoTime();
                client.get(key);
                long endTime = System.nanoTime();
                
                getLatencies.add((endTime - startTime) / 1_000); // Convert to microseconds
            }
            
        } finally {
            client.close();
        }
        
        return new LatencyResults(clientName, setLatencies, getLatencies);
    }
    
    public static class LatencyResults {
        private final String clientName;
        private final List<Long> setLatencies;
        private final List<Long> getLatencies;
        
        public LatencyResults(String clientName, List<Long> setLatencies, List<Long> getLatencies) {
            this.clientName = clientName;
            this.setLatencies = new ArrayList<>(setLatencies);
            this.getLatencies = new ArrayList<>(getLatencies);
            Collections.sort(this.setLatencies);
            Collections.sort(this.getLatencies);
        }
        
        public void printResults() {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("Results for " + clientName);
            System.out.println("=".repeat(60));
            
            System.out.printf("SET Operations (%d total):%n", setLatencies.size());
            System.out.printf("  Average: %.1f μs%n", getAverage(setLatencies));
            System.out.printf("  Median:  %.1f μs%n", getPercentile(setLatencies, 50));
            System.out.printf("  P95:     %.1f μs%n", getPercentile(setLatencies, 95));
            System.out.printf("  P99:     %.1f μs%n", getPercentile(setLatencies, 99));
            System.out.printf("  Min:     %d μs%n", setLatencies.get(0));
            System.out.printf("  Max:     %d μs%n", setLatencies.get(setLatencies.size()-1));
            
            System.out.printf("%nGET Operations (%d total):%n", getLatencies.size());
            System.out.printf("  Average: %.1f μs%n", getAverage(getLatencies));
            System.out.printf("  Median:  %.1f μs%n", getPercentile(getLatencies, 50));
            System.out.printf("  P95:     %.1f μs%n", getPercentile(getLatencies, 95));
            System.out.printf("  P99:     %.1f μs%n", getPercentile(getLatencies, 99));
            System.out.printf("  Min:     %d μs%n", getLatencies.get(0));
            System.out.printf("  Max:     %d μs%n", getLatencies.get(getLatencies.size()-1));
            
            System.out.printf("%nOverall Average: %.1f μs%n", getOverallAverage());
        }
        
        private double getAverage(List<Long> latencies) {
            return latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }
        
        private double getPercentile(List<Long> latencies, double percentile) {
            if (latencies.isEmpty()) return 0.0;
            int index = (int) Math.ceil(percentile / 100.0 * latencies.size()) - 1;
            index = Math.max(0, Math.min(index, latencies.size() - 1));
            return latencies.get(index);
        }
        
        public double getOverallAverage() {
            List<Long> allLatencies = new ArrayList<>();
            allLatencies.addAll(setLatencies);
            allLatencies.addAll(getLatencies);
            return getAverage(allLatencies);
        }
        
        public String getClientName() {
            return clientName;
        }
    }
}
