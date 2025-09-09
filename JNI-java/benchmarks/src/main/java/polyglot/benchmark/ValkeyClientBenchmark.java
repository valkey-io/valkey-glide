package polyglot.benchmark;

import glide.api.GlideClusterClient;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import org.apache.commons.cli.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadLocalRandom;
import java.io.*;

/**
 * Valkey Client Performance Benchmark (JNI only)
 * - Uses streaming statistics (no storage of individual values)
 * - Periodic checkpoint to disk for long-running tests
 * - Minimal memory footprint even for millions of operations
 */
public class ValkeyClientBenchmark {
    private static final Logger logger = LoggerFactory.getLogger(ValkeyClientBenchmark.class);
    
    // Default to AWS ElastiCache configuration
    private static final String DEFAULT_HOST = System.getenv("ELASTICACHE_HOST") != null ? 
        System.getenv("ELASTICACHE_HOST") : "clustercfg.testing-cluster.ey5v7d.use2.cache.amazonaws.com";
    private static final int DEFAULT_PORT = 6379;
    private static final int DEFAULT_DATA_SIZE = 100;
    private static final int DEFAULT_CONCURRENCY = 100;
    private static final double DEFAULT_QPS = 10000;
    private static final double DEFAULT_WRITE_RATIO = 0.2; // 20% writes
    private static final double DEFAULT_HIT_RATIO = 0.8; // 80% cache hit rate
    private static final int KEY_SPACE_SIZE = 10000;
    
    public static class BenchmarkConfig {
        public String host = DEFAULT_HOST;
        public int port = DEFAULT_PORT;
        public int dataSize = DEFAULT_DATA_SIZE;
        public int concurrency = DEFAULT_CONCURRENCY;
        public double targetQps = DEFAULT_QPS;
        public boolean clusterMode = true;
        public boolean tls = true;
        public boolean warmup = true;
        public String outputFile = null;
        public boolean verbose = false;
        public int testDurationSec = 120;
        public double writeRatio = DEFAULT_WRITE_RATIO;
        public double hitRatio = DEFAULT_HIT_RATIO;
        public int keySpaceSize = KEY_SPACE_SIZE;
        public int checkpointIntervalSec = 30; // Save stats to disk every 30 seconds
    }
    
    // Lightweight metrics using streaming statistics
    public static class StreamingMetrics {
        public final AtomicLong setCommands = new AtomicLong(0);
        public final AtomicLong getCommands = new AtomicLong(0);
        public final AtomicLong getHits = new AtomicLong(0);
        public final AtomicLong getMisses = new AtomicLong(0);
        public final AtomicLong setErrors = new AtomicLong(0);
        public final AtomicLong getErrors = new AtomicLong(0);
        
        // Streaming statistics - no storage of individual values
        public final StreamingStatistics setLatencies = new StreamingStatistics();
        public final StreamingStatistics getHitLatencies = new StreamingStatistics();
        public final StreamingStatistics getMissLatencies = new StreamingStatistics();
        
        public long getTotalCommands() {
            return setCommands.get() + getCommands.get();
        }
        
        public long getTotalErrors() {
            return setErrors.get() + getErrors.get();
        }
        
        public double getCacheHitRate() {
            long total = getHits.get() + getMisses.get();
            return total > 0 ? (getHits.get() * 100.0 / total) : 0;
        }
        
        public MetricsSnapshot getSnapshot() {
            return new MetricsSnapshot(
                setCommands.get(), getCommands.get(),
                getHits.get(), getMisses.get(),
                setErrors.get(), getErrors.get(),
                setLatencies.getSnapshot(),
                getHitLatencies.getSnapshot(),
                getMissLatencies.getSnapshot()
            );
        }
    }
    
    // Immutable snapshot for checkpointing
    public static class MetricsSnapshot {
        public final long setCommands;
        public final long getCommands;
        public final long getHits;
        public final long getMisses;
        public final long setErrors;
        public final long getErrors;
        public final StreamingStatistics.StatisticsSnapshot setLatencies;
        public final StreamingStatistics.StatisticsSnapshot getHitLatencies;
        public final StreamingStatistics.StatisticsSnapshot getMissLatencies;
        public final long timestamp;
        
