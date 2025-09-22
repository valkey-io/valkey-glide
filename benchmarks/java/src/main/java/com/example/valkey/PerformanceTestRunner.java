package com.example.valkey;

import java.util.ArrayList;
import java.util.List;

public class PerformanceTestRunner {
    
    public static void main(String[] args) {
        try {
            TestConfiguration config = parseArguments(args);
            String clientType = getClientType(args);
            
            System.out.println("Redis Performance Test Suite");
            System.out.println("Configuration: " + config.toString());
            System.out.println();
            
            List<PerformanceTest> tests = new ArrayList<>();
            
            // Create tests based on client type
            switch (clientType.toLowerCase()) {
                case "jedis":
                    tests.add(PerformanceTest.createJedisTest(config));
                    if (!config.isClusterMode()) {
                        tests.add(PerformanceTest.createJedisPooledTest(config));
                    }
                    break;
                case "jedispooled":
                    if (!config.isClusterMode()) {
                        tests.add(PerformanceTest.createJedisPooledTest(config));
                    } else {
                        System.err.println("JedisPooled is not supported in cluster mode");
                        System.exit(1);
                    }
                    break;
                case "valkey":
                case "glide":
                    tests.add(PerformanceTest.createValkeyGlideTest(config));
                    break;
                case "compatibility":
                    tests.add(PerformanceTest.createJedisCompatibilityTest(config));
                    tests.add(PerformanceTest.createUnifiedJedisTest(config));
                    break;
                case "all":
                    tests.add(PerformanceTest.createJedisTest(config));
                    if (!config.isClusterMode()) {
                        tests.add(PerformanceTest.createJedisPooledTest(config));
                    }
                    tests.add(PerformanceTest.createValkeyGlideTest(config));
                    tests.add(PerformanceTest.createJedisCompatibilityTest(config));
                    tests.add(PerformanceTest.createUnifiedJedisTest(config));
                    break;
                case "both":
                default:
                    tests.add(PerformanceTest.createJedisTest(config));
                    if (!config.isClusterMode()) {
                        tests.add(PerformanceTest.createJedisPooledTest(config));
                    }
                    tests.add(PerformanceTest.createValkeyGlideTest(config));
                    break;
            }
            
            // Run all tests
            List<PerformanceMetrics> results = new ArrayList<>();
            List<String> clientNames = new ArrayList<>();
            int testCounter = 0;
            for (PerformanceTest test : tests) {
                testCounter++;
                try {
                    PerformanceMetrics metrics = test.runTest();
                    results.add(metrics);
                    clientNames.add(test.getClientName());
                    
                    // Wait between tests
                    if (tests.size() > 1 && testCounter < tests.size()) {
                        System.out.println("\nWaiting 15 seconds before next test...\n");
                        Thread.sleep(15000);
                    }
                } catch (Exception e) {
                    System.err.println("Test failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // Print comparison summary
            if (results.size() > 1) {
                printComparisonSummary(results, clientNames);
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
        }
    }
    
    private static TestConfiguration parseArguments(String[] args) {
        TestConfiguration config = new TestConfiguration();
        
        for (String arg : args) {
            if (arg.startsWith("--connections=")) {
                config.setConcurrentConnections(Integer.parseInt(arg.substring(14)));
            } else if (arg.startsWith("--rpm=")) {
                config.setRequestsPerMinute(Integer.parseInt(arg.substring(6)));
            } else if (arg.startsWith("--min-data-size=")) {
                config.setMinDataSize(Integer.parseInt(arg.substring(16)));
            } else if (arg.startsWith("--max-data-size=")) {
                config.setMaxDataSize(Integer.parseInt(arg.substring(16)));
            } else if (arg.startsWith("--duration=")) {
                config.setTestDurationSeconds(Integer.parseInt(arg.substring(11)));
            } else if (arg.startsWith("--host=")) {
                config.setRedisHost(arg.substring(7));
            } else if (arg.startsWith("--port=")) {
                config.setRedisPort(Integer.parseInt(arg.substring(7)));
            } else if (arg.startsWith("--read-ratio=")) {
                config.setReadWriteRatio(Integer.parseInt(arg.substring(13)));
            } else if (arg.startsWith("--warmup=")) {
                config.setWarmupSeconds(Integer.parseInt(arg.substring(9)));
            } else if (arg.startsWith("--key-prefix=")) {
                config.setKeyPrefix(arg.substring(13));
            } else if (arg.equals("--cluster")) {
                config.setClusterMode(true);
            } else if (arg.equals("--tls")) {
                config.setTlsEnabled(true);
            }
        }
        
        return config;
    }
    
    private static String getClientType(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--client=")) {
                return arg.substring(9);
            }
        }
        return "both"; // default
    }
    
    private static void printComparisonSummary(List<PerformanceMetrics> results, List<String> clientNames) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("COMPARISON SUMMARY");
        System.out.println("=".repeat(100));
        
        System.out.printf("%-20s %10s %10s %10s %10s %10s %10s %10s%n",
            "Client", "RPS", "Avg Lat", "P95 Lat", "P99 Lat", "Success%", "SET Ops", "GET Ops");
        System.out.println("-".repeat(100));
        
        for (int i = 0; i < results.size(); i++) {
            PerformanceMetrics metrics = results.get(i);
            String clientName = clientNames.get(i);
            
            System.out.printf("%-20s %10.1f %10.1f %10.1f %10.1f %9.1f%% %10d %10d%n",
                clientName,
                metrics.getRequestsPerSecond(),
                metrics.getAverageLatency(),
                metrics.getPercentile(95),
                metrics.getPercentile(99),
                metrics.getSuccessRate(),
                metrics.getSetOperations(),
                metrics.getGetOperations()
            );
        }
        System.out.println("=".repeat(100));
    }
    
    private static String getClientNameFromTest(PerformanceTest test) {
        return test.getClientName();
    }
    
    private static void printUsage() {
        System.out.println("\nUsage: java -jar valkey-performance-test.jar [options]");
        System.out.println("\nOptions:");
        System.out.println("  --client=<type>           Client type: jedis, valkey, compatibility, all, both (default: both)");
        System.out.println("  --connections=<num>       Concurrent connections (default: 20)");
        System.out.println("  --rpm=<num>               Requests per minute (default: 10000)");
        System.out.println("  --min-data-size=<bytes>   Minimum data size (default: 10240)");
        System.out.println("  --max-data-size=<bytes>   Maximum data size (default: 51200)");
        System.out.println("  --duration=<seconds>      Test duration (default: 60)");
        System.out.println("  --host=<hostname>         Redis host (default: localhost)");
        System.out.println("  --port=<port>             Redis port (default: 6379)");
        System.out.println("  --read-ratio=<percent>    Read operation percentage (default: 50)");
        System.out.println("  --warmup=<seconds>        Warmup duration (default: 10)");
        System.out.println("  --key-prefix=<prefix>     Key prefix (default: perf_test_)");
        System.out.println("  --cluster                 Enable cluster mode (default: false)");
        System.out.println("  --tls                     Enable TLS/SSL (default: false)");
        System.out.println("\nExamples:");
        System.out.println("  gradle run --args=\"--client=jedis --connections=50 --rpm=20000\"");
        System.out.println("  gradle run --args=\"--cluster --tls --host=my-cluster.com --port=6380\"");
        System.out.println("  gradle run --args=\"--client=valkey --cluster --connections=30\"");
        System.out.println("  gradle runJedisTest");
        System.out.println("  gradle runValkeyTest");
        System.out.println("  gradle runBothTests");
    }
}
