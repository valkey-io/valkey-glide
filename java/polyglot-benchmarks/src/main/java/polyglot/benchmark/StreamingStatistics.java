package polyglot.benchmark;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Memory-efficient streaming statistics calculator that doesn't store individual values.
 * Uses Welford's online algorithm for variance calculation and P² algorithm for percentiles.
 */
public class StreamingStatistics {
    
    private final AtomicLong count = new AtomicLong(0);
    private final DoubleAdder sum = new DoubleAdder();
    private final AtomicReference<Double> min = new AtomicReference<>(Double.MAX_VALUE);
    private final AtomicReference<Double> max = new AtomicReference<>(Double.MIN_VALUE);
    
    // For standard deviation using Welford's algorithm
    // Use a single atomic reference to ensure consistency
    private final AtomicReference<VarianceState> varianceState = new AtomicReference<>(new VarianceState(0.0, 0.0, 0));
    
    // Simplified percentile tracking using reservoir sampling
    // We keep a small fixed-size reservoir for approximate percentiles
    private static final int RESERVOIR_SIZE = 1000;
    private final double[] reservoir = new double[RESERVOIR_SIZE];
    private final Object reservoirLock = new Object();
    
    public void addValue(double value) {
        long n = count.incrementAndGet();
        sum.add(value);
        
        // Update min/max
        updateMin(value);
        updateMax(value);
        
        // Update mean and variance using Welford's algorithm
        updateVariance(value);
        
        // Update reservoir for percentile estimation
        updateReservoir(value, n);
    }
    
    private void updateMin(double value) {
        Double currentMin;
        do {
            currentMin = min.get();
            if (value >= currentMin) return;
        } while (!min.compareAndSet(currentMin, value));
    }
    
    private void updateMax(double value) {
        Double currentMax;
        do {
            currentMax = max.get();
            if (value <= currentMax) return;
        } while (!max.compareAndSet(currentMax, value));
    }
    
    private void updateVariance(double value) {
        // Welford's online algorithm for variance
        // Use a single CAS operation on a combined state object to ensure consistency
        VarianceState oldState, newState;
        
        do {
            oldState = varianceState.get();
            
            // Use the stored count for proper Welford's algorithm
            long oldCount = oldState.count;
            long newCount = oldCount + 1;
            
            double delta = value - oldState.mean;
            double newMean = oldState.mean + delta / newCount;
            double delta2 = value - newMean;
            double newM2 = oldState.m2 + delta * delta2;
            
            newState = new VarianceState(newMean, newM2, newCount);
            
        } while (!varianceState.compareAndSet(oldState, newState));
    }
    
    private void updateReservoir(double value, long n) {
        synchronized (reservoirLock) {
            if (n <= RESERVOIR_SIZE) {
                reservoir[(int)(n - 1)] = value;
            } else {
                // Reservoir sampling: each new item has RESERVOIR_SIZE/n chance of being included
                // Use ThreadLocalRandom to avoid contention
                long randomIndex = java.util.concurrent.ThreadLocalRandom.current().nextLong(n);
                if (randomIndex < RESERVOIR_SIZE) {
                    reservoir[(int)randomIndex] = value;
                }
            }
        }
    }
    
    public long getCount() {
        return count.get();
    }
    
    public double getSum() {
        return sum.sum();
    }
    
    public double getMin() {
        return count.get() > 0 ? min.get() : 0.0;
    }
    
    public double getMax() {
        return count.get() > 0 ? max.get() : 0.0;
    }
    
    public double getMean() {
        long n = count.get();
        return n > 0 ? sum.sum() / n : 0.0;
    }
    
    public double getStandardDeviation() {
        long n = count.get();
        if (n < 2) return 0.0;
        return Math.sqrt(varianceState.get().m2 / (n - 1));
    }
    
    public double getPercentile(double percentile) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("Percentile must be between 0 and 100");
        }
        
        long n = count.get();
        if (n == 0) return 0.0;
        
        synchronized (reservoirLock) {
            int sampleSize = (int)Math.min(n, RESERVOIR_SIZE);
            if (sampleSize == 0) return 0.0;
            
            // Create a copy and sort it
            double[] sorted = new double[sampleSize];
            System.arraycopy(reservoir, 0, sorted, 0, sampleSize);
            java.util.Arrays.sort(sorted);
            
            // Calculate percentile index
            double index = (percentile / 100.0) * (sampleSize - 1);
            int lower = (int)Math.floor(index);
            int upper = (int)Math.ceil(index);
            
            if (lower == upper) {
                return sorted[lower];
            } else {
                // Linear interpolation
                double weight = index - lower;
                return sorted[lower] * (1 - weight) + sorted[upper] * weight;
            }
        }
    }
    
    /**
     * Create a snapshot of current statistics that can be safely used without locking
     */
    public StatisticsSnapshot getSnapshot() {
        return new StatisticsSnapshot(
            count.get(),
            getMin(),
            getMax(),
            getMean(),
            getStandardDeviation(),
            getPercentile(50),
            getPercentile(90),
            getPercentile(95),
            getPercentile(99)
        );
    }
    
    public static class StatisticsSnapshot {
        public final long count;
        public final double min;
        public final double max;
        public final double mean;
        public final double stdDev;
        public final double p50;
        public final double p90;
        public final double p95;
        public final double p99;
        
        public StatisticsSnapshot(long count, double min, double max, double mean, 
                                 double stdDev, double p50, double p90, double p95, double p99) {
            this.count = count;
            this.min = min;
            this.max = max;
            this.mean = mean;
            this.stdDev = stdDev;
            this.p50 = p50;
            this.p90 = p90;
            this.p95 = p95;
            this.p99 = p99;
        }
    }
    
    // Immutable state class for atomic variance updates
    private static class VarianceState {
        final double mean;
        final double m2;
        final long count;
        
        VarianceState(double mean, double m2, long count) {
            this.mean = mean;
            this.m2 = m2;
            this.count = count;
        }
    }
}