        public MetricsSnapshot(long setCommands, long getCommands, long getHits, long getMisses,
                              long setErrors, long getErrors,
                              StreamingStatistics.StatisticsSnapshot setLatencies,
                              StreamingStatistics.StatisticsSnapshot getHitLatencies,
                              StreamingStatistics.StatisticsSnapshot getMissLatencies) {
            this.setCommands = setCommands;
            this.getCommands = getCommands;
            this.getHits = getHits;
            this.getMisses = getMisses;
            this.setErrors = setErrors;
            this.getErrors = getErrors;
            this.setLatencies = setLatencies;
            this.getHitLatencies = getHitLatencies;
            this.getMissLatencies = getMissLatencies;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public static void main(String[] args) {
        // Configure logging
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
        
        try {
            BenchmarkConfig config = parseArgs(args);
            if (config == null) return;
            
            ValkeyClientBenchmark benchmark = new ValkeyClientBenchmark(config);
            ImplementationResult results = benchmark.run();
            benchmark.printResults(results);
            
            if (config.outputFile != null) {
                benchmark.saveResults(results, config.outputFile);
            }
            
        } catch (Exception e) {
            logger.error("Benchmark failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    private final BenchmarkConfig config;
    private final String testValue;
    private final AtomicLong totalKeysWritten = new AtomicLong(0);
    private final File checkpointDir;
    
    public ValkeyClientBenchmark(BenchmarkConfig config) throws IOException {
        this.config = config;
        this.testValue = "x".repeat(config.dataSize);
        
        // Create checkpoint directory
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        this.checkpointDir = new File("benchmark_checkpoints_" + timestamp);
        if (!checkpointDir.mkdirs() && !checkpointDir.exists()) {
            throw new IOException("Failed to create checkpoint directory: " + checkpointDir);
        }
    }
    
    private static BenchmarkConfig parseArgs(String[] args) {
        Options options = new Options();
        
        options.addOption("H", "host", true, "Valkey host");
        options.addOption("p", "port", true, "Valkey port");
        options.addOption("d", "data-size", true, "Data size in bytes");
        options.addOption("t", "concurrency", true, "Concurrent tasks");
        options.addOption("q", "qps", true, "Target QPS");
        options.addOption("D", "duration", true, "Test duration in seconds");
        options.addOption(null, "write-ratio", true, "Write ratio 0.0-1.0");
        options.addOption(null, "hit-ratio", true, "Cache hit ratio 0.0-1.0");
        options.addOption(null, "key-space", true, "Key space size");
        options.addOption(null, "checkpoint-interval", true, "Checkpoint interval in seconds");
        options.addOption(null, "no-cluster", false, "Disable cluster mode");
        options.addOption(null, "no-tls", false, "Disable TLS");
        options.addOption(null, "no-warmup", false, "Skip warmup phase");
        options.addOption("o", "output", true, "Output file for results");
        options.addOption("v", "verbose", false, "Verbose output");
        options.addOption("help", false, "Show this help");
        
        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            
            if (cmd.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("ValkeyClientBenchmark", options);
                return null;
            }
            
            BenchmarkConfig config = new BenchmarkConfig();
            config.host = cmd.getOptionValue("H", DEFAULT_HOST);
            config.port = Integer.parseInt(cmd.getOptionValue("port", String.valueOf(DEFAULT_PORT)));
            config.dataSize = Integer.parseInt(cmd.getOptionValue("data-size", String.valueOf(DEFAULT_DATA_SIZE)));
            config.concurrency = Integer.parseInt(cmd.getOptionValue("concurrency", String.valueOf(DEFAULT_CONCURRENCY)));
            config.targetQps = Double.parseDouble(cmd.getOptionValue("qps", String.valueOf(DEFAULT_QPS)));
            config.testDurationSec = Integer.parseInt(cmd.getOptionValue("duration", "120"));
            config.writeRatio = Double.parseDouble(cmd.getOptionValue("write-ratio", String.valueOf(DEFAULT_WRITE_RATIO)));
            config.hitRatio = Double.parseDouble(cmd.getOptionValue("hit-ratio", String.valueOf(DEFAULT_HIT_RATIO)));
            config.keySpaceSize = Integer.parseInt(cmd.getOptionValue("key-space", String.valueOf(KEY_SPACE_SIZE)));
            config.checkpointIntervalSec = Integer.parseInt(cmd.getOptionValue("checkpoint-interval", "30"));
            config.clusterMode = !cmd.hasOption("no-cluster");
            config.tls = !cmd.hasOption("no-tls");
            config.warmup = !cmd.hasOption("no-warmup");
            config.outputFile = cmd.getOptionValue("output");
            config.verbose = cmd.hasOption("verbose");
            
            return config;
            
        } catch (ParseException | NumberFormatException e) {
            logger.error("Error parsing arguments: {}", e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("ValkeyClientBenchmark", options);
            return null;
        }
    }
    
    public ImplementationResult run() throws Exception {
        logger.info("=== VALKEY CLIENT BENCHMARK ===");
        logger.info("Configuration:");
        logger.info("  Host: {}:{}", config.host, config.port);
        logger.info("  Target QPS: {}", config.targetQps);
        logger.info("  Test Duration: {}s", config.testDurationSec);
        logger.info("  Checkpoint Interval: {}s", config.checkpointIntervalSec);
        logger.info("  Checkpoint Directory: {}", checkpointDir);
        logger.info("");
        
        // Clean database before starting
        flushDatabase();
        
        try (GlideClusterClient client = buildClient()) {
            logger.info("✅ GLIDE client connected");
            logger.info("   Using implementation: {}", client.getClass().getName());
            logger.info("   Class loader: {}", client.getClass().getClassLoader());
            logger.info("   Module: {}", client.getClass().getModule());
            
            if (config.warmup) warmup(client);
            prepopulateKeys(client);
            
            StreamingMetrics metrics = new StreamingMetrics();
            return runBenchmark("JNI", new ClientOperations() {
                @Override
                public void executeSet(String key, String value) throws Exception {
                    long start = System.nanoTime();
                    try {
                        client.set(key, value).get();
                        double latency = (System.nanoTime() - start) / 1_000_000.0;
                        metrics.setLatencies.addValue(latency);
                        metrics.setCommands.incrementAndGet();
                        totalKeysWritten.incrementAndGet();
                    } catch (Exception e) {
                        metrics.setErrors.incrementAndGet();
                        throw e;
                    }
                }
                
                @Override
                public void executeGet(String key) throws Exception {
                    long start = System.nanoTime();
                    try {
                        Object result = client.get(key).get();
                        double latency = (System.nanoTime() - start) / 1_000_000.0;
                        
                        if (result != null) {
                            metrics.getHitLatencies.addValue(latency);
                            metrics.getHits.incrementAndGet();
                        } else {
                            metrics.getMissLatencies.addValue(latency);
                            metrics.getMisses.incrementAndGet();
                        }
                        metrics.getCommands.incrementAndGet();
                    } catch (Exception e) {
                        metrics.getErrors.incrementAndGet();
                        throw e;
                    }
                }
                
                @Override
                public void handleError(boolean isWrite, Exception e) {
                    if (isWrite) {
                        metrics.setErrors.incrementAndGet();
                    } else {
                        metrics.getErrors.incrementAndGet();
                    }
                }
            }, metrics, null);
        }
    }
    
    private GlideClusterClient buildClient() {
        GlideClusterClientConfiguration glideConfig = GlideClusterClientConfiguration.builder()
                .address(NodeAddress.builder().host(config.host).port(config.port).build())
                .useTLS(config.tls)
                .requestTimeout(30000)
                .advancedConfiguration(
                        glide.api.models.configuration.AdvancedGlideClusterClientConfiguration.builder()
                                .connectionTimeout(15000)
                                .build())
                .build();
        try {
            return GlideClusterClient.createClient(glideConfig).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GlideClusterClient", e);
        }
    }
    
    private void flushDatabase() {
        logger.info("🧹 Cleaning database before test ...");
        try (GlideClusterClient client = buildClient()) {
            client.flushall().get();
            logger.info("✅ Database cleaned successfully");
        } catch (Exception e) {
            logger.warn("⚠️  Warning: Database cleanup failed: {}", e.getMessage());
        }
    }
    
    private void warmup(GlideClusterClient client) throws Exception {
        logger.info("🔥 Warming up client...");
        for (int i = 0; i < 1000; i++) {
            String key = "warmup:" + i;
            client.set(key, testValue).get();
            client.get(key).get();
        }
        logger.info("✅ Warmup completed");
    }
    
    private void prepopulateKeys(GlideClusterClient client) throws Exception {
        logger.info("📦 Pre-populating keys...");
        int keysToPopulate = (int)(config.keySpaceSize * config.hitRatio);
        for (int i = 0; i < keysToPopulate; i++) {
            String key = "preload:" + i;
            client.set(key, testValue).get();
        }
        logger.info("✅ Pre-populated {} keys ({}% of keyspace)",
                keysToPopulate, config.hitRatio * 100);
    }
    
    private interface ClientOperations {
        void executeSet(String key, String value) throws Exception;
        void executeGet(String key) throws Exception;
        void handleError(boolean isWrite, Exception e);
    }
    
    private ImplementationResult runBenchmark(String implementation,
                                            ClientOperations ops,
                                            StreamingMetrics metrics,
                                            Callable<String> perfMetrics) throws Exception {
        long startTime = System.nanoTime();
        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService executor = Executors.newFixedThreadPool(config.concurrency);
        
        // Resource monitoring
        SystemResourceMonitor resourceMonitor = new SystemResourceMonitor();
        List<SystemResourceMonitor.ResourceSnapshot> resourceCheckpoints = new CopyOnWriteArrayList<>();
        
        // Checkpoint service
        ScheduledExecutorService checkpointService = Executors.newSingleThreadScheduledExecutor();
        List<MetricsSnapshot> checkpoints = new CopyOnWriteArrayList<>();
        
        checkpointService.scheduleAtFixedRate(() -> {
            MetricsSnapshot snapshot = metrics.getSnapshot();
            checkpoints.add(snapshot);
            
            // Capture resource usage
            SystemResourceMonitor.ResourceSnapshot resourceSnapshot = resourceMonitor.captureSnapshot();
            resourceCheckpoints.add(resourceSnapshot);
            
            saveCheckpoint(implementation, snapshot, resourceSnapshot);
            
            long totalCommands = snapshot.setCommands + snapshot.getCommands;
            double elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0;
            double actualQps = totalCommands / elapsed;
            
            logger.info("{}: {} cmds, {} QPS, {}% hits | {}",
                    implementation, totalCommands,
                    String.format("%.0f", actualQps),
                        String.format("%.1f", (snapshot.getHits * 100.0 / (snapshot.getHits + snapshot.getMisses))),
                    resourceSnapshot.toCompactString());
        }, config.checkpointIntervalSec, config.checkpointIntervalSec, TimeUnit.SECONDS);
        
        // Workers
        GlobalRateLimiter globalLimiter = new GlobalRateLimiter(config.targetQps);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < config.concurrency; i++) {
            futures.add(executor.submit(new Worker(ops, metrics, globalLimiter, running)));
        }
        
        // Run for specified duration
        Thread.sleep(config.testDurationSec * 1000);
        
        // Shutdown
        running.set(false);
        checkpointService.shutdown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        double duration = (System.nanoTime() - startTime) / 1_000_000_000.0;
        String perfMetricsStr = "";
        try {
            perfMetricsStr = perfMetrics.call();
        } catch (Exception e) {
            perfMetricsStr = "N/A";
        }
        
        MetricsSnapshot finalSnapshot = metrics.getSnapshot();
        checkpoints.add(finalSnapshot);
        SystemResourceMonitor.ResourceSnapshot peakResources = resourceMonitor.getPeakSnapshot();
        
        return new ImplementationResult(implementation, duration, finalSnapshot, 
                                       checkpoints, resourceCheckpoints, peakResources, perfMetricsStr);
    }
    
    private void saveCheckpoint(String impl, MetricsSnapshot snapshot, SystemResourceMonitor.ResourceSnapshot resourceSnapshot) {
        File checkpointFile = new File(checkpointDir, 
            String.format("%s_checkpoint_%d.json", impl, snapshot.timestamp));
        
        try (PrintWriter writer = new PrintWriter(checkpointFile)) {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode json = mapper.createObjectNode();
            
            json.put("implementation", impl);
            json.put("timestamp", snapshot.timestamp);
            json.put("set_commands", snapshot.setCommands);
            json.put("get_commands", snapshot.getCommands);
            json.put("get_hits", snapshot.getHits);
            json.put("get_misses", snapshot.getMisses);
            
            ObjectNode resourcesNode = json.putObject("resources");
            resourcesNode.put("process_cpu_percent", resourceSnapshot.processCpuLoad);
            resourcesNode.put("system_cpu_percent", resourceSnapshot.systemCpuLoad);
            resourcesNode.put("heap_used_mb", resourceSnapshot.heapUsed);
            resourcesNode.put("heap_max_mb", resourceSnapshot.heapMax);
            resourcesNode.put("total_memory_mb", resourceSnapshot.usedMemory);
            resourcesNode.put("physical_memory_mb", resourceSnapshot.usedPhysicalMemory);
            
            if (snapshot.setLatencies.count > 0) {
                ObjectNode setStats = json.putObject("set_latency");
                setStats.put("count", snapshot.setLatencies.count);
                setStats.put("mean", snapshot.setLatencies.mean);
                setStats.put("p50", snapshot.setLatencies.p50);
                setStats.put("p99", snapshot.setLatencies.p99);
            }
            
            if (snapshot.getHitLatencies.count > 0) {
                ObjectNode getHitStats = json.putObject("get_hit_latency");
                getHitStats.put("count", snapshot.getHitLatencies.count);
                getHitStats.put("mean", snapshot.getHitLatencies.mean);
                getHitStats.put("p50", snapshot.getHitLatencies.p50);
                getHitStats.put("p99", snapshot.getHitLatencies.p99);
            }
            
            if (snapshot.getMissLatencies.count > 0) {
                ObjectNode getMissStats = json.putObject("get_miss_latency");
                getMissStats.put("count", snapshot.getMissLatencies.count);
                getMissStats.put("mean", snapshot.getMissLatencies.mean);
                getMissStats.put("p50", snapshot.getMissLatencies.p50);
                getMissStats.put("p99", snapshot.getMissLatencies.p99);
            }
            
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, json);
        } catch (Exception e) {
            logger.error("Failed to save checkpoint: {}", e.getMessage());
        }
    }
    
    private class Worker implements Runnable {
        private final ClientOperations ops;
        private final StreamingMetrics metrics;
        private final GlobalRateLimiter limiter;
        private final AtomicBoolean running;
        private final ThreadLocalRandom random = ThreadLocalRandom.current();
        
        Worker(ClientOperations ops, StreamingMetrics metrics, GlobalRateLimiter limiter, AtomicBoolean running) {
            this.ops = ops;
            this.metrics = metrics;
            this.limiter = limiter;
            this.running = running;
        }
        
        @Override
        public void run() {
            while (running.get()) {
                boolean wasWrite = false;
                try {
                    limiter.acquire();
                    
                    wasWrite = random.nextDouble() < config.writeRatio;
                    if (wasWrite) {
                        String key = "write:" + random.nextInt(config.keySpaceSize);
                        ops.executeSet(key, testValue);
                    } else {
                        String key;
                        if (random.nextDouble() < config.hitRatio) {
                            int preloadedKeys = (int)(config.keySpaceSize * config.hitRatio);
                            key = "preload:" + random.nextInt(preloadedKeys);
                        } else {
                            key = "miss:" + random.nextInt(config.keySpaceSize * 10);
                        }
                        ops.executeGet(key);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    ops.handleError(wasWrite, e);
                }
            }
        }
    }
    
    private static class GlobalRateLimiter {
        private final long intervalNanos;
        private final AtomicLong nextOperationNanos;
        
        public GlobalRateLimiter(double targetRate) {
            this.intervalNanos = (long)(1_000_000_000.0 / targetRate);
            this.nextOperationNanos = new AtomicLong(System.nanoTime());
        }
        
        public void acquire() throws InterruptedException {
            long expectedTime;
            long actualTime;
            
            do {
                expectedTime = nextOperationNanos.get();
                actualTime = System.nanoTime();
                long nextTime = expectedTime + intervalNanos;
                if (nextOperationNanos.compareAndSet(expectedTime, nextTime)) {
                    long waitNanos = expectedTime - actualTime;
                    if (waitNanos > 0) {
                        java.util.concurrent.locks.LockSupport.parkNanos(waitNanos);
                    }
                    return;
                }
            } while (true);
        }
    }
    
    public static class ImplementationResult {
        public final String implementation;
        public final double durationSec;
        public final MetricsSnapshot finalMetrics;
        public final List<MetricsSnapshot> checkpoints;
        public final List<SystemResourceMonitor.ResourceSnapshot> resourceCheckpoints;
        public final SystemResourceMonitor.ResourceSnapshot peakResources;
        public final String performanceMetrics;
        
        public ImplementationResult(String implementation, double durationSec,
                                   MetricsSnapshot finalMetrics, List<MetricsSnapshot> checkpoints,
                                   List<SystemResourceMonitor.ResourceSnapshot> resourceCheckpoints,
                                   SystemResourceMonitor.ResourceSnapshot peakResources,
                                   String performanceMetrics) {
            this.implementation = implementation;
            this.durationSec = durationSec;
            this.finalMetrics = finalMetrics;
            this.checkpoints = checkpoints;
            this.resourceCheckpoints = resourceCheckpoints;
            this.peakResources = peakResources;
            this.performanceMetrics = performanceMetrics;
        }
    }
    
    public void printResults(ImplementationResult result) {
        logger.info("=== FINAL RESULTS ===");
        MetricsSnapshot m = result.finalMetrics;
        logger.info("Total Commands: {} (SET: {}, GET: {})",
            m.setCommands + m.getCommands, m.setCommands, m.getCommands);
        logger.info("Cache Hit Rate: {}%",
            String.format("%.1f", m.getHits * 100.0 / (m.getHits + m.getMisses)));
        double actualQps = (m.setCommands + m.getCommands) / result.durationSec;
        logger.info("Throughput: {} commands/sec (Target: {}, {}%)",
            String.format("%.0f", actualQps), 
            String.format("%.0f", config.targetQps), 
            String.format("%.1f", (actualQps / config.targetQps * 100)));
        if (m.setLatencies.count > 0) {
            logger.info("SET Latency: mean={}ms, p99={}ms",
                String.format("%.2f", m.setLatencies.mean), 
                String.format("%.2f", m.setLatencies.p99));
        }
        if (m.getHitLatencies.count > 0) {
            logger.info("GET Hit Latency: mean={}ms, p99={}ms",
                String.format("%.2f", m.getHitLatencies.mean), 
                String.format("%.2f", m.getHitLatencies.p99));
        }
        if (result.peakResources != null) {
            logger.info("Peak Resource Usage:");
            logger.info("  Process CPU: {}%", String.format("%.1f", result.peakResources.processCpuLoad));
            logger.info("  Heap Memory: {}MB / {}MB ({}%)",
                result.peakResources.heapUsed, result.peakResources.heapMax,
                String.format("%.1f", (result.peakResources.heapUsed * 100.0 / result.peakResources.heapMax)));
            logger.info("  Total Memory: {}MB", result.peakResources.usedMemory);
        }
        logger.info("📁 Checkpoints saved to: {}", checkpointDir);
    }
    
    public void saveResults(ImplementationResult result, String filename) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filename), result);
    }
}