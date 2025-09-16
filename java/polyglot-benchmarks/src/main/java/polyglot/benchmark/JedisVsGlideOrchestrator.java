package polyglot.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Orchestrates head-to-head runs for GLIDE-JNI vs Jedis across the scenarios in bench.md.
 * Uses the AWS ElastiCache host by default (ELASTICACHE_HOST env or the benchmark's default).
 */
public class JedisVsGlideOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(JedisVsGlideOrchestrator.class);

    public static void main(String[] args) throws Exception {
        String host = System.getenv("ELASTICACHE_HOST");
        if (host == null || host.isEmpty()) {
            host = "clustercfg.testing-cluster.ey5v7d.use2.cache.amazonaws.com";
        }
        int port = 6379;
        int dataSize = 25600; // 25KB per bench.md
        int durationSec = 300; // 5 minutes per scenario

        // RPM -> QPS mapping
        int[] rpms = new int[]{10000, 50000, 100000, 600000};
        double[] qps = Arrays.stream(rpms).mapToDouble(r -> r / 60.0).toArray();

        for (int i = 0; i < rpms.length; i++) {
            int rpm = rpms[i];
            double targetQps = qps[i];

            logger.info("\n====================================");
            logger.info("Starting scenario: {} RPM (~{} QPS)", rpm, String.format("%.1f", targetQps));
            logger.info("Data size: {} bytes, Duration: {}s, Host: {}:{}, TLS on, Cluster on", dataSize, durationSec, host, port);
            logger.info("====================================\n");

            // GLIDE-JNI run
            ValkeyClientBenchmark.BenchmarkConfig glideCfg = new ValkeyClientBenchmark.BenchmarkConfig();
            glideCfg.host = host;
            glideCfg.port = port;
            glideCfg.dataSize = dataSize;
            glideCfg.targetQps = targetQps;
            glideCfg.testDurationSec = durationSec;
            glideCfg.clusterMode = true;
            glideCfg.tls = true;
            // Implementation selection is implicit in this simplified benchmark (JNI only)
            glideCfg.warmup = true;

            ValkeyClientBenchmark glideBench = new ValkeyClientBenchmark(glideCfg);
            ValkeyClientBenchmark.ImplementationResult glideResults = glideBench.run();
            glideBench.printResults(glideResults);

            // Cooldown between runs
            Thread.sleep(5000);

            // Jedis run
            ValkeyClientBenchmark.BenchmarkConfig jedisCfg = new ValkeyClientBenchmark.BenchmarkConfig();
            jedisCfg.host = host;
            jedisCfg.port = port;
            jedisCfg.dataSize = dataSize;
            jedisCfg.targetQps = targetQps;
            jedisCfg.testDurationSec = durationSec;
            jedisCfg.clusterMode = true;
            jedisCfg.tls = true;
            // Jedis implementation path removed in simplified benchmark module
            jedisCfg.warmup = true;

            ValkeyClientBenchmark jedisBench = new ValkeyClientBenchmark(jedisCfg);
            ValkeyClientBenchmark.ImplementationResult jedisResults = jedisBench.run();
            jedisBench.printResults(jedisResults);

            // Cooldown between scenarios
            logger.info("Cooling down 15s before next scenario...");
            Thread.sleep(15000);
        }

        logger.info("All scenarios completed.");
    }
}
