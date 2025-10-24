package com.example.valkey;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class PerformanceMetrics {
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder totalLatency = new LongAdder();
    private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatency = new AtomicLong(0);
    
    private final LongAdder setOperations = new LongAdder();
    private final LongAdder getOperations = new LongAdder();
    private final LongAdder setLatency = new LongAdder();
    private final LongAdder getLatency = new LongAdder();
    
    private final List<Long> latencySamples = Collections.synchronizedList(new ArrayList<>());
    private volatile long startTime = System.currentTimeMillis();
    private volatile double finalRps = -1; // -1 indicates not finalized yet
    
    public void reset() {
        totalRequests.reset();
        successfulRequests.reset();
        failedRequests.reset();
        totalLatency.reset();
        minLatency.set(Long.MAX_VALUE);
        maxLatency.set(0);
        setOperations.reset();
        getOperations.reset();
        setLatency.reset();
        getLatency.reset();
        synchronized (latencySamples) {
            latencySamples.clear();
        }
        startTime = System.currentTimeMillis();
        finalRps = -1; // Reset finalization state
    }
    
    public void recordRequest(String operation, long latencyMs, boolean success) {
        totalRequests.increment();
        
        if (success) {
            successfulRequests.increment();
            totalLatency.add(latencyMs);
            
            // Update min/max latency
            updateMinLatency(latencyMs);
            updateMaxLatency(latencyMs);
            
            // Record operation-specific metrics
            if ("SET".equals(operation)) {
                setOperations.increment();
                setLatency.add(latencyMs);
            } else if ("GET".equals(operation)) {
                getOperations.increment();
                getLatency.add(latencyMs);
            }
            
            // Sample latencies for percentile calculation (limit to 10000 samples)
            if (latencySamples.size() < 10000) {
                latencySamples.add(latencyMs);
            }
        } else {
            failedRequests.increment();
        }
    }
    
    private void updateMinLatency(long latency) {
        long current = minLatency.get();
        while (latency < current && !minLatency.compareAndSet(current, latency)) {
            current = minLatency.get();
        }
    }
    
    private void updateMaxLatency(long latency) {
        long current = maxLatency.get();
        while (latency > current && !maxLatency.compareAndSet(current, latency)) {
            current = maxLatency.get();
        }
    }
    
    public long getTotalRequests() {
        return totalRequests.sum();
    }
    
    public long getSuccessfulRequests() {
        return successfulRequests.sum();
    }
    
    public long getFailedRequests() {
        return failedRequests.sum();
    }
    
    public double getSuccessRate() {
        long total = getTotalRequests();
        return total > 0 ? (double) getSuccessfulRequests() / total * 100 : 0;
    }
    
    public double getAverageLatency() {
        long successful = getSuccessfulRequests();
        return successful > 0 ? (double) totalLatency.sum() / successful : 0;
    }
    
    public long getMinLatency() {
        long min = minLatency.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }
    
    public long getMaxLatency() {
        return maxLatency.get();
    }
    
    public double getRequestsPerSecond() {
        // If metrics have been finalized, return the captured RPS
        if (finalRps >= 0) {
            return finalRps;
        }
        // Otherwise calculate current RPS
        long duration = System.currentTimeMillis() - startTime;
        return duration > 0 ? (double) getSuccessfulRequests() / (duration / 1000.0) : 0;
    }
    
    public void finalizeMetrics() {
        // Capture the final RPS to prevent it from changing due to time passage
        finalRps = getRequestsPerSecond();
    }
    
    public long getSetOperations() {
        return setOperations.sum();
    }
    
    public long getGetOperations() {
        return getOperations.sum();
    }
    
    public double getAverageSetLatency() {
        long sets = getSetOperations();
        return sets > 0 ? (double) setLatency.sum() / sets : 0;
    }
    
    public double getAverageGetLatency() {
        long gets = getGetOperations();
        return gets > 0 ? (double) getLatency.sum() / gets : 0;
    }
    
    public double getPercentile(double percentile) {
        if (latencySamples.isEmpty()) {
            return 0;
        }
        
        List<Long> sortedSamples = new ArrayList<>(latencySamples);
        Collections.sort(sortedSamples);
        
        int index = (int) Math.ceil(percentile / 100.0 * sortedSamples.size()) - 1;
        index = Math.max(0, Math.min(index, sortedSamples.size() - 1));
        
        return sortedSamples.get(index);
    }
    
    public String getSummary() {
        return String.format(
            "Total: %d, Success: %d (%.1f%%), Failed: %d, RPS: %.1f, " +
            "Latency - Avg: %.1fms, Min: %dms, Max: %dms, P95: %.1fms, P99: %.1fms, " +
            "SET: %d (avg: %.1fms), GET: %d (avg: %.1fms)",
            getTotalRequests(), getSuccessfulRequests(), getSuccessRate(), getFailedRequests(),
            getRequestsPerSecond(), getAverageLatency(), getMinLatency(), getMaxLatency(),
            getPercentile(95), getPercentile(99),
            getSetOperations(), getAverageSetLatency(), getGetOperations(), getAverageGetLatency()
        );
    }
}
