package com.example.valkey;

import java.util.ArrayList;
import java.util.List;

public class MinimalLatencyTestRunner {
    
    public static void main(String[] args) {
        try {
            TestConfiguration config = parseArguments(args);
            String clientType = getClientType(args);
            int operations = getOperations(args);
            
            System.out.println("Minimal Latency Test Suite");
            System.out.println("Host: " + config.getRedisHost() + ":" + config.getRedisPort());
            System.out.println("Operations per client: " + operations);
            System.out.println("Client type: " + clientType);
            System.out.println();
            
            List<MinimalLatencyTest.LatencyResults> results = new ArrayList<>();
            
            // Run tests based on client type
            switch (clientType.toLowerCase()) {
                case "jedis":
                    results.add(runJedisTest(config, operations));
                    break;
                case "valkey":
                case "glide":
                    results.add(runValkeyTest(config, operations));
                    break;
                case "both":
                default:
                    results.add(runJedisTest(config, operations));
                    System.out.println("\nWaiting 5 seconds between tests...\n");
                    Thread.sleep(5000);
                    results.add(runValkeyTest(config, operations));
                    break;
            }
            
            // Print comparison if multiple clients tested
            if (results.size() > 1) {
                printComparison(results);
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
        }
    }
    
    private static MinimalLatencyTest.LatencyResults runJedisTest(TestConfiguration config, int operations) throws Exception {
        JedisClient client = new JedisClient(config);
        MinimalLatencyTest test = new MinimalLatencyTest(client, "Jedis", operations);
        MinimalLatencyTest.LatencyResults results = test.runTest();
        results.printResults();
        return results;
    }
    
    private static MinimalLatencyTest.LatencyResults runValkeyTest(TestConfiguration config, int operations) throws Exception {
        ValkeyGlideClient client = new ValkeyGlideClient(config);
        MinimalLatencyTest test = new MinimalLatencyTest(client, "Valkey-Glide", operations);
        MinimalLatencyTest.LatencyResults results = test.runTest();
        results.printResults();
        return results;
    }
    
    private static void printComparison(List<MinimalLatencyTest.LatencyResults> results) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("LATENCY COMPARISON");
        System.out.println("=".repeat(80));
        
        System.out.printf("%-20s %15s%n", "Client", "Avg Latency (μs)");
        System.out.println("-".repeat(40));
        
        for (MinimalLatencyTest.LatencyResults result : results) {
            System.out.printf("%-20s %15.1f%n", 
                result.getClientName(), 
                result.getOverallAverage());
        }
        
        if (results.size() == 2) {
            double ratio = results.get(1).getOverallAverage() / results.get(0).getOverallAverage();
            System.out.printf("%nLatency Ratio: %.2fx%n", ratio);
        }
        
        System.out.println("=".repeat(80));
    }
    
    private static TestConfiguration parseArguments(String[] args) {
        TestConfiguration config = new TestConfiguration();
        
        for (String arg : args) {
            if (arg.startsWith("--host=")) {
                config.setRedisHost(arg.substring(7));
            } else if (arg.startsWith("--port=")) {
                config.setRedisPort(Integer.parseInt(arg.substring(7)));
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
    
    private static int getOperations(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--operations=")) {
                return Integer.parseInt(arg.substring(13));
            }
        }
        return 1000; // default
    }
    
    private static void printUsage() {
        System.out.println("\nUsage: gradle runMinimalLatencyTest --args=\"[options]\"");
        System.out.println("\nOptions:");
        System.out.println("  --client=<type>        Client type: jedis, valkey, both (default: both)");
        System.out.println("  --operations=<num>     Operations per client (default: 1000)");
        System.out.println("  --host=<hostname>      Redis host (default: localhost)");
        System.out.println("  --port=<port>          Redis port (default: 6379)");
        System.out.println("  --cluster              Enable cluster mode");
        System.out.println("  --tls                  Enable TLS/SSL");
        System.out.println("\nExamples:");
        System.out.println("  gradle runMinimalLatencyTest");
        System.out.println("  gradle runMinimalLatencyTest --args=\"--operations=5000\"");
        System.out.println("  gradle runMinimalLatencyTest --args=\"--client=jedis --operations=2000\"");
    }
}
