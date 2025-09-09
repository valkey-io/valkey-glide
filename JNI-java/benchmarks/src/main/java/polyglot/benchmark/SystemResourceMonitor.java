package polyglot.benchmark;

import java.lang.management.*;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.management.OperatingSystemMXBean;

/**
 * Monitors system resources (CPU and memory) during benchmark execution.
 */
public class SystemResourceMonitor {
    
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final Runtime runtime;
    
    // Resource snapshots
    private final AtomicReference<ResourceSnapshot> currentSnapshot = new AtomicReference<>();
    private final AtomicReference<ResourceSnapshot> peakSnapshot = new AtomicReference<>();
    
    public SystemResourceMonitor() {
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.runtime = Runtime.getRuntime();
        
        // Initialize with zero values first to avoid NPE
        ResourceSnapshot zero = new ResourceSnapshot(
            System.currentTimeMillis(),
            0.0, 0.0, runtime.availableProcessors(),
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L
        );
        currentSnapshot.set(zero);
        peakSnapshot.set(zero);
        
        // Now capture real initial values
        ResourceSnapshot initial = captureSnapshot();
        currentSnapshot.set(initial);
        peakSnapshot.set(initial);
    }
    
    /**
     * Capture current resource usage
     */
    public ResourceSnapshot captureSnapshot() {
        // Force GC for accurate memory reading (optional, can be disabled for performance)
        // System.gc();
        
        // CPU metrics
        double processCpuLoad = osBean.getProcessCpuLoad() * 100; // Convert to percentage
        double systemCpuLoad = osBean.getCpuLoad() * 100;
        int availableProcessors = osBean.getAvailableProcessors();
        
        // Memory metrics (in MB)
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        
        // Heap memory details
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long heapUsed = heapUsage.getUsed() / (1024 * 1024);
        long heapCommitted = heapUsage.getCommitted() / (1024 * 1024);
        long heapMax = heapUsage.getMax() / (1024 * 1024);
        
        // Non-heap memory (includes metaspace, code cache, etc.)
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        long nonHeapUsed = nonHeapUsage.getUsed() / (1024 * 1024);
        
        // OS-level memory (if available)
        long totalPhysicalMemory = osBean.getTotalMemorySize() / (1024 * 1024);
        long freePhysicalMemory = osBean.getFreeMemorySize() / (1024 * 1024);
        long usedPhysicalMemory = totalPhysicalMemory - freePhysicalMemory;
        
        ResourceSnapshot snapshot = new ResourceSnapshot(
            System.currentTimeMillis(),
            processCpuLoad, systemCpuLoad, availableProcessors,
            usedMemory, totalMemory, maxMemory,
            heapUsed, heapCommitted, heapMax,
            nonHeapUsed,
            usedPhysicalMemory, totalPhysicalMemory
        );
        
        // Update current and peak
        currentSnapshot.set(snapshot);
        updatePeak(snapshot);
        
        return snapshot;
    }
    
    private void updatePeak(ResourceSnapshot snapshot) {
        ResourceSnapshot current;
        do {
            current = peakSnapshot.get();
            // Always create new peak snapshot with max of each metric independently
            // This tracks peaks per metric, not requiring all metrics to peak together
        } while (!peakSnapshot.compareAndSet(current, new ResourceSnapshot(
            snapshot.timestamp,
            Math.max(current.processCpuLoad, snapshot.processCpuLoad),
            Math.max(current.systemCpuLoad, snapshot.systemCpuLoad),
            snapshot.availableProcessors,
            Math.max(current.usedMemory, snapshot.usedMemory),
            snapshot.totalMemory,
            snapshot.maxMemory,
            Math.max(current.heapUsed, snapshot.heapUsed),
            Math.max(current.heapCommitted, snapshot.heapCommitted),
            snapshot.heapMax,
            Math.max(current.nonHeapUsed, snapshot.nonHeapUsed),
            Math.max(current.usedPhysicalMemory, snapshot.usedPhysicalMemory),
            snapshot.totalPhysicalMemory
        )));
    }
    
    public ResourceSnapshot getCurrentSnapshot() {
        return currentSnapshot.get();
    }
    
    public ResourceSnapshot getPeakSnapshot() {
        return peakSnapshot.get();
    }
    
    /**
     * Immutable snapshot of system resources at a point in time
     */
    public static class ResourceSnapshot {
        public final long timestamp;
        
        // CPU metrics
        public final double processCpuLoad; // Process CPU usage %
        public final double systemCpuLoad;  // System CPU usage %
        public final int availableProcessors;
        
        // JVM memory metrics (MB)
        public final long usedMemory;
        public final long totalMemory;
        public final long maxMemory;
        
        // Heap details (MB)
        public final long heapUsed;
        public final long heapCommitted;
        public final long heapMax;
        
        // Non-heap memory (MB)
        public final long nonHeapUsed;
        
        // OS memory (MB)
        public final long usedPhysicalMemory;
        public final long totalPhysicalMemory;
        
        public ResourceSnapshot(long timestamp,
                               double processCpuLoad, double systemCpuLoad, int availableProcessors,
                               long usedMemory, long totalMemory, long maxMemory,
                               long heapUsed, long heapCommitted, long heapMax,
                               long nonHeapUsed,
                               long usedPhysicalMemory, long totalPhysicalMemory) {
            this.timestamp = timestamp;
            this.processCpuLoad = processCpuLoad;
            this.systemCpuLoad = systemCpuLoad;
            this.availableProcessors = availableProcessors;
            this.usedMemory = usedMemory;
            this.totalMemory = totalMemory;
            this.maxMemory = maxMemory;
            this.heapUsed = heapUsed;
            this.heapCommitted = heapCommitted;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.usedPhysicalMemory = usedPhysicalMemory;
            this.totalPhysicalMemory = totalPhysicalMemory;
        }
        
        public String toCompactString() {
            return String.format("CPU: %.1f%% (sys: %.1f%%), Heap: %dMB/%dMB, Total: %dMB/%dMB",
                processCpuLoad, systemCpuLoad,
                heapUsed, heapMax,
                usedMemory, maxMemory);
        }
    }
